package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 领用记录：谁在什么时间领走了哪顶帽子，归还时间可空（空=在用）。 */
@Entity
@Table(name = "issue_records")
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "helmet_id")
    public Helmet helmet;

    @Column(nullable = false, length = 64)
    public String workerName;

    @Column(nullable = false)
    public LocalDateTime issuedAt = LocalDateTime.now();

    public LocalDateTime returnedAt;

    /** 归还备注（如：撞击自动收回） */
    @Column(length = 500)
    public String returnNote;
}
