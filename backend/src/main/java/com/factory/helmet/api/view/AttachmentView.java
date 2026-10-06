package com.factory.helmet.api.view;

import com.factory.helmet.domain.InspectionAttachment;

public record AttachmentView(Long id,
                             String fileName,
                             String contentType,
                             long sizeBytes,
                             String downloadUrl) {

    public static AttachmentView of(InspectionAttachment a) {
        return new AttachmentView(a.getId(), a.getFileName(), a.getContentType(),
                a.getSizeBytes(), "/api/attachments/" + a.getId() + "/content");
    }
}
