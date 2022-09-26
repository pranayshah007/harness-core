package io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class CompositeSLOTransformer implements SLOV2Transformer<CompositeServiceLevelObjective> {
  @Inject NotificationRuleService notificationRuleService;

  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;

  @Override
  public CompositeServiceLevelObjective getSLOV2(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, Boolean isEnabled) {
    CompositeServiceLevelObjective serviceLevelObjectiveV2 =
        CompositeServiceLevelObjective.builder()
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .identifier(serviceLevelObjectiveV2DTO.getIdentifier())
            .name(serviceLevelObjectiveV2DTO.getName())
            .desc(serviceLevelObjectiveV2DTO.getDescription())
            .tags(TagMapper.convertToList(serviceLevelObjectiveV2DTO.getTags()))
            .userJourneyIdentifiers(serviceLevelObjectiveV2DTO.getUserJourneyRefs())
            .notificationRuleRefs(notificationRuleService.getNotificationRuleRefs(projectParams,
                serviceLevelObjectiveV2DTO.getNotificationRuleRefs(), NotificationRuleType.SLO,
                Instant.ofEpochSecond(0)))
            .sloTarget(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
                           .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec()))
            .serviceLevelObjectivesDetails(
                Collections.singletonList(CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.builder()
                                              .weightagePercentage(100.00)
                                              .serviceLevelObjectiveRef(serviceLevelObjectiveV2DTO.getIdentifier())
                                              .build()))
            .sloTargetPercentage(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage())
            .serviceLevelObjectivesDetails(serviceLevelObjectiveV2DTO.getServiceLevelObjectivesDetails())
            .enabled(isEnabled)
            .build();
    return serviceLevelObjectiveV2;
  }

  @Override
  public CompositeServiceLevelObjective getSLOV2(ServiceLevelObjective serviceLevelObjective) {
    return CompositeServiceLevelObjective.builder()
        .accountId(serviceLevelObjective.getAccountId())
        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
        .uuid(serviceLevelObjective.getUuid())
        .identifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .desc(serviceLevelObjective.getDesc())
        .tags(serviceLevelObjective.getTags() == null ? Collections.emptyList() : serviceLevelObjective.getTags())
        .userJourneyIdentifiers(Collections.singletonList(serviceLevelObjective.getUserJourneyIdentifier()))
        .notificationRuleRefs(serviceLevelObjective.getNotificationRuleRefs())
        .sloTarget(serviceLevelObjective.getSloTarget())
        .enabled(serviceLevelObjective.isEnabled())
        .lastUpdatedAt(serviceLevelObjective.getLastUpdatedAt())
        .createdAt(serviceLevelObjective.getCreatedAt())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .nextNotificationIteration(serviceLevelObjective.getNextNotificationIteration())
        .serviceLevelObjectivesDetails(
            Collections.singletonList(CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.builder()
                                          .weightagePercentage(100.00)
                                          .serviceLevelObjectiveRef(serviceLevelObjective.getIdentifier())
                                          .build()))
        .serviceLevelObjectiveType(ServiceLevelObjectiveType.COMPOSITE)
        .build();
  }

  @Override
  public ServiceLevelObjectiveV2DTO getSLOV2DTO(CompositeServiceLevelObjective serviceLevelObjective) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjective.getAccountId())
                                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                      .build();
    return ServiceLevelObjectiveV2DTO.builder()
        .serviceLevelObjectiveType(ServiceLevelObjectiveType.COMPOSITE)
        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
        .identifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .description(serviceLevelObjective.getDesc())
        .serviceLevelObjectivesDetails(serviceLevelObjective.getServiceLevelObjectivesDetails())
        .notificationRuleRefs(
            notificationRuleService.getNotificationRuleRefDTOs(serviceLevelObjective.getNotificationRuleRefs()))
        .sloTarget(SLOTargetDTO.builder()
                       .type(serviceLevelObjective.getSloTarget().getType())
                       .spec(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjective.getSloTarget().getType())
                                 .getSLOTargetSpec(serviceLevelObjective.getSloTarget()))
                       .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
                       .build())
        .tags(TagMapper.convertToMap(serviceLevelObjective.getTags()))
        .userJourneyRefs(serviceLevelObjective.getUserJourneyIdentifiers())
        .build();
  }

  public ServiceLevelObjectiveV2DTO getSLOV2DTO(ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    return ServiceLevelObjectiveV2DTO.builder()
        .serviceLevelObjectiveType(ServiceLevelObjectiveType.COMPOSITE)
        .description(serviceLevelObjectiveDTO.getDescription())
        .identifier(serviceLevelObjectiveDTO.getIdentifier())
        .name(serviceLevelObjectiveDTO.getName())
        .orgIdentifier(serviceLevelObjectiveDTO.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjectiveDTO.getProjectIdentifier())
        .notificationRuleRefs(serviceLevelObjectiveDTO.getNotificationRuleRefs())
        .userJourneyRefs(Collections.singletonList(serviceLevelObjectiveDTO.getUserJourneyRef()))
        .serviceLevelObjectivesDetails(
            Collections.singletonList(CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.builder()
                                          .weightagePercentage(100.00)
                                          .serviceLevelObjectiveRef(serviceLevelObjectiveDTO.getIdentifier())
                                          .build()))
        .sloTarget(serviceLevelObjectiveDTO.getTarget())
        .tags(serviceLevelObjectiveDTO.getTags())
        .build();
  }
}
