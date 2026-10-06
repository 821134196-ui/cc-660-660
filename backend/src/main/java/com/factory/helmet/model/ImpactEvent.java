package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 撞击事件。上报即冻结帽子；结案前（含复检中）帽子保持冻结。
 * userAtImpact 为当时使用人快照，领用记录后续变化不影响事故追溯。
 */
@Entity
@Table(name = "impact_events")
public class ImpactEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "helmet_id")
    public Helmet helmet;

    @Column(nullable = false)
    public LocalDateTime reportedAt = LocalDateTime.now();

    @Column(length = 64)
    public String reportedBy;

    @Column(length = 2000)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Severity severity = Severity.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ImpactStatus status = ImpactStatus.PENDING;

    /** 撞击发生时使用人（快照）；撞击时在库则为空 */
    @Column(length = 64)
    public String userAtImpact;

    public LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public Decision finalDecision;
}
