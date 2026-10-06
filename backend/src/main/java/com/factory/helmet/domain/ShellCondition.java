package com.factory.helmet.domain;

/** 外壳检查结论 */
public enum ShellCondition {
    INTACT("外壳完好"),
    WORN("磨损/划痕"),
    CRACKED("裂纹/破裂/穿孔");

    private final String label;

    ShellCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
