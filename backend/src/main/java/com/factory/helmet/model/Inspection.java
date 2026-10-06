package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一次检查记录（含复检）。结论为 RECHECK 时事件转入复检中，帽子保持冻结。 */
@Entity
@Table(name = "inspections")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "impact_event_id")
    public ImpactEvent impactEvent;

    /** 第几轮检查（复检时递增） */
    @Column(nullable = false)
    public int roundNo;

    /** 安全员 */
    @Column(nullable = false, length = 64)
    public String inspector;

    @Column(nullable = false)
    public LocalDateTime inspectedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public PartCondition shellCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public PartCondition linerCondition;

    /** 检查时是否仍在使用年限内（服务端按生产日期+期限计算） */
    @Column(nullable = false)
    public boolean serviceLifeOk;

    /** 检查依据（必填） */
    @Column(nullable = false, length = 2000)
    public String basis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Decision decision;

    /** 结论为复用时必填：下次检查日期 */
    public LocalDate nextInspectionDate;
}
