package com.factory.helmet.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 安全员检查结论提交。
 * 三种 result：REUSE（复用，须填 nextInspectionDate）/ REINSPECT（退回复检，保持冻结）/ RETIRE（退役）
 */
public record InspectionRequest(String shellCondition,
                                String linerCondition,
                                String result,
                                String basis,
                                String inspectorName,
                                LocalDate nextInspectionDate,
                                List<AttachmentInput> attachments) {
}
