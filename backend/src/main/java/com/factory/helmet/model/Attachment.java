package com.factory.helmet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 检查附件（照片/报告），文件存本地磁盘，此处记录路径。 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "inspection_id")
    public Inspection inspection;

    @Column(nullable = false, length = 255)
    public String originalName;

    @Column(nullable = false, length = 512)
    public String storedPath;

    @Column(nullable = false)
    public long sizeBytes;

    @Column(nullable = false)
    public LocalDateTime uploadedAt = LocalDateTime.now();
}
