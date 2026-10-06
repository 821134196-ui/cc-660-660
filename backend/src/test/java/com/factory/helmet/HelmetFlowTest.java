package com.factory.helmet;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 核心业务规则测试：
 *  - 撞击即冻结，冻结禁止领用
 *  - 复检退回后仍保持冻结，再检合格才可领用
 *  - 退役不可逆：禁止领用、扫码拦截、旧编号重复登记拦截
 *  - 程度不明 / 超期 / 部件损伤 不得复用
 */
@QuarkusTest
class HelmetFlowTest {

    private long register(String code) {
        // 生产日期取上个月，保证 30 个月期限内（避免测试随时间腐烂）
        String mfg = java.time.LocalDate.now().minusMonths(1).toString();
        return given().contentType("application/json")
                .body("""
                        {"code":"%s","batch":"T-BATCH","manufactureDate":"%s","serviceLifeMonths":30}
                        """.formatted(code, mfg))
                .when().post("/api/helmets")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private long registerExpired(String code) {
        return given().contentType("application/json")
                .body("""
                        {"code":"%s","batch":"T-BATCH","manufactureDate":"2020-01-01","serviceLifeMonths":30}
                        """.formatted(code))
                .when().post("/api/helmets")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private long reportImpact(long helmetId, String severity) {
        return given().contentType("application/json")
                .body("""
                        {"description":"测试撞击","severity":"%s","reportedBy":"测试员"}
                        """.formatted(severity))
                .when().post("/api/helmets/" + helmetId + "/impacts")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private io.restassured.response.Response inspect(long eventId, String shell, String liner,
                                                     String decision, String nextDate) {
        var req = given()
                .multiPart("inspector", "安全员测试")
                .multiPart("shellCondition", shell)
                .multiPart("linerCondition", liner)
                .multiPart("basis", "测试依据")
                .multiPart("decision", decision);
        if (nextDate != null) {
            req.multiPart("nextInspectionDate", nextDate);
        }
        return req.when().post("/api/impacts/" + eventId + "/inspections");
    }

    @Test
    void impactFreezesHelmetAndBlocksIssue() {
        long id = register("T-FRZ-001");
        reportImpact(id, "MINOR");

        // 冻结状态扫码：不可领用
        given().when().get("/api/scan/T-FRZ-001")
                .then().statusCode(200)
                .body("status", is("FROZEN"))
                .body("canIssue", is(false))
                .body("blockReason", containsString("冻结"));

        // 冻结状态领用：被拦截
        given().contentType("application/json").body("{\"workerName\":\"工人甲\"}")
                .when().post("/api/helmets/" + id + "/issue")
                .then().statusCode(409)
                .body("error", containsString("冻结"));
    }

    @Test
    void recheckKeepsFrozenUntilPassed() {
        long id = register("T-RCK-001");
        long eventId = reportImpact(id, "MODERATE");

        // 结论=复检：事件转复检中，帽子仍冻结
        inspect(eventId, "OK", "OK", "RECHECK", null).then().statusCode(201);

        given().when().get("/api/scan/T-RCK-001")
                .then().statusCode(200)
                .body("status", is("FROZEN"))
                .body("canIssue", is(false))
                .body("openEvent.status", is("IN_RECHECK"));

        // 复检期间领用仍被拦截
        given().contentType("application/json").body("{\"workerName\":\"工人乙\"}")
                .when().post("/api/helmets/" + id + "/issue")
                .then().statusCode(409);

        // 复检退回后再次检查，结论复用 → 解冻可领
        inspect(eventId, "OK", "OK", "REUSE", "2027-01-01").then().statusCode(201);
        given().when().get("/api/scan/T-RCK-001")
                .then().statusCode(200)
                .body("status", is("IN_STOCK"))
                .body("canIssue", is(true))
                .body("nextInspectionDate", is("2027-01-01"));

        given().contentType("application/json").body("{\"workerName\":\"工人乙\"}")
                .when().post("/api/helmets/" + id + "/issue")
                .then().statusCode(200);
    }

    @Test
    void retireIsFinalAndOldCodeBlocked() {
        long id = register("T-RET-001");
        long eventId = reportImpact(id, "SEVERE");
        inspect(eventId, "DAMAGED", "OK", "RETIRE", null).then().statusCode(201);

        // 退役后扫码：拦截
        given().when().get("/api/scan/T-RET-001")
                .then().statusCode(200)
                .body("status", is("RETIRED"))
                .body("canIssue", is(false))
                .body("blockReason", containsString("退役"));

        // 退役后领用：拦截
        given().contentType("application/json").body("{\"workerName\":\"工人丙\"}")
                .when().post("/api/helmets/" + id + "/issue")
                .then().statusCode(409)
                .body("error", containsString("退役"));

        // 旧编号再次登记：拦截（编号永久保留）
        given().contentType("application/json")
                .body("{\"code\":\"T-RET-001\",\"batch\":\"NEW\",\"manufactureDate\":\"2026-01-01\",\"serviceLifeMonths\":30}")
                .when().post("/api/helmets")
                .then().statusCode(409)
                .body("error", containsString("已登记"));

        // 退役台账可查
        given().when().get("/api/retirements")
                .then().statusCode(200)
                .body("find { it.helmet.code == 'T-RET-001' }.reason", notNullValue());
    }

    @Test
    void unknownSeverityCannotBeReused() {
        long id = register("T-UNK-001");
        long eventId = reportImpact(id, "UNKNOWN");

        // 程度不明 → 复用被拒（不能默认合格）
        inspect(eventId, "OK", "OK", "REUSE", "2027-01-01").then()
                .statusCode(400)
                .body("error", containsString("程度不明"));

        // 仍冻结；可改判复检
        inspect(eventId, "OK", "OK", "RECHECK", null).then().statusCode(201);
        given().when().get("/api/scan/T-UNK-001")
                .then().statusCode(200)
                .body("status", is("FROZEN"));
    }

    @Test
    void expiredHelmetMustRetire() {
        long id = registerExpired("T-EXP-001");
        long eventId = reportImpact(id, "MINOR");

        inspect(eventId, "OK", "OK", "REUSE", "2027-01-01").then()
                .statusCode(400)
                .body("error", containsString("使用年限"));

        inspect(eventId, "OK", "OK", "RETIRE", null).then().statusCode(201);
        given().when().get("/api/scan/T-EXP-001")
                .then().statusCode(200)
                .body("status", is("RETIRED"));
    }

    @Test
    void damagedPartsCannotBeReused() {
        long id = register("T-DMG-001");
        long eventId = reportImpact(id, "MINOR");
        inspect(eventId, "OK", "DAMAGED", "REUSE", "2027-01-01").then()
                .statusCode(400)
                .body("error", containsString("损伤"));
    }

    @Test
    void reuseRequiresNextInspectionDate() {
        long id = register("T-NXT-001");
        long eventId = reportImpact(id, "MINOR");
        inspect(eventId, "OK", "OK", "REUSE", null).then()
                .statusCode(400)
                .body("error", containsString("下次检查日期"));
    }

    @Test
    void impactSnapshotsCurrentUser() {
        long id = register("T-USR-001");
        given().contentType("application/json").body("{\"workerName\":\"工人丁\"}")
                .when().post("/api/helmets/" + id + "/issue")
                .then().statusCode(200);

        reportImpact(id, "MINOR");

        // 事故记录可查到当时使用人
        given().when().get("/api/scan/T-USR-001")
                .then().statusCode(200)
                .body("openEvent.userAtImpact", is("工人丁"));
    }

    @Test
    void unknownCodeScanReturns404() {
        given().when().get("/api/scan/NO-SUCH-CODE")
                .then().statusCode(404);
    }
}
