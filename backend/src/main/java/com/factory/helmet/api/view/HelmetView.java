package com.factory.helmet.api.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.factory.helmet.domain.Helmet;
import com.factory.helmet.domain.HelmetStatus;

/** 安全帽扫描/列表视图：仓库扫码页据此醒目展示状态与能否领出 */
public record HelmetView(Long id,
                         String code,
                         String batch,
                         LocalDate manufactureDate,
                         HelmetStatus status,
                         String statusLabel,
                         LocalDate nextInspectionDate,
                         LocalDateTime lastImpactAt,
                         LocalDateTime retiredAt,
                         boolean serviceLifeExpired,
                         boolean canCheckout,
                         String blockReason,
                         String currentHolder) {

    public static HelmetView of(Helmet h, boolean serviceLifeExpired,
                                String currentHolder, String blockReason) {
        boolean canCheckout = h.getStatus() == HelmetStatus.IN_SERVICE
                && blockReason == null
                && currentHolder == null;
        String reason;
        if (blockReason != null) {
            reason = blockReason;
        } else if (currentHolder != null) {
            reason = "该帽尚在 " + currentHolder + " 手中，未归还";
        } else {
            reason = switch (h.getStatus()) {
                case FROZEN -> "受冲击已冻结，须经安全员检查";
                case REINSPECTING -> "复检退回期间，仍保持冻结";
                case RETIRED -> "已退役，旧编号永久作废";
                case IN_SERVICE -> null;
            };
        }
        return new HelmetView(h.getId(), h.getCode(), h.getBatch(), h.getManufactureDate(),
                h.getStatus(), h.getStatus().getLabel(), h.getNextInspectionDate(),
                h.getLastImpactAt(), h.getRetiredAt(), serviceLifeExpired,
                canCheckout, reason, currentHolder);
    }
}
