package com.factory.helmet.model;

/** 撞击事件状态：待检查 / 复检中（仍冻结）/ 已结案 */
public enum ImpactStatus {
    PENDING,
    IN_RECHECK,
    CLOSED
}
