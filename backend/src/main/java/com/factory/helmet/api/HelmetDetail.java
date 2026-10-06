package com.factory.helmet.api;

import com.factory.helmet.model.Attachment;
import com.factory.helmet.model.Helmet;
import com.factory.helmet.model.ImpactEvent;
import com.factory.helmet.model.Inspection;
import com.factory.helmet.model.IssueRecord;

import java.util.List;

/** 安全帽详情：基本信息 + 领用历史 + 撞击事件（含各轮检查与附件） */
public record HelmetDetail(
        Helmet helmet,
        String currentHolder,
        List<IssueRecord> issues,
        List<EventDto> events) {

    public record EventDto(ImpactEvent event, List<InspectionDto> inspections) {
    }

    public record InspectionDto(Inspection inspection, List<Attachment> attachments) {
    }
}
