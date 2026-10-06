package com.factory.helmet.api.view;

import java.util.List;

/** 安全帽完整档案：主档 + 领用历史 + 撞击记录（含当时使用人）+ 检查记录 */
public record HelmetDetailView(HelmetView helmet,
                               List<CheckoutView> checkouts,
                               List<ImpactView> impacts,
                               List<InspectionView> inspections) {
}
