package com.factory.helmet.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 领用记录：一条对应一次领出/归还，构成领用历史 */
@Entity
@Table(name = "checkout_record")
public class CheckoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helmet_id", nullable = false)
    private Helmet helmet;

    /** 领用人 */
    @Column(nullable = false, length = 64)
    private String workerName;

    /** 班组 */
    @Column(length = 64)
    private String team;

    @Column(nullable = false)
    private LocalDateTime checkedOutAt;

    private LocalDateTime returnedAt;

    @Column(length = 255)
    private String note;

    protected CheckoutRecord() {
    }

    public CheckoutRecord(Helmet helmet, String workerName, String team, LocalDateTime checkedOutAt, String note) {
        this.helmet = helmet;
        this.workerName = workerName;
        this.team = team;
        this.checkedOutAt = checkedOutAt;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Helmet getHelmet() {
        return helmet;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getTeam() {
        return team;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public String getNote() {
        return note;
    }

    public boolean isOpen() {
        return returnedAt == null;
    }
}
