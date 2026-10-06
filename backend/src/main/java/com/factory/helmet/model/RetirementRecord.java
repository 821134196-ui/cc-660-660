package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 退役记录。helmet_id 唯一 —— 一顶帽子只会有一条退役记录。
 * 系统不提供任何修改/删除退役记录的接口，退役结论不可通过库存操作撤销。
 */
@Entity
@Table(name = "retirement_records")
public class RetirementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "helmet_id", unique = true)
    public Helmet helmet;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "inspection_id")
    public Inspection inspection;

    @Column(nullable = false)
    public LocalDateTime retiredAt = LocalDateTime.now();

    /** 退役依据（来自检查记录） */
    @Column(nullable = false, length = 2000)
    public String reason;

    /** 作出结论的安全员 */
    @Column(nullable = false, length = 64)
    public String inspector;
}
