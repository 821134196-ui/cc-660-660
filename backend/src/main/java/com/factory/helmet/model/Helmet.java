package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 安全帽。编号 code 全局唯一且永久保留：
 * 退役后编号不回收、不重用，旧编号扫码仍命中本记录并被拦截。
 */
@Entity
@Table(name = "helmets")
public class Helmet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 唯一编号（扫码内容） */
    @Column(nullable = false, unique = true, length = 64)
    public String code;

    /** 生产批次 */
    @Column(nullable = false, length = 64)
    public String batch;

    /** 生产日期 */
    @Column(nullable = false)
    public LocalDate manufactureDate;

    /** 使用期限（月），常见为 30 个月 */
    @Column(nullable = false)
    public int serviceLifeMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public HelmetStatus status = HelmetStatus.IN_STOCK;

    /** 复用时注明的下次检查日期 */
    public LocalDate nextInspectionDate;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 退役时间与依据（快照，退役记录见 RetirementRecord） */
    public LocalDateTime retiredAt;

    @Column(length = 2000)
    public String retireReason;

    /** 使用期限到期日 */
    public LocalDate getServiceLifeEndDate() {
        return manufactureDate.plusMonths(serviceLifeMonths);
    }

    /** 是否已超过使用年限 */
    public boolean isServiceLifeExpired() {
        return getServiceLifeEndDate().isBefore(LocalDate.now());
    }
}
