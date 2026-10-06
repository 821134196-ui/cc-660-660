package com.factory.helmet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.factory.helmet.api.BusinessRuleException;
import com.factory.helmet.api.dto.AttachmentInput;
import com.factory.helmet.api.dto.CheckoutRequest;
import com.factory.helmet.api.dto.HelmetRequest;
import com.factory.helmet.api.dto.HelmetUpdate;
import com.factory.helmet.api.dto.ImpactRequest;
import com.factory.helmet.api.dto.InspectionRequest;
import com.factory.helmet.api.view.AttachmentView;
import com.factory.helmet.api.view.CheckoutView;
import com.factory.helmet.api.view.HelmetDetailView;
import com.factory.helmet.api.view.HelmetView;
import com.factory.helmet.api.view.ImpactView;
import com.factory.helmet.api.view.InspectionView;
import com.factory.helmet.domain.CheckoutRecord;
import com.factory.helmet.domain.Helmet;
import com.factory.helmet.domain.HelmetStatus;
import com.factory.helmet.domain.ImpactEvent;
import com.factory.helmet.domain.ImpactSeverity;
import com.factory.helmet.domain.Inspection;
import com.factory.helmet.domain.InspectionAttachment;
import com.factory.helmet.domain.InspectionResult;
import com.factory.helmet.domain.LinerCondition;
import com.factory.helmet.domain.ShellCondition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;

/**
 * 安全帽业务核心。所有状态流转只允许通过本类完成：
 *
 *  撞击  → 立即 FROZEN（程度不明也冻结，不默认合格）
 *  检查  → REUSE 恢复在役（须填下次检查日，且壳体/内衬/年限必须合格）
 *        → REINSPECT 退回复检（置 REINSPECTING，仓库视角仍冻结、禁止领出）
 *        → RETIRE 永久退役（不可通过库存编辑撤销）
 *
 * 仓库发放仅认 IN_SERVICE；退役编号永久保留，再次登记/扫码领用一律拦截。
 */
@ApplicationScoped
public class HelmetService {

    @Inject
    EntityManager em;

    @Inject
    AttachmentStorage attachmentStorage;

    @ConfigProperty(name = "safetyhelmet.default-service-life-years", defaultValue = "3")
    int serviceLifeYears;

    // ---------------- 登记 / 库存 ----------------

    @Transactional
    public Helmet register(HelmetRequest req) {
        String code = requireCode(req.code());
        requireText(req.batch(), "生产批次不能为空");
        if (req.manufactureDate() == null) {
            throw BusinessRuleException.badRequest("生产日期不能为空");
        }
        if (req.manufactureDate().isAfter(LocalDate.now())) {
            throw BusinessRuleException.badRequest("生产日期不能晚于今天");
        }
        Helmet existing = findHelmetOrNull(code);
        if (existing != null) {
            if (existing.getStatus() == HelmetStatus.RETIRED) {
                // 旧编号再次扫码/登记必须拦截，退役编号永久作废
                throw BusinessRuleException.conflict(
                        "编号 " + code + " 属于已退役安全帽，旧编号永久作废，禁止重新登记");
            }
            throw BusinessRuleException.conflict("编号 " + code + " 已存在");
        }
        Helmet helmet = new Helmet(code, req.batch().trim(), req.manufactureDate());
        em.persist(helmet);
        try {
            em.flush();
        } catch (PersistenceException e) {
            // 并发下唯一约束兜底
            throw BusinessRuleException.conflict("编号 " + code + " 已存在（或为退役旧码）");
        }
        return helmet;
    }

    /**
     * 库存资料编辑：只能改批次/生产日期。
     * DTO 不含状态字段（未知字段会被 Jackson 拒绝），退役结论无法由此撤销。
     */
    @Transactional
    public Helmet updateInventory(String code, HelmetUpdate req) {
        Helmet helmet = findHelmetByCode(code);
        if (req.batch() != null) {
            if (req.batch().isBlank()) {
                throw BusinessRuleException.badRequest("生产批次不能为空");
            }
            helmet.setBatch(req.batch().trim());
        }
        if (req.manufactureDate() != null) {
            if (req.manufactureDate().isAfter(LocalDate.now())) {
                throw BusinessRuleException.badRequest("生产日期不能晚于今天");
            }
            helmet.setManufactureDate(req.manufactureDate());
        }
        // 注意：无论帽子是否退役，这里都不触碰 status/retiredAt
        return helmet;
    }

