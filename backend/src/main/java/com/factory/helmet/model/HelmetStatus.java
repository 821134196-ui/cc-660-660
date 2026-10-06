package com.factory.helmet.model;

/** 安全帽状态：在库 / 已领出 / 冻结（撞击待检或复检中）/ 已退役 */
public enum HelmetStatus {
    IN_STOCK,
    ISSUED,
    FROZEN,
    RETIRED
}
