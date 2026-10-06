package com.factory.helmet.api;

import java.time.LocalDate;

/** 请求体 DTO */
public final class Requests {

    private Requests() {
    }

    /** 登记安全帽 */
    public record RegisterReq(String code, String batch, LocalDate manufactureDate, Integer serviceLifeMonths) {
    }

    /** 领用 */
    public record IssueReq(String workerName) {
    }

    /** 上报撞击 */
    public record ImpactReq(String description, String severity, String reportedBy) {
    }
}