    // ---------------- 受冲击：立即冻结 ----------------

    @Transactional
    public ImpactEvent reportImpact(String code, ImpactRequest req) {
        Helmet helmet = findHelmetByCode(code);
        if (helmet.getStatus() == HelmetStatus.RETIRED) {
            throw BusinessRuleException.conflict("安全帽已退役，不能再登记撞击事件");
        }
        if (req.holderName() == null || req.holderName().isBlank()) {
            throw BusinessRuleException.badRequest("必须填写撞击时使用人，事故记录须可追溯到人");
        }
        // 程度不明 → UNKNOWN，绝不默认合格
        ImpactSeverity severity = parseSeverity(req.severity());
        LocalDateTime occurred = req.occurredAt() != null ? req.occurredAt() : LocalDateTime.now();

        ImpactEvent event = new ImpactEvent(helmet, severity, req.holderName().trim(),
                trim(req.location()), trim(req.description()), occurred);
        em.persist(event);

        // 受冲击后立即冻结：FROZEN / REINSPECTING 一律压回 FROZEN，等待安全员检查
        helmet.setStatus(HelmetStatus.FROZEN);
        helmet.setLastImpactAt(LocalDateTime.now());
        return event;
    }

    // ---------------- 领用 / 归还 ----------------

    @Transactional
    public CheckoutRecord checkout(String code, CheckoutRequest req) {
        Helmet helmet = findHelmetByCode(code);
        if (req.workerName() == null || req.workerName().isBlank()) {
            throw BusinessRuleException.badRequest("领用人不能为空");
        }
        // 仓库发放闸口：只有 IN_SERVICE 能领出
        switch (helmet.getStatus()) {
            case FROZEN -> throw BusinessRuleException.conflict(
                    "安全帽 " + code + " 受冲击已冻结，禁止领出，须经安全员检查");
            case REINSPECTING -> throw BusinessRuleException.conflict(
                    "安全帽 " + code + " 处于复检退回期，仍保持冻结，禁止领出");
            case RETIRED -> throw BusinessRuleException.conflict(
                    "安全帽 " + code + " 已退役，旧编号已拦截，禁止领出");
            case IN_SERVICE -> { /* 状态放行，继续做年限/检查期校验 */ }
        }
        // 即使状态在役：超过使用年限或逾期未复检，也不得领出（不以系统状态字段为唯一依据）
        if (isServiceLifeExpired(helmet)) {
            throw BusinessRuleException.conflict(
                    "安全帽 " + code + " 已超过 " + serviceLifeYears + " 年使用年限，禁止领出，应退役");
        }
        if (helmet.getNextInspectionDate() != null
                && helmet.getNextInspectionDate().isBefore(LocalDate.now())) {
            throw BusinessRuleException.conflict(
                    "安全帽 " + code + " 已超过下次检查日（" + helmet.getNextInspectionDate()
                            + "），须重新检查后方可领出");
        }
        if (hasOpenCheckout(helmet)) {
            throw BusinessRuleException.conflict("该帽尚有未归还的领用记录，请先归还再领出");
        }
        CheckoutRecord record = new CheckoutRecord(helmet, req.workerName().trim(),
                trim(req.team()), LocalDateTime.now(), null);
        em.persist(record);
        return record;
    }

    /** 归还：冻结/复检中的帽子由工人交回仓库，允许归还（交回后继续冻结待检） */
    @Transactional
    public CheckoutRecord returnHelmet(String code, String note) {
        Helmet helmet = findHelmetByCode(code);
        CheckoutRecord open = findOpenCheckout(helmet);
        if (open == null) {
            throw BusinessRuleException.conflict("该帽没有未归还的领用记录");
        }
        open.setReturnedAt(LocalDateTime.now());
        return open;
    }

    // ---------------- 安全员检查 ----------------

