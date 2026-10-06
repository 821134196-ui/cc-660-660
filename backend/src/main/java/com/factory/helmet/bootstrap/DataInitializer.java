package com.factory.helmet.bootstrap;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.factory.helmet.domain.CheckoutRecord;
import com.factory.helmet.domain.Helmet;
import com.factory.helmet.domain.HelmetStatus;
import com.factory.helmet.domain.ImpactEvent;
import com.factory.helmet.domain.ImpactSeverity;
import com.factory.helmet.domain.Inspection;
import com.factory.helmet.domain.InspectionResult;
import com.factory.helmet.domain.LinerCondition;
import com.factory.helmet.domain.ShellCondition;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.persistence.EntityManager;

/**
 * 首次启动时写入演示数据，覆盖全部状态，便于仓库扫码直接体验：
 *  AQM-1001 在役可发 / AQM-1002 在役但被领用 / AQM-1003 受冲击冻结
 *  AQM-1004 复检退回（仍冻结）/ AQM-1005 已退役（旧码拦截）/ AQM-1006 超期在役（扫码拦截）
 */
@ApplicationScoped
public class DataInitializer {

    @Inject
    EntityManager em;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        // 测试环境不播种演示数据，保证用例数据干净
        if (LaunchMode.current() == LaunchMode.TEST) {
            return;
        }
        Long count = em.createQuery("select count(h) from Helmet h", Long.class).getSingleResult();
        if (count != null && count > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        // 1. 在役可发放
        em.persist(new Helmet("AQM-1001", "B2025-09", today.minusMonths(8)));

        // 2. 在役，但已被工人领用
        Helmet h2 = new Helmet("AQM-1002", "B2025-09", today.minusMonths(5));
        em.persist(h2);
        em.persist(new CheckoutRecord(h2, "张伟", "装配一班", LocalDateTime.now().minusDays(2), null));

        // 3. 受冲击冻结，等待安全员检查
        Helmet h3 = new Helmet("AQM-1003", "B2024-03", today.minusMonths(20));
        h3.setStatus(HelmetStatus.FROZEN);
        h3.setLastImpactAt(LocalDateTime.now().minusHours(5));
        em.persist(h3);
        CheckoutRecord r3 = new CheckoutRecord(h3, "李强", "冲压二班",
                LocalDateTime.now().minusDays(1), null);
        r3.setReturnedAt(LocalDateTime.now().minusHours(5));
        em.persist(r3);
        em.persist(new ImpactEvent(h3, ImpactSeverity.UNKNOWN, "李强",
                "冲压车间 B 区", "被滑落的料箱砸中，撞击程度待评估",
                LocalDateTime.now().minusHours(5)));

        // 4. 复检退回：第一次检查退回，仍冻结
        Helmet h4 = new Helmet("AQM-1004", "B2024-11", today.minusMonths(14));
        h4.setStatus(HelmetStatus.REINSPECTING);
        h4.setLastImpactAt(LocalDateTime.now().minusDays(3));
        em.persist(h4);
        ImpactEvent e4 = new ImpactEvent(h4, ImpactSeverity.LIGHT, "王芳",
                "装配线", "1.5 米高度跌落，帽壳待复测", LocalDateTime.now().minusDays(3));
        em.persist(e4);
        Inspection i4 = new Inspection(h4, e4, ShellCondition.WORN, LinerCondition.AGED,
                false, "GB 2811-2019 外观检查：帽壳有浅划痕，吸能性能存疑，提交实验室复测",
                InspectionResult.REINSPECT, null, "安全员-赵建国");
        em.persist(i4);

        // 5. 已退役：裂纹 + 超年限，判定退役，不可逆
        Helmet h5 = new Helmet("AQM-1005", "B2020-01", today.minusYears(5));
        h5.setStatus(HelmetStatus.RETIRED);
        h5.setLastImpactAt(LocalDateTime.now().minusDays(10));
        h5.setRetiredAt(LocalDateTime.now().minusDays(10));
        em.persist(h5);
        ImpactEvent e5 = new ImpactEvent(h5, ImpactSeverity.HEAVY, "陈军",
                "检修现场", "高处坠物直接击中帽顶", LocalDateTime.now().minusDays(10));
        em.persist(e5);
        Inspection i5 = new Inspection(h5, e5, ShellCondition.CRACKED, LinerCondition.DAMAGED,
                true, "GB 2811-2019：帽顶贯穿裂纹、衬带脱开，且已超 3 年使用年限，判废",
                InspectionResult.RETIRE, null, "安全员-赵建国");
        em.persist(i5);

        // 6. 状态在役但已超使用年限——扫码时后端按生产日期拦截
        em.persist(new Helmet("AQM-1006", "B2019-06", today.minusYears(6)));
    }
}
