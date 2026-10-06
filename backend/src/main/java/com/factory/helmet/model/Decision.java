package com.factory.helmet.model;

/** 检查结论：复用 / 复检（退回再检，保持冻结）/ 退役（不可逆） */
public enum Decision {
    REUSE,
    RECHECK,
    RETIRE
}