    @Transactional
    public InspectionView inspect(String code, InspectionRequest req) {
        Helmet helmet = findHelmetByCode(code);
        if (helmet.getStatus() == HelmetStatus.RETIRED) {
            throw BusinessRuleException.conflict("安全帽已退役，退役结论不可更改");
        }
        if (helmet.getStatus() == HelmetStatus.IN_SERVICE) {
            throw BusinessRuleException.conflict("安全帽当前在役，未发生受冲击冻结，不能提交检查结论");
        }
        ShellCondition shell = parseEnum(ShellCondition.class, req.shellCondition(), "外壳检查结果");
        LinerCondition liner = parseEnum(LinerCondition.class, req.linerCondition(), "内衬检查结果");
        InspectionResult result = parseEnum(InspectionResult.class, req.result(), "检查结论");
        if (req.basis() == null || req.basis().isBlank()) {
            throw BusinessRuleException.badRequest("必须填写判定依据（规范条款或检查事实）");
        }
        if (req.inspectorName() == null || req.inspectorName().isBlank()) {
            throw BusinessRuleException.badRequest("必须填写安全员姓名");
        }
        // 使用年限由系统按生产日期计算，不信任前端传值
        boolean lifeExpired = isServiceLifeExpired(helmet);

        switch (result) {
            case REUSE -> {
                if (shell == ShellCondition.CRACKED) {
                    throw BusinessRuleException.badRequest("外壳存在裂纹/破裂/穿孔，不得判定复用");
                }
                if (liner == LinerCondition.DAMAGED) {
                    throw BusinessRuleException.badRequest("内衬撕裂/脱开/缺失，不得判定复用");
                }
                if (lifeExpired) {
                    throw BusinessRuleException.badRequest("已超过使用年限，不得判定复用，应退役");
                }
                if (req.nextInspectionDate() == null) {
                    throw BusinessRuleException.badRequest("判定复用必须注明下次检查日");
                }
                if (req.nextInspectionDate().isBefore(LocalDate.now())) {
                    throw BusinessRuleException.badRequest("下次检查日不能早于今天");
                }
                helmet.setStatus(HelmetStatus.IN_SERVICE);
                helmet.setNextInspectionDate(req.nextInspectionDate());
            }
            case REINSPECT -> {
                // 复检退回：帽子保持冻结（REINSPECTING 仍被领用闸口拦截）
                helmet.setStatus(HelmetStatus.REINSPECTING);
            }
            case RETIRE -> {
                // 退役：终态，任何后续业务操作都不可改变
                helmet.setStatus(HelmetStatus.RETIRED);
                helmet.setRetiredAt(LocalDateTime.now());
            }
        }

        ImpactEvent trigger = findLatestImpact(helmet);
        Inspection inspection = new Inspection(helmet, trigger, shell, liner, lifeExpired,
                req.basis().trim(), result,
                result == InspectionResult.REUSE ? req.nextInspectionDate() : null,
                req.inspectorName().trim());
        em.persist(inspection);
        em.flush(); // 取得 inspection id 作为附件目录名

        if (req.attachments() != null) {
            for (AttachmentInput input : req.attachments()) {
                AttachmentStorage.StoredFile stored = attachmentStorage.store(inspection.getId(), input);
                inspection.addAttachment(new InspectionAttachment(inspection, stored.fileName(),
                        stored.storagePath(), stored.contentType(), stored.sizeBytes()));
            }
        }
        em.flush();
        return InspectionView.of(inspection,
                inspection.getAttachments().stream().map(AttachmentView::of).toList());
    }

    // ---------------- 查询 ----------------

    @Transactional
    public Helmet findHelmetByCode(String code) {        Helmet helmet = findHelmetOrNull(code);
        if (helmet == null) {
            throw BusinessRuleException.notFound("查无此编号: " + code);
        }
        return helmet;
    }

    public List<Helmet> listHelmets() {
        return em.createQuery("select h from Helmet h order by h.id", Helmet.class).getResultList();
    }

    public List<CheckoutRecord> listCheckouts(Helmet helmet) {
        return em.createQuery(
                        "select c from CheckoutRecord c where c.helmet = :h order by c.checkedOutAt desc",
                        CheckoutRecord.class)
                .setParameter("h", helmet)
                .getResultList();
    }

