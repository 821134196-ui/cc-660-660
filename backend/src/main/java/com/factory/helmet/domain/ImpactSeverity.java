package com.factory.helmet.domain;

/** 撞击程度：不明时按最严处理，禁止默认合格 */
public enum ImpactSeverity {
    HEAVY("重击（明显坠落/重物击打）"),
    LIGHT("轻微撞击"),
    UNKNOWN("程度不明");

    private final String label;

    ImpactSeverity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
