package com.factory.helmet.api;

import com.factory.helmet.service.HelmetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/helmets")
@Produces(MediaType.APPLICATION_JSON)
public class HelmetResource {

    @Inject
    HelmetService service;

    @GET
    public List<?> list(@QueryParam("status") String status) {
        return service.listHelmets(status);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(Requests.RegisterReq req) {
        var h = service.register(req.code(), req.batch(), req.manufactureDate(), req.serviceLifeMonths());
        return Response.status(Response.Status.CREATED).entity(h).build();
    }

    @GET
    @Path("/{id}")
    public HelmetDetail detail(@PathParam("id") long id) {
        return service.detail(id);
    }

    /** 领用。冻结/退役/已领出的帽子在此被拦截。 */
    @POST
    @Path("/{id}/issue")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object issue(@PathParam("id") long id, Requests.IssueReq req) {
        return service.issue(id, req.workerName());
    }

    @POST
    @Path("/{id}/return")
    public Object returnHelmet(@PathParam("id") long id) {
        return service.returnHelmet(id);
    }

    /** 上报撞击：立即冻结。 */
    @POST
    @Path("/{id}/impacts")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response reportImpact(@PathParam("id") long id, Requests.ImpactReq req) {
        var ev = service.reportImpact(id, req.description(), req.severity(), req.reportedBy());
        return Response.status(Response.Status.CREATED).entity(ev).build();
    }
}
