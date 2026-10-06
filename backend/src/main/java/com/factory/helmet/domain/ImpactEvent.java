package com.factory.helmet.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 受冲击事件。撞击后立即登记，系统据此冻结帽子。
 * holderName 记录撞击发生时的实际使用人，事后可追溯。
 */
@Entity
@Table(name = "impact_event")
public class ImpactEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helmet_id", nullable = false)
    private Helmet helmet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImpactSeverity severity;

    /** 撞击发生时使用人（事故追溯依据） */
    @Column(nullable = false, length = 64)
    private String holderName;

    @Column(length = 64)
    private String location;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    protected ImpactEvent() {
    }

    public ImpactEvent(Helmet helmet, ImpactSeverity severity, String holderName,
                       String location, String description, LocalDateTime occurredAt) {
        this.helmet = helmet;
        this.severity = severity;
        this.holderName = holderName;
        this.location = location;
        this.description = description;
        this.occurredAt = occurredAt;
        this.reportedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Helmet getHelmet() {
        return helmet;
    }

    public ImpactSeverity getSeverity() {
        return severity;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }
}
