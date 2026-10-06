package com.factory.helmet.api.dto;

/** 检查附件（base64 内联提交，文件落本地磁盘） */
public record AttachmentInput(String fileName, String contentType, String contentBase64) {
}
