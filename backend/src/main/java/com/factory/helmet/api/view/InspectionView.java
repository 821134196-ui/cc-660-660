package com.factory.helmet.api.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.factory.helmet.domain.Inspection;
import com.factory.helmet.domain.InspectionResult;
import com.factory.helmet.domain.LinerCondition;
import com.factory.helmet.domain.ShellCondition;

public record InspectionView(Long id,
                             Long impactEventId,
                             ShellCondition shellCondition,
                             String shellLabel,
                             LinerCondition linerCondition,
                             String linerLabel,
                             boolean serviceLifeExpired,
                             String basis,
                             InspectionResult result,
                             String resultLabel,
                             LocalDate nextInspectionDate,
                             String inspectorName,
                             LocalDateTime inspectedAt,
                             List<AttachmentView> attachments) {

    public static InspectionView of(Inspection i, List<AttachmentView> attachments) {
        return new InspectionView(i.getId(),
                i.getImpactEvent() == null ? null : i.getImpactEvent().getId(),
                i.getShellCondition(), i.getShellCondition().getLabel(),
                i.getLinerCondition(), i.getLinerCondition().getLabel(),
                i.isServiceLifeExpired(), i.getBasis(),
                i.getResult(), i.getResult().getLabel(),
                i.getNextInspectionDate(), i.getInspectorName(),
                i.getInspectedAt(), attachments);
    }
}
