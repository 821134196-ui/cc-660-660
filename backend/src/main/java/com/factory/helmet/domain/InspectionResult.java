package com.factory.helmet.domain;

/** 安全员检查结论 */
public enum InspectionResult {
    REUSE("判定复用"),
    REINSPECT("退回复检"),
    RETIRE("判定退役");

    private final String label;

    InspectionResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
