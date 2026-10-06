package com.factory.helmet.api;

import com.factory.helmet.model.Helmet;
import com.factory.helmet.model.ImpactEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 扫码结果：仓库扫码页据此展示状态大卡。
 * canIssue=false 时 blockReason 给出拦截原因（冻结中 / 已退役 / 已领出）。
 */
public record ScanResult(
        Long id,
        String code,
        String batch,
        String status,
        LocalDate manufactureDate,
        int serviceLifeMonths,
        LocalDate serviceLifeEndDate,
        boolean serviceLifeExpired,
        LocalDate nextInspectionDate,
        boolean canIssue,
        String blockReason,
        String currentHolder,
        OpenEvent openEvent,
        LocalDateTime retiredAt,
        String retireReason) {

    public record OpenEvent(
            Long id,
            String status,
            String severity,
            LocalDateTime reportedAt,
            String userAtImpact,
            String description) {

        public static OpenEvent of(ImpactEvent e) {
            return new OpenEvent(e.id, e.status.name(), e.severity.name(),
                    e.reportedAt, e.userAtImpact, e.description);
        }
    }

    public static ScanResult of(Helmet h, boolean canIssue, String blockReason,
                                String currentHolder, ImpactEvent openEvent) {
        return new ScanResult(
                h.id, h.code, h.batch, h.status.name(),
                h.manufactureDate, h.serviceLifeMonths,
                h.getServiceLifeEndDate(), h.isServiceLifeExpired(),
                h.nextInspectionDate,
                canIssue, blockReason, currentHolder,
                openEvent == null ? null : OpenEvent.of(openEvent),
                h.retiredAt, h.retireReason);
    }
}
