package com.factory.helmet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * 关键安全规则端到端测试：
 *  - 撞击（含程度不明）立即冻结，冻结帽禁止领出
 *  - 复检退回期间仍冻结、禁止领出；二次检查复用后恢复发放
 *  - 退役后：领用拦截、库存编辑无法撤销、同号重新登记拦截、扫码显示退役
 *  - 复用必须填下次检查日；裂壳/超年限不得复用
 *  - 事故记录可查到当时使用人
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelmetLifecycleTest {

    private static final String BASE = "/api/helmets";

    private static Map<String, Object> helmet(String code, String batch, String date) {
        return Map.of("code", code, "batch", batch, "manufactureDate", date);
    }

    private void register(String code, String batch, String manufactureDate) {
        given().contentType(ContentType.JSON).body(helmet(code, batch, manufactureDate))
                .when().post(BASE)
                .then().statusCode(200);
    }

    // ---------- 1. 受冲击立即冻结（程度不明不默认合格） ----------

    @Test
    @Order(1)
    void impact_unknownSeverity_freezesAndBlocksCheckout() {
        String code = "TC-UNKNOWN-01";
        register(code, "B-T1", LocalDate.now().minusMonths(3).toString());

        // 撞击程度不明（severity 传 UNKNOWN），必须填使用人
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "UNKNOWN", "holderName", "工人甲",
                        "location", "一车间"))
                .when().post(BASE + "/" + code + "/impacts")
                .then().statusCode(200)
                .body("severity", equalTo("UNKNOWN"))
                .body("holderName", equalTo("工人甲"));

        // 扫码状态为冻结
        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("FROZEN"))
                .body("[0].canCheckout", equalTo(false))
                .body("[0].blockReason", containsString("冻结"));

        // 冻结帽不能领出
        given().contentType(ContentType.JSON)
                .body(Map.of("workerName", "仓库想直接发出去"))
                .when().post(BASE + "/" + code + "/checkout")
                .then().statusCode(409)
                .body("error", containsString("冻结"));

        // 撞击不填使用人：拒绝（事故须可追溯到人）
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "LIGHT"))
                .when().post(BASE + "/" + code + "/impacts")
                .then().statusCode(400);
    }

    // ---------- 2. 复检退回：期间仍冻结，领用持续被拦；二次检查复用恢复 ----------

    @Test
    @Order(2)
    void reinspect_keepsFrozen_thenSecondInspectionReuseRestores() {
        String code = "TC-REINSPECT-02";
        register(code, "B-T2", LocalDate.now().minusMonths(10).toString());

        // 发生轻微撞击 → 冻结
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "LIGHT", "holderName", "工人乙"))
                .when().post(BASE + "/" + code + "/impacts")
                .then().statusCode(200);

        // 第一次检查：退回复检
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "shellCondition", "WORN",
                        "linerCondition", "AGED",
                        "result", "REINSPECT",
                        "basis", "帽壳浅划痕，吸能性待实验室复测",
                        "inspectorName", "安全员-赵"))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(200)
                .body("result", equalTo("REINSPECT"));

        // 复检期间：状态 REINSPECTING，仓库仍不可领出
        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("REINSPECTING"))
                .body("[0].canCheckout", equalTo(false))
                .body("[0].blockReason", containsString("复检"));

        given().contentType(ContentType.JSON)
                .body(Map.of("workerName", "复检期想领用"))
                .when().post(BASE + "/" + code + "/checkout")
                .then().statusCode(409)
                .body("error", containsString("复检"));

        // 复检期间二次撞击也允许登记，状态压回 FROZEN（这里不验证，直接二次检查）

        // 第二次检查：外壳/内衬合格、未超年限 → 复用，必须给下次检查日
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "shellCondition", "INTACT",
                        "linerCondition", "INTACT",
                        "result", "REUSE",
                        "basis", "复测吸能性能合格，外观无结构损伤，符合 GB 2811-2019",
                        "inspectorName", "安全员-赵",
                        "nextInspectionDate", LocalDate.now().plusMonths(6).toString()))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(200)
                .body("result", equalTo("REUSE"));

        // 恢复在役，扫码可领出
        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("IN_SERVICE"))
                .body("[0].canCheckout", equalTo(true))
                .body("[0].blockReason", nullValue());

        given().contentType(ContentType.JSON)
                .body(Map.of("workerName", "工人丙", "team", "装配班"))
                .when().post(BASE + "/" + code + "/checkout")
                .then().statusCode(200)
                .body("workerName", equalTo("工人丙"))
                .body("open", equalTo(true));
    }

    @Test
    @Order(3)
    void reuse_requiresNextInspectionDateAndSoundShell() {
        String code = "TC-REUSE-INVALID-03";
        register(code, "B-T3", LocalDate.now().minusMonths(4).toString());
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "LIGHT", "holderName", "工人丁"))
                .when().post(BASE + "/" + code + "/impacts").then().statusCode(200);

        // 复用但不给下次检查日 → 400
        given().contentType(ContentType.JSON)
                .body(Map.of("shellCondition", "INTACT", "linerCondition", "INTACT",
                        "result", "REUSE", "basis", "检查合格", "inspectorName", "安全员-钱"))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(400)
                .body("error", containsString("下次检查日"));

        // 外壳裂纹却要复用 → 400，绝不允许放行
        given().contentType(ContentType.JSON)
                .body(Map.of("shellCondition", "CRACKED", "linerCondition", "INTACT",
                        "result", "REUSE", "basis", "试图放行", "inspectorName", "安全员-钱",
                        "nextInspectionDate", LocalDate.now().plusMonths(1).toString()))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(400)
                .body("error", containsString("裂纹"));
    }

    // ---------- 3. 退役：领用/改库存/重新登记 三路全部拦截 ----------

    @Test
    @Order(4)
    void retired_helmetIsBlockedEverywhere() {
        String code = "TC-RETIRE-04";
        register(code, "B-T4", LocalDate.now().minusYears(4).toString()); // 超 3 年年限

        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "HEAVY", "holderName", "工人戊"))
                .when().post(BASE + "/" + code + "/impacts").then().statusCode(200);

        // 判定退役（超年限，系统计算 serviceLifeExpired=true）
        given().contentType(ContentType.JSON)
                .body(Map.of("shellCondition", "CRACKED", "linerCondition", "DAMAGED",
                        "result", "RETIRE",
                        "basis", "帽顶裂纹、衬带脱开，超使用年限，GB 2811-2019 判废",
                        "inspectorName", "安全员-孙"))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(200)
                .body("result", equalTo("RETIRE"))
                .body("serviceLifeExpired", equalTo(true));

        // 3.1 扫码：退役 + 不可领出
        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("RETIRED"))
                .body("[0].canCheckout", equalTo(false))
                .body("[0].blockReason", containsString("退役"));

        // 3.2 仓库直接领出 → 409 拦截
        given().contentType(ContentType.JSON)
                .body(Map.of("workerName", "旧码领用尝试"))
                .when().post(BASE + "/" + code + "/checkout")
                .then().statusCode(409)
                .body("error", containsString("退役"));

        // 3.3 修改库存无法撤销退役：改批次/日期后状态仍是 RETIRED
        given().contentType(ContentType.JSON)
                .body(Map.of("batch", "B-EDITED",
                        "manufactureDate", LocalDate.now().minusMonths(2).toString()))
                .when().put(BASE + "/" + code)
                .then().statusCode(200)
                .body("batch", equalTo("B-EDITED"))
                .body("status", equalTo("RETIRED"))
                .body("canCheckout", equalTo(false));

        // 3.4 夹带 status 字段试图改状态 → 未知字段直接 400
        given().contentType(ContentType.JSON)
                .body(Map.of("batch", "B-HACK", "status", "IN_SERVICE"))
                .when().put(BASE + "/" + code)
                .then().statusCode(400);

        // 3.5 退役后再做检查翻案 → 409
        given().contentType(ContentType.JSON)
                .body(Map.of("shellCondition", "INTACT", "linerCondition", "INTACT",
                        "result", "REUSE", "basis", "想翻案", "inspectorName", "安全员-李",
                        "nextInspectionDate", LocalDate.now().plusMonths(6).toString()))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(409);

        // 3.6 旧编号再次扫码登记 → 409 拦截，编号永久作废
        given().contentType(ContentType.JSON)
                .body(helmet(code, "B-NEW", LocalDate.now().minusMonths(1).toString()))
                .when().post(BASE)
                .then().statusCode(409)
                .body("error", containsString("退役"));
    }

    // ---------- 4. 事故记录可追溯当时使用人 ----------

    @Test
    @Order(5)
    void impactHistory_showsHolderAtTimeOfAccident() {
        String code = "TC-HOLDER-05";
        register(code, "B-T5", LocalDate.now().minusMonths(6).toString());

        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "HEAVY", "holderName", "钱多多",
                        "location", "3 号高炉", "description", "高空坠物"))
                .when().post(BASE + "/" + code + "/impacts").then().statusCode(200);

        given().when().get(BASE + "/" + code)
                .then().statusCode(200)
                .body("helmet.status", equalTo("FROZEN"))
                .body("impacts.size()", equalTo(1))
                .body("impacts[0].holderName", equalTo("钱多多"))
                .body("impacts[0].location", equalTo("3 号高炉"));
    }

    // ---------- 5. 超使用年限：即便状态在役，扫码也拦截 ----------

    @Test
    @Order(6)
    void expiredServiceLife_blocksCheckoutOnScan() {
        String code = "TC-EXPIRED-06";
        register(code, "B-T6", LocalDate.now().minusYears(5).toString());

        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("IN_SERVICE"))
                .body("[0].serviceLifeExpired", equalTo(true))
                .body("[0].canCheckout", equalTo(false))
                .body("[0].blockReason", containsString("年限"));

        // 服务端领用闸口同样拦截超年限帽
        given().contentType(ContentType.JSON)
                .body(Map.of("workerName", "侥幸领取"))
                .when().post(BASE + "/" + code + "/checkout")
                .then().statusCode(409)
                .body("error", containsString("年限"));
    }

    // ---------- 6. 归还后继续冻结，且在役帽被领出期间列表显示使用人 ----------

    @Test
    @Order(7)
    void returnToWarehouse_keepsFrozenForInspection() {
        String code = "TC-RETURN-07";
        register(code, "B-T7", LocalDate.now().minusMonths(8).toString());

        // 先领出
        given().contentType(ContentType.JSON).body(Map.of("workerName", "周强", "team", "维修班"))
                .when().post(BASE + "/" + code + "/checkout").then().statusCode(200);
        // 使用中受冲击（仓库立即冻结，等待交回）
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "UNKNOWN", "holderName", "周强"))
                .when().post(BASE + "/" + code + "/impacts").then().statusCode(200);
        // 交回仓库：允许归还
        given().contentType(ContentType.JSON).body(Map.of("note", "砸了一下"))
                .when().post(BASE + "/" + code + "/return")
                .then().statusCode(200).body("open", equalTo(false));
        // 已归还但仍冻结，不能再发
        given().when().get(BASE + "?code=" + code)
                .then().statusCode(200)
                .body("[0].status", equalTo("FROZEN"))
                .body("[0].canCheckout", equalTo(false));
        // 档案中能看到事故时使用人就是周强
        given().when().get(BASE + "/" + code)
                .then().body("impacts[0].holderName", equalTo("周强"))
                .body("checkouts[0].workerName", equalTo("周强"));
    }

    // ---------- 7. 附件：检查带附件，落盘后可下载 ----------

    @Test
    @Order(8)
    void inspectionAttachment_isStoredAndDownloadable() {
        String code = "TC-ATTACH-08";
        register(code, "B-T8", LocalDate.now().minusMonths(2).toString());
        given().contentType(ContentType.JSON)
                .body(Map.of("severity", "LIGHT", "holderName", "吴敏"))
                .when().post(BASE + "/" + code + "/impacts").then().statusCode(200);

        // "helmet-crack-check" 的 base64
        String b64 = java.util.Base64.getEncoder()
                .encodeToString("helmet-crack-check".getBytes());

        Integer attachmentId = given().contentType(ContentType.JSON)
                .body(Map.of("shellCondition", "INTACT", "linerCondition", "INTACT",
                        "result", "REUSE", "basis", "外观与性能复检合格",
                        "inspectorName", "安全员-周",
                        "nextInspectionDate", LocalDate.now().plusMonths(3).toString(),
                        "attachments", java.util.List.of(
                                Map.of("fileName", "shell-photo.txt",
                                        "contentType", "text/plain",
                                        "contentBase64", b64))))
                .when().post(BASE + "/" + code + "/inspections")
                .then().statusCode(200)
                .body("attachments.size()", equalTo(1))
                .extract().path("attachments[0].id");

        given().when().get("/api/attachments/" + attachmentId + "/content")
                .then().statusCode(200)
                .body(equalTo("helmet-crack-check"));
    }
}
