package com.factory.helmet;

import com.factory.helmet.model.Helmet;
import com.factory.helmet.service.HelmetService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;

/**
 * 首次启动（库为空）时写入演示数据，覆盖：在库 / 已领出 / 冻结待检 /
 * 复检中 / 已退役 / 已超期 等各种状态，方便扫码页与检查台直接体验。
 * 生产日期均相对当前日期取值，保证演示数据不会随时间过期失真。
 */
@ApplicationScoped
public class SeedData {

    @Inject
    EntityManager em;

    @Inject
    HelmetService service;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        Long count = em.createQuery("select count(h) from Helmet h", Long.class).getSingleResult();
        if (count > 0) return;

        LocalDate now = LocalDate.now();

        // 在库
        service.register("HMT-1001", "P2024-A", now.minusMonths(6), 30);
        // 已领出
        Helmet h2 = service.register("HMT-1002", "P2024-A", now.minusMonths(6), 30);
        service.issue(h2.id, "张伟");
        // 冻结待检：领用中发生撞击，程度不明
        Helmet h3 = service.register("HMT-1003", "P2023-B", now.minusMonths(10), 30);
        service.issue(h3.id, "李强");
        service.reportImpact(h3.id, "高处坠物砸中帽顶，现场无法判断损伤程度", "UNKNOWN", "班组长王磊");
        // 复检中：首轮检查要求进一步检测
        Helmet h4 = service.register("HMT-1004", "P2023-B", now.minusMonths(10), 30);
        var ev4 = service.reportImpact(h4.id, "货架倾倒碰撞", "MODERATE", "仓库员赵敏");
        service.inspect(ev4.id, "安全员陈工", "OK", "OK",
                "外壳无明显裂痕，但受中等撞击，需送第三方做冲击吸收复测", "RECHECK", null, null);
        // 已退役：严重撞击后直接退役
        Helmet h5 = service.register("HMT-1005", "P2022-C", now.minusMonths(20), 30);
        var ev5 = service.reportImpact(h5.id, "被坠落钢管正面击中，帽壳开裂", "SEVERE", "安全员陈工");
        service.inspect(ev5.id, "安全员陈工", "DAMAGED", "OK",
                "帽壳贯穿性裂纹，依据 GB 2811 直接报废", "RETIRE", null, null);
        // 在库
        service.register("HMT-1006", "P2024-D", now.minusMonths(3), 30);
        // 在库但已超期（演示：超期帽子撞击后不能复用，只能退役）
        service.register("HMT-1007", "P2021-E", now.minusMonths(40), 30);
    }
}
