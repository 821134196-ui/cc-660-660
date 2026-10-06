package com.factory.helmet.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 安全员检查记录。一次受冲击可对应多次检查（复检退回后须二次检查）。
 * 结论：
 *  REUSE       —— 恢复在役，必须填写下次检查日
 *  REINSPECT   —— 退回进一步复检，帽子保持冻结
 *  RETIRE      —— 永久退役，结论不可逆
 */
@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helmet_id", nullable = false)
    private Helmet helmet;

    /** 触发本次检查的撞击事件 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_event_id")
    private ImpactEvent impactEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShellCondition shellCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinerCondition linerCondition;

    /** 使用年限是否超期 */
    @Column(nullable = false)
    private boolean serviceLifeExpired;

    /** 判定依据（规范条款/实测描述，必填） */
    @Column(nullable = false, length = 1000)
    private String basis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InspectionResult result;

    /** 复用时的下次检查日（result=REUSE 时必填） */
    private LocalDate nextInspectionDate;

    @Column(nullable = false, length = 64)
    private String inspectorName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime inspectedAt;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionAttachment> attachments = new ArrayList<>();

    protected Inspection() {
    }

    public Inspection(Helmet helmet, ImpactEvent impactEvent, ShellCondition shellCondition,
                      LinerCondition linerCondition, boolean serviceLifeExpired, String basis,
                      InspectionResult result, LocalDate nextInspectionDate, String inspectorName) {
        this.helmet = helmet;
        this.impactEvent = impactEvent;
        this.shellCondition = shellCondition;
        this.linerCondition = linerCondition;
        this.serviceLifeExpired = serviceLifeExpired;
        this.basis = basis;
        this.result = result;
        this.nextInspectionDate = nextInspectionDate;
        this.inspectorName = inspectorName;
        this.inspectedAt = LocalDateTime.now();
    }

    public void addAttachment(InspectionAttachment attachment) {
        attachments.add(attachment);
    }

    public Long getId() {
        return id;
    }

    public Helmet getHelmet() {
        return helmet;
    }

    public ImpactEvent getImpactEvent() {
        return impactEvent;
    }

    public ShellCondition getShellCondition() {
        return shellCondition;
    }

    public LinerCondition getLinerCondition() {
        return linerCondition;
    }

    public boolean isServiceLifeExpired() {
        return serviceLifeExpired;
    }

    public String getBasis() {
        return basis;
    }

    public InspectionResult getResult() {
        return result;
    }

    public LocalDate getNextInspectionDate() {
        return nextInspectionDate;
    }

    public String getInspectorName() {
        return inspectorName;
    }

    public LocalDateTime getInspectedAt() {
        return inspectedAt;
    }

    public List<InspectionAttachment> getAttachments() {
        return attachments;
    }
}
