package com.factory.helmet.api;

/**
 * 业务规则冲突：返回 409（如冻结帽领出、退役旧码重登记）。
 * 参数错误返回 400。
 */
public class BusinessRuleException extends RuntimeException {

    private final int status;

    public BusinessRuleException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static BusinessRuleException badRequest(String message) {
        return new BusinessRuleException(400, message);
    }

    public static BusinessRuleException conflict(String message) {
        return new BusinessRuleException(409, message);
    }

    public static BusinessRuleException notFound(String message) {
        return new BusinessRuleException(404, message);
    }

    public int getStatus() {
        return status;
    }
}
