package com.factory.helmet.service;

import com.factory.helmet.api.ApiException;
import com.factory.helmet.api.HelmetDetail;
import com.factory.helmet.api.ScanResult;
import com.factory.helmet.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 安全帽全生命周期业务规则。
 *
 * 关键约束（全部在服务端强制，前端仅做提示）：
 *  1. 撞击上报即冻结，冻结/退役帽子一律禁止领用；
 *  2. 撞击程度不明（UNKNOWN）不允许直接复用 —— 不得默认合格；
 *  3. 结论为复检时事件转入复检中，帽子保持冻结，可再次提交检查（复检退回）；
 *  4. 超过使用年限或外壳/内衬损伤的帽子不允许复用；
 *  5. 复用必须注明下次检查日期；
 *  6. 退役不可逆：不存在任何把帽子从 RETIRED 改回其他状态的接口，
 *     退役记录只允许新增和查询；旧编号永久保留，重复登记与扫码领用均被拦截。
 */
@ApplicationScoped
public class HelmetService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "helmet.attachments.dir")
    String attachmentsDir;

    // ---------- 登记 ----------

    @Transactional
    public Helmet register(String code, String batch, LocalDate manufactureDate, Integer serviceLifeMonths) {
        if (isBlank(code)) throw ApiException.badRequest("编号不能为空");
        if (isBlank(batch)) throw ApiException.badRequest("生产批次不能为空");
        if (manufactureDate == null) throw ApiException.badRequest("生产日期不能为空");
        if (serviceLifeMonths == null || serviceLifeMonths <= 0) throw ApiException.badRequest("使用期限（月）必须为正数");

        code = code.trim();
        Helmet existing = findByCode(code);
        if (existing != null) {
            // 旧编号（含退役编号）再次登记一律拦截，编号永久唯一
            throw ApiException.conflict("编号 " + code + " 已登记（当前状态：" + statusLabel(existing.status)
                    + "），不能重复登记；退役编号永久保留，不可回收重用");
        }

        Helmet h = new Helmet();
        h.code = code;
        h.batch = batch.trim();
        h.manufactureDate = manufactureDate;
        h.serviceLifeMonths = serviceLifeMonths;
        em.persist(h);
        return h;
    }

    // ---------- 领用 / 归还 ----------

    @Transactional
    public Helmet issue(long helmetId, String workerName) {
        Helmet h = requireHelmet(helmetId);
        if (isBlank(workerName)) throw ApiException.badRequest("领用人姓名不能为空");
        switch (h.status) {
            case ISSUED -> throw ApiException.conflict("该安全帽已被 " + currentHolderName(h) + " 领用，不能重复发放");
            case FROZEN -> throw ApiException.conflict("该安全帽因撞击处于冻结状态（待检查/复检中），禁止领用");
            case RETIRED -> throw ApiException.conflict("该安全帽已退役，禁止领用；退役结论不可撤销");
            default -> { }
        }

        IssueRecord rec = new IssueRecord();
        rec.helmet = h;
        rec.workerName = workerName.trim();
        em.persist(rec);

        h.status = HelmetStatus.ISSUED;
        return h;
    }

    @Transactional
    public Helmet returnHelmet(long helmetId) {
        Helmet h = requireHelmet(helmetId);
        if (h.status != HelmetStatus.ISSUED) {
            throw ApiException.conflict("该安全帽当前不在领用状态，无法归还");
        }
        IssueRecord open = openIssue(h);
        if (open != null) {
            open.returnedAt = LocalDateTime.now();
        }
        h.status = HelmetStatus.IN_STOCK;
        return h;
    }

    // ---------- 撞击上报 ----------

    @Transactional
    public ImpactEvent reportImpact(long helmetId, String description, String severityStr, String reportedBy) {
        Helmet h = requireHelmet(helmetId);
        if (h.status == HelmetStatus.RETIRED) {
            throw ApiException.conflict("该安全帽已退役，无需上报撞击");
        }
        ImpactEvent open = openEvent(h);
        if (open != null) {
            throw ApiException.conflict("该安全帽已存在未结案的撞击事件（#" + open.id + "），请先完成检查");
        }

        Severity severity = parseSeverity(severityStr);

        // 撞击即冻结；若正在领用中，自动收回并快照当时使用人
        String userAtImpact = null;
        IssueRecord openIssue = openIssue(h);
        if (openIssue != null) {
            userAtImpact = openIssue.workerName;
            openIssue.returnedAt = LocalDateTime.now();
            openIssue.returnNote = "撞击上报，自动收回待检";
        }
        h.status = HelmetStatus.FROZEN;

        ImpactEvent ev = new ImpactEvent();
        ev.helmet = h;
        ev.description = isBlank(description) ? null : description.trim();
        ev.severity = severity;
        ev.reportedBy = isBlank(reportedBy) ? null : reportedBy.trim();
        ev.userAtImpact = userAtImpact;
        ev.status = ImpactStatus.PENDING;
        em.persist(ev);
        return ev;
    }

    // ---------- 检查（含复检退回后再检） ----------

    @Transactional
    public Inspection inspect(long eventId, String inspector, String shellStr, String linerStr,
                              String basis, String decisionStr, String nextInspectionDateStr,
                              List<FileUpload> files) {
        ImpactEvent ev = em.find(ImpactEvent.class, eventId);
        if (ev == null) throw ApiException.notFound("撞击事件不存在");
        if (ev.status == ImpactStatus.CLOSED) {
            throw ApiException.conflict("该撞击事件已结案，不能重复检查");
        }
        // PENDING 与 IN_RECHECK（复检退回）均允许提交新一轮检查

        if (isBlank(inspector)) throw ApiException.badRequest("安全员姓名不能为空");
        if (isBlank(basis)) throw ApiException.badRequest("检查依据不能为空");
        PartCondition shell = parsePart(shellStr, "外壳状况");
        PartCondition liner = parsePart(linerStr, "内衬状况");
        Decision decision = parseDecision(decisionStr);

        Helmet h = ev.helmet;
        boolean serviceLifeOk = !h.isServiceLifeExpired();

        LocalDate nextInspectionDate = null;
        if (!isBlank(nextInspectionDateStr)) {
            try {
                nextInspectionDate = LocalDate.parse(nextInspectionDateStr.trim());
            } catch (Exception e) {
                throw ApiException.badRequest("下次检查日期格式应为 yyyy-MM-dd");
            }
        }

        switch (decision) {
            case REUSE -> {
                // 撞击程度不明时不能默认合格
                if (ev.severity == Severity.UNKNOWN) {
                    throw ApiException.badRequest("撞击程度不明，不能判定复用；请选择复检（进一步检测）或退役");
                }
                if (!serviceLifeOk) {
                    throw ApiException.badRequest("该安全帽已超过使用年限（到期日 "
                            + h.getServiceLifeEndDate() + "），必须退役，不能复用");
                }
                if (shell == PartCondition.DAMAGED || liner == PartCondition.DAMAGED) {
                    throw ApiException.badRequest("外壳或内衬存在损伤，不能复用；请选择复检或退役");
                }
                if (nextInspectionDate == null) {
                    throw ApiException.badRequest("结论为复用时必须注明下次检查日期");
                }
            }
            case RECHECK -> {
                // 复检：不作结论，帽子保持冻结，等待下一轮检查
            }
            case RETIRE -> {
                // 退役：任何状态下均允许，结论不可逆
            }
        }

        Inspection insp = new Inspection();
        insp.impactEvent = ev;
        insp.roundNo = countInspections(ev) + 1;
        insp.inspector = inspector.trim();
        insp.shellCondition = shell;
        insp.linerCondition = liner;
        insp.serviceLifeOk = serviceLifeOk;
        insp.basis = basis.trim();
        insp.decision = decision;
        insp.nextInspectionDate = nextInspectionDate;
        em.persist(insp);
        em.flush(); // 拿到 insp.id 用于附件目录

        saveAttachments(insp, files);

        switch (decision) {
            case REUSE -> {
                h.status = HelmetStatus.IN_STOCK;
                h.nextInspectionDate = nextInspectionDate;
                closeEvent(ev, decision);
            }
            case RECHECK -> {
                // 帽子保持 FROZEN 不变
                ev.status = ImpactStatus.IN_RECHECK;
            }
            case RETIRE -> {
                h.status = HelmetStatus.RETIRED;
                h.retiredAt = LocalDateTime.now();
                h.retireReason = insp.basis;
                h.nextInspectionDate = null;

                RetirementRecord rr = new RetirementRecord();
                rr.helmet = h;
                rr.inspection = insp;
                rr.reason = insp.basis;
                rr.inspector = insp.inspector;
                em.persist(rr);
                closeEvent(ev, decision);
            }
        }
        return insp;
    }

    // ---------- 查询 ----------

    public List<Helmet> listHelmets(String status) {
        if (isBlank(status)) {
            return em.createQuery("select h from Helmet h order by h.code", Helmet.class).getResultList();
        }
        HelmetStatus st;
        try {
            st = HelmetStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知状态：" + status);
        }
        return em.createQuery("select h from Helmet h where h.status = :st order by h.code", Helmet.class)
                .setParameter("st", st).getResultList();
    }

    public HelmetDetail detail(long helmetId) {
        Helmet h = requireHelmet(helmetId);
        List<IssueRecord> issues = em.createQuery(
                        "select r from IssueRecord r where r.helmet = :h order by r.issuedAt desc", IssueRecord.class)
                .setParameter("h", h).getResultList();
        List<ImpactEvent> events = em.createQuery(
                        "select e from ImpactEvent e where e.helmet = :h order by e.reportedAt desc", ImpactEvent.class)
                .setParameter("h", h).getResultList();
        List<HelmetDetail.EventDto> eventDtos = events.stream()
                .map(e -> new HelmetDetail.EventDto(e, inspectionDtos(e)))
                .toList();
        return new HelmetDetail(h, currentHolderName(h), issues, eventDtos);
    }

    public List<ImpactEvent> listEvents(String status) {
        if (isBlank(status)) {
            return em.createQuery("select e from ImpactEvent e order by e.reportedAt desc", ImpactEvent.class)
                    .getResultList();
        }
        ImpactStatus st;
        try {
            st = ImpactStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知事件状态：" + status);
        }
        return em.createQuery("select e from ImpactEvent e where e.status = :st order by e.reportedAt", ImpactEvent.class)
                .setParameter("st", st).getResultList();
    }

    public HelmetDetail.EventDto eventDetail(long eventId) {
        ImpactEvent ev = em.find(ImpactEvent.class, eventId);
        if (ev == null) throw ApiException.notFound("撞击事件不存在");
        return new HelmetDetail.EventDto(ev, inspectionDtos(ev));
    }

    public List<RetirementRecord> listRetirements() {
        return em.createQuery("select r from RetirementRecord r order by r.retiredAt desc", RetirementRecord.class)
                .getResultList();
    }

    public ScanResult scan(String code) {
        if (isBlank(code)) throw ApiException.badRequest("扫码内容不能为空");
        Helmet h = findByCode(code.trim());
        if (h == null) {
            throw ApiException.notFound("编号 " + code + " 未登记，非本厂安全帽");
        }
        boolean canIssue;
        String blockReason = null;
        switch (h.status) {
            case IN_STOCK -> canIssue = true;
            case ISSUED -> {
                canIssue = false;
                blockReason = "已被 " + currentHolderName(h) + " 领用";
            }
            case FROZEN -> {
                canIssue = false;
                blockReason = "撞击后冻结，待安全员检查，禁止领用";
            }
            case RETIRED -> {
                canIssue = false;
                blockReason = "已退役，禁止领用（退役结论不可撤销）";
            }
            default -> canIssue = false;
        }
        return ScanResult.of(h, canIssue, blockReason, currentHolderName(h), openEvent(h));
    }

    public Attachment attachment(long id) {
        Attachment a = em.find(Attachment.class, id);
        if (a == null) throw ApiException.notFound("附件不存在");
        return a;
    }

    // ---------- 内部工具 ----------

    public Helmet findByCode(String code) {
        return em.createQuery("select h from Helmet h where h.code = :code", Helmet.class)
                .setParameter("code", code).getResultStream().findFirst().orElse(null);
    }

    private Helmet requireHelmet(long id) {
        Helmet h = em.find(Helmet.class, id);
        if (h == null) throw ApiException.notFound("安全帽不存在");
        return h;
    }

    private IssueRecord openIssue(Helmet h) {
        return em.createQuery("select r from IssueRecord r where r.helmet = :h and r.returnedAt is null",
                        IssueRecord.class)
                .setParameter("h", h).getResultStream().findFirst().orElse(null);
    }

    private ImpactEvent openEvent(Helmet h) {
        return em.createQuery("select e from ImpactEvent e where e.helmet = :h and e.status <> :closed",
                        ImpactEvent.class)
                .setParameter("h", h)
                .setParameter("closed", ImpactStatus.CLOSED)
                .getResultStream().findFirst().orElse(null);
    }

    private String currentHolderName(Helmet h) {
        IssueRecord open = openIssue(h);
        return open == null ? null : open.workerName;
    }

    private int countInspections(ImpactEvent ev) {
        return em.createQuery("select count(i) from Inspection i where i.impactEvent = :e", Long.class)
                .setParameter("e", ev).getSingleResult().intValue();
    }

    private List<HelmetDetail.InspectionDto> inspectionDtos(ImpactEvent ev) {
        List<Inspection> inspections = em.createQuery(
                        "select i from Inspection i where i.impactEvent = :e order by i.inspectedAt", Inspection.class)
                .setParameter("e", ev).getResultList();
        return inspections.stream()
                .map(i -> new HelmetDetail.InspectionDto(i, em.createQuery(
                                "select a from Attachment a where a.inspection = :i order by a.uploadedAt", Attachment.class)
                        .setParameter("i", i).getResultList()))
                .toList();
    }

    private void closeEvent(ImpactEvent ev, Decision decision) {
        ev.status = ImpactStatus.CLOSED;
        ev.closedAt = LocalDateTime.now();
        ev.finalDecision = decision;
    }

    private void saveAttachments(Inspection insp, List<FileUpload> files) {
        if (files == null) return;
        for (FileUpload f : files) {
            if (f == null || isBlank(f.fileName())) continue;
            try {
                Path dir = Path.of(attachmentsDir, String.valueOf(insp.id));
                Files.createDirectories(dir);
                String safeName = f.fileName().replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
                Path target = dir.resolve(UUID.randomUUID() + "_" + safeName);
                Files.copy(f.uploadedFile(), target);

                Attachment a = new Attachment();
                a.inspection = insp;
                a.originalName = f.fileName();
                a.storedPath = target.toString();
                a.sizeBytes = Files.size(target);
                em.persist(a);
            } catch (IOException e) {
                throw ApiException.badRequest("附件保存失败：" + f.fileName());
            }
        }
    }

    private static Severity parseSeverity(String s) {
        if (isBlank(s)) return Severity.UNKNOWN; // 未填写一律视为程度不明，走最严流程
        try {
            return Severity.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知撞击程度：" + s);
        }
    }

    private static PartCondition parsePart(String s, String label) {
        if (isBlank(s)) throw ApiException.badRequest(label + "不能为空");
        try {
            return PartCondition.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知" + label + "：" + s);
        }
    }

    private static Decision parseDecision(String s) {
        if (isBlank(s)) throw ApiException.badRequest("检查结论不能为空");
        try {
            return Decision.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知检查结论：" + s);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String statusLabel(HelmetStatus st) {
        return switch (st) {
            case IN_STOCK -> "在库";
            case ISSUED -> "已领出";
            case FROZEN -> "冻结";
            case RETIRED -> "已退役";
        };
    }
}
