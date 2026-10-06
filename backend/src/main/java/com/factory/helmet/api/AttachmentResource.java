package com.factory.helmet.api;

import com.factory.helmet.model.Attachment;
import com.factory.helmet.service.HelmetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 检查附件下载（本地文件）。 */
@Path("/api/attachments")
public class AttachmentResource {

    @Inject
    HelmetService service;

    @GET
    @Path("/{id}/download")
    public Response download(@PathParam("id") long id) throws Exception {
        Attachment a = service.attachment(id);
        java.nio.file.Path path = java.nio.file.Path.of(a.storedPath);
        if (!Files.exists(path)) {
            throw ApiException.notFound("附件文件已丢失：" + a.originalName);
        }
        String encoded = URLEncoder.encode(a.originalName, StandardCharsets.UTF_8).replace("+", "%20");
        return Response.ok(Files.readAllBytes(path))
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encoded)
                .build();
    }
}
