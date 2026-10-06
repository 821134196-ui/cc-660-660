package com.factory.helmet.api.view;

import java.time.LocalDateTime;

import com.factory.helmet.domain.ImpactEvent;
import com.factory.helmet.domain.ImpactSeverity;

/** 撞击事件视图：holderName 即事故当时使用人 */
public record ImpactView(Long id,
                         ImpactSeverity severity,
                         String severityLabel,
                         String holderName,
                         String location,
                         String description,
                         LocalDateTime occurredAt,
                         LocalDateTime reportedAt) {

    public static ImpactView of(ImpactEvent e) {
        return new ImpactView(e.getId(), e.getSeverity(), e.getSeverity().getLabel(),
                e.getHolderName(), e.getLocation(), e.getDescription(),
                e.getOccurredAt(), e.getReportedAt());
    }
}
