package com.factory.helmet.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 安全帽主档。编号唯一，退役后编号永久保留、禁止再次登记。
 * 状态只能通过业务服务流转（撞击/检查），库存编辑接口不得修改 status。
 */
@Entity
@Table(name = "helmet")
public class Helmet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一编号（喷码/条码） */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 生产批次 */
    @Column(nullable = false, length = 64)
    private String batch;

    @Column(nullable = false)
    private LocalDate manufactureDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelmetStatus status = HelmetStatus.IN_SERVICE;

    /** 复用时注明的下次检查日 */
    private LocalDate nextInspectionDate;

    /** 最近一次受冲击时间（扫描页提示用） */
    private LocalDateTime lastImpactAt;

    /** 退役时间，退役结论不可逆 */
    private LocalDateTime retiredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Helmet() {
    }

    public Helmet(String code, String batch, LocalDate manufactureDate) {
        this.code = code;
        this.batch = batch;
        this.manufactureDate = manufactureDate;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public void setManufactureDate(LocalDate manufactureDate) {
        this.manufactureDate = manufactureDate;
    }

    public HelmetStatus getStatus() {
        return status;
    }

    public void setStatus(HelmetStatus status) {
        this.status = status;
    }

    public LocalDate getNextInspectionDate() {
        return nextInspectionDate;
    }

    public void setNextInspectionDate(LocalDate nextInspectionDate) {
        this.nextInspectionDate = nextInspectionDate;
    }

    public LocalDateTime getLastImpactAt() {
        return lastImpactAt;
    }

    public void setLastImpactAt(LocalDateTime lastImpactAt) {
        this.lastImpactAt = lastImpactAt;
    }

    public LocalDateTime getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(LocalDateTime retiredAt) {
        this.retiredAt = retiredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