    public List<ImpactEvent> listImpacts(Helmet helmet) {
        return em.createQuery(
                        "select e from ImpactEvent e where e.helmet = :h order by e.occurredAt desc",
                        ImpactEvent.class)
                .setParameter("h", helmet)
                .getResultList();
    }

    public List<Inspection> listInspections(Helmet helmet) {
        return em.createQuery(
                        "select i from Inspection i where i.helmet = :h order by i.inspectedAt desc",
                        Inspection.class)
                .setParameter("h", helmet)
                .getResultList();
    }

    public Inspection findInspection(Long id) {
        Inspection inspection = em.find(Inspection.class, id);
        if (inspection == null) {
            throw BusinessRuleException.notFound("检查记录不存在");
        }
        return inspection;
    }

    public InspectionAttachment findAttachment(Long attachmentId) {
        InspectionAttachment a = em.find(InspectionAttachment.class, attachmentId);
        if (a == null) {
            throw BusinessRuleException.notFound("附件不存在");
        }
        return a;
    }

    /** 装配扫码视图（在事务内读取关联） */
    @Transactional
    public HelmetView getHelmetView(Helmet h) {
        boolean expired = isServiceLifeExpired(h);
        String holder = listCheckouts(h).stream()
                .filter(CheckoutRecord::isOpen)
                .map(CheckoutRecord::getWorkerName)
                .findFirst().orElse(null);
        String block = null;
        if (h.getStatus() == HelmetStatus.IN_SERVICE) {
            if (expired) {
                block = "已超过 " + serviceLifeYears + " 年使用年限，禁止领出";
            } else if (h.getNextInspectionDate() != null && h.getNextInspectionDate().isBefore(LocalDate.now())) {
                block = "已超过下次检查日（" + h.getNextInspectionDate() + "），须重新检查";
            }
        }
        return HelmetView.of(h, expired, holder, block);
    }

    /** 完整档案：主档 + 领用历史 + 撞击记录 + 检查记录（均在事务内加载） */
    @Transactional
    public HelmetDetailView getDetail(String code) {
        Helmet h = findHelmetByCode(code);
        HelmetView view = getHelmetView(h);
        List<CheckoutView> checkouts = listCheckouts(h).stream().map(CheckoutView::of).toList();
        List<ImpactView> impacts = listImpacts(h).stream().map(ImpactView::of).toList();
        List<InspectionView> inspections = listInspections(h).stream()
                .map(i -> InspectionView.of(i,
                        i.getAttachments().stream().map(AttachmentView::of).toList()))
                .toList();
        return new HelmetDetailView(view, checkouts, impacts, inspections);
    }

    // ---------------- 内部辅助 ----------------

    private Helmet findHelmetOrNull(String code) {
        String normalized = requireCode(code);
        return em.createQuery("select h from Helmet h where h.code = :c", Helmet.class)
                .setParameter("c", normalized)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private boolean hasOpenCheckout(Helmet helmet) {
        return findOpenCheckout(helmet) != null;
    }

    private CheckoutRecord findOpenCheckout(Helmet helmet) {
        TypedQuery<CheckoutRecord> q = em.createQuery(
                "select c from CheckoutRecord c where c.helmet = :h and c.returnedAt is null",
                CheckoutRecord.class).setParameter("h", helmet);
        return q.getResultList().stream().findFirst().orElse(null);
    }

    private ImpactEvent findLatestImpact(Helmet helmet) {
        return em.createQuery(
                        "select e from ImpactEvent e where e.helmet = :h order by e.occurredAt desc",
                        ImpactEvent.class)
                .setParameter("h", helmet)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    boolean isServiceLifeExpired(Helmet helmet) {
        return helmet.getManufactureDate().plusYears(serviceLifeYears).isBefore(LocalDate.now());
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessRuleException.badRequest("安全帽编号不能为空");
        }
        return code.trim();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BusinessRuleException.badRequest(message);
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static ImpactSeverity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return ImpactSeverity.UNKNOWN;
        }
        try {
            return ImpactSeverity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ImpactSeverity.UNKNOWN;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw BusinessRuleException.badRequest(fieldName + "不能为空");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessRuleException.badRequest(fieldName + "取值非法: " + raw);
        }
    }
}
