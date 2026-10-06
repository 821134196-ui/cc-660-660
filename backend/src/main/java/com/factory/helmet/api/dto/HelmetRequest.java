package com.factory.helmet.api.dto;

import java.time.LocalDate;

/** 新帽登记 */
public record HelmetRequest(String code, String batch, LocalDate manufactureDate) {
}
