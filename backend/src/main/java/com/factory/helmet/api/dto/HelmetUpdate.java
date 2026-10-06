package com.factory.helmet.api.dto;

import java.time.LocalDate;

/**
 * 库存资料编辑。刻意不包含 status / retiredAt 等字段：
 * 退役结论不能通过修改库存撤销；配合 fail-on-unknown-properties，
 * 请求体夹带 status 会直接 400。
 */
public record HelmetUpdate(String batch, LocalDate manufactureDate) {
}
