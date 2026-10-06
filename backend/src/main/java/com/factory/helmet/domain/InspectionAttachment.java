package com.factory.helmet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 检查附件元数据；文件本体存在本地磁盘 upload 目录 */
@Entity
@Table(name = "inspection_attachment")
public class InspectionAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    /** 原始文件名 */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** 磁盘存储的相对路径 */
    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    protected InspectionAttachment() {
    }

    public InspectionAttachment(Inspection inspection, String fileName, String storagePath,
                                String contentType, long sizeBytes) {
        this.inspection = inspection;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
