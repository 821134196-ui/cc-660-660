package com.factory.helmet.api;

import com.factory.helmet.service.HelmetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** 仓库扫码：任何状态都返回结果，由 canIssue/blockReason 表达拦截。 */
@Path("/api/scan")
@Produces(MediaType.APPLICATION_JSON)
public class ScanResource {

    @Inject
    HelmetService service;

    @GET
    @Path("/{code}")
    public ScanResult scan(@PathParam("code") String code) {
        return service.scan(code);
    }
}
