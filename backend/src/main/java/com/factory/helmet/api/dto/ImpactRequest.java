package com.factory.helmet.api.dto;

import java.time.LocalDateTime;

/**
 * 受冲击上报。severity 缺省按 UNKNOWN（程度不明）处理——
 * 程度不明不得默认合格，一律冻结。
 */
public record ImpactRequest(String severity,
                            String holderName,
                            String location,
                            String description,
                            LocalDateTime occurredAt) {
}
