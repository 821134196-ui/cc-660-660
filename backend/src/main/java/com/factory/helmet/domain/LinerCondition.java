package com.factory.helmet.domain;

/** 内衬/悬挂系统检查结论 */
public enum LinerCondition {
    INTACT("内衬完好"),
    AGED("老化/松弛"),
    DAMAGED("撕裂/脱开/缺失");

    private final String label;

    LinerCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
