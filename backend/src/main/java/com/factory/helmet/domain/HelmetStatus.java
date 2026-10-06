package com.factory.helmet.domain;

/**
 * 安全帽生命周期状态。
 * IN_SERVICE：在库可发放（或在工人手中使用中，可正常归还）
 * FROZEN：受冲击冻结 / 复检中，仓库禁止领出
 * RETIRED：退役，编号永久作废
 */
public enum HelmetStatus {
    IN_SERVICE("在役可发放"),
    FROZEN("受冲击冻结"),
    REINSPECTING("复检验证中"),
    RETIRED("已退役");

    private final String label;

    HelmetStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
