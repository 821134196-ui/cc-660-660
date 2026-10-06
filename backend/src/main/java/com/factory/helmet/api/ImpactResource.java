package com.factory.helmet.api;

import com.factory.helmet.service.HelmetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Path("/api/impacts")
@Produces(MediaType.APPLICATION_JSON)
public class ImpactResource {

    @Inject
    HelmetService service;

    /** 检查工作台队列：?status=PENDING / IN_RECHECK / CLOSED，缺省全部 */
    @GET
    public List<?> list(@QueryParam("status") String status) {
        return service.listEvents(status);
    }

    @GET
    @Path("/{id}")
    public HelmetDetail.EventDto detail(@PathParam("id") long id) {
        return service.eventDetail(id);
    }

    /**
     * 提交检查（含复检退回后的再检）。multipart 表单：
     * inspector / shellCondition / linerCondition / basis / decision /
     * nextInspectionDate（复用时必填）/ files（附件，可多选，存本地）。
     */
    @POST
    @Path("/{id}/inspections")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response inspect(@PathParam("id") long id,
                            @RestForm("inspector") String inspector,
                            @RestForm("shellCondition") String shellCondition,
                            @RestForm("linerCondition") String linerCondition,
                            @RestForm("basis") String basis,
                            @RestForm("decision") String decision,
                            @RestForm("nextInspectionDate") String nextInspectionDate,
                            @RestForm("files") List<FileUpload> files) {
        var insp = service.inspect(id, inspector, shellCondition, linerCondition,
                basis, decision, nextInspectionDate, files);
        return Response.status(Response.Status.CREATED).entity(insp).build();
    }
}
