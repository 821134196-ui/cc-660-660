package com.factory.helmet.api;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.factory.helmet.api.dto.CheckoutRequest;
import com.factory.helmet.api.dto.HelmetRequest;
import com.factory.helmet.api.dto.HelmetUpdate;
import com.factory.helmet.api.dto.ImpactRequest;
import com.factory.helmet.api.dto.InspectionRequest;
import com.factory.helmet.api.dto.ReturnRequest;
import com.factory.helmet.api.view.CheckoutView;
import com.factory.helmet.api.view.HelmetDetailView;
import com.factory.helmet.api.view.HelmetView;
import com.factory.helmet.api.view.ImpactView;
import com.factory.helmet.api.view.InspectionView;
import com.factory.helmet.domain.Helmet;
import com.factory.helmet.service.HelmetService;

@Path("/api/helmets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HelmetResource {

    @Inject
    HelmetService service;

    /** 库存列表，可按状态过滤；扫码页也直接用此接口查询编号 */
    @GET
    @Transactional
    public List<HelmetView> list(@QueryParam("status") String status,
                                 @QueryParam("code") String code) {
        if (code != null && !code.isBlank()) {
            return List.of(service.getHelmetView(service.findHelmetByCode(code.trim())));
        }
        return service.listHelmets().stream()
                .filter(h -> status == null || status.isBlank()
                        || h.getStatus().name().equalsIgnoreCase(status.trim()))
                .map(service::getHelmetView)
                .toList();
    }

    /** 登记新安全帽（退役旧编号再次登记会被 409 拦截） */
    @POST
    public HelmetView register(HelmetRequest req) {
        Helmet h = service.register(req);
        return service.getHelmetView(h);
    }

    /** 完整档案：领用历史、撞击事故（含当时使用人）、检查记录 */
    @GET
    @Path("/{code}")
    public HelmetDetailView detail(@PathParam("code") String code) {
        return service.getDetail(code);
    }

    /**
     * 修改库存资料（批次/生产日期）。
     * 接口不接受 status 字段，夹带即 400；退役无法借此撤销。
     */
    @PUT
    @Path("/{code}")
    public HelmetView update(@PathParam("code") String code, HelmetUpdate req) {
        return service.getHelmetView(service.updateInventory(code, req));
    }

    /** 受冲击上报：立即冻结 */
    @POST
    @Path("/{code}/impacts")
    public ImpactView reportImpact(@PathParam("code") String code, ImpactRequest req) {
        return ImpactView.of(service.reportImpact(code, req));
    }

    /** 仓库领出：冻结/复检中/退役一律 409 */
    @POST
    @Path("/{code}/checkout")
    public CheckoutView checkout(@PathParam("code") String code, CheckoutRequest req) {
        return CheckoutView.of(service.checkout(code, req));
    }

    @POST
    @Path("/{code}/return")
    public CheckoutView returnHelmet(@PathParam("code") String code, ReturnRequest req) {
        return CheckoutView.of(service.returnHelmet(code, req == null ? null : req.note()));
    }

    /**
     * 安全员检查：外壳 + 内衬 + 使用年限 + 依据 + 结论（复用/复检/退役）。
     * 复检退回后帽子保持冻结，须再次检查直到复用或退役。
     */
    @POST
    @Path("/{code}/inspections")
    public InspectionView inspect(@PathParam("code") String code, InspectionRequest req) {
        return service.inspect(code, req);
    }
}
