package com.factory.helmet.api;

import com.factory.helmet.service.HelmetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** 退役台账：只读。退役结论没有修改/删除入口。 */
@Path("/api/retirements")
@Produces(MediaType.APPLICATION_JSON)
public class RetirementResource {

    @Inject
    HelmetService service;

    @GET
    public List<?> list() {
        return service.listRetirements();
    }
}
