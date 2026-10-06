package com.factory.helmet.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.factory.helmet.domain.InspectionAttachment;
import com.factory.helmet.service.AttachmentStorage;
import com.factory.helmet.service.HelmetService;

@Path("/api/attachments")
public class AttachmentResource {

    @Inject
    HelmetService service;

    @Inject
    AttachmentStorage storage;

    /** 下载本地存储的检查附件 */
    @GET
    @Path("/{id}/content")
    @Transactional
    public Response download(@PathParam("id") Long id) {
        InspectionAttachment a = service.findAttachment(id);
        byte[] bytes = storage.read(a.getStoragePath());
        MediaType type;
        try {
            type = MediaType.valueOf(a.getContentType());
        } catch (IllegalArgumentException e) {
            type = MediaType.APPLICATION_OCTET_STREAM_TYPE;
        }
        return Response.ok(bytes, type)
                .header("Content-Disposition", "inline; filename=\"" + a.getFileName() + "\"")
                .build();
    }
}
