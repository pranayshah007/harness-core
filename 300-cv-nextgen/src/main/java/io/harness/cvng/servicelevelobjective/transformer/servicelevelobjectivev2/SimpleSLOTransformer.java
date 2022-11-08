/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleSLOTransformer implements SLOV2Transformer<SimpleServiceLevelObjective> {
  @Inject NotificationRuleService notificationRuleService;

  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;

  @Override
  public SimpleServiceLevelObjective getSLOV2(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, Boolean isEnabled) {
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec();
    return SimpleServiceLevelObjective.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelObjectiveV2DTO.getIdentifier())
        .name(serviceLevelObjectiveV2DTO.getName())
        .desc(serviceLevelObjectiveV2DTO.getDescription())
        .tags(TagMapper.convertToList(serviceLevelObjectiveV2DTO.getTags()))
        .userJourneyIdentifiers(serviceLevelObjectiveV2DTO.getUserJourneyRefs())
        .notificationRuleRefs(notificationRuleService.getNotificationRuleRefs(projectParams,
            serviceLevelObjectiveV2DTO.getNotificationRuleRefs(), NotificationRuleType.SLO, Instant.ofEpochSecond(0)))
        .sloTarget(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
                       .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec()))
        .sloTargetPercentage(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage())
        .enabled(isEnabled)
        .serviceLevelIndicatorType(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType())
        .serviceLevelIndicators(simpleServiceLevelObjectiveSpec.getServiceLevelIndicators()
                                    .stream()
                                    .map(ServiceLevelIndicatorDTO::getIdentifier)
                                    .collect(Collectors.toList()))
        .monitoredServiceIdentifier(simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
        .healthSourceIdentifier(simpleServiceLevelObjectiveSpec.getHealthSourceRef())
        .type(ServiceLevelObjectiveType.SIMPLE)
        .startedAt(System.currentTimeMillis())
        .build();
  }

  @Override
  public SimpleServiceLevelObjective getSLOV2(ServiceLevelObjective serviceLevelObjective) {
    return SimpleServiceLevelObjective.builder()
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
        .startedAt(serviceLevelObjective.getCreatedAt())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .nextNotificationIteration(serviceLevelObjective.getNextNotificationIteration())
        .serviceLevelIndicatorType(serviceLevelObjective.getType())
        .healthSourceIdentifier(serviceLevelObjective.getHealthSourceIdentifier())
        .monitoredServiceIdentifier(serviceLevelObjective.getMonitoredServiceIdentifier())
        .serviceLevelIndicators(serviceLevelObjective.getServiceLevelIndicators())
        .type(ServiceLevelObjectiveType.SIMPLE)
        .build();
  }

  @Override
  public ServiceLevelObjectiveV2DTO getSLOV2DTO(SimpleServiceLevelObjective serviceLevelObjective) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjective.getAccountId())
                                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                      .build();
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.SIMPLE)
        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
        .identifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .description(serviceLevelObjective.getDesc())
        .spec(SimpleServiceLevelObjectiveSpec.builder()
                  .monitoredServiceRef(serviceLevelObjective.getMonitoredServiceIdentifier())
                  .healthSourceRef(serviceLevelObjective.getHealthSourceIdentifier())
                  .serviceLevelIndicatorType(serviceLevelObjective.getServiceLevelIndicatorType())
                  .serviceLevelIndicators(serviceLevelIndicatorService.get(
                      projectParams, serviceLevelObjective.getServiceLevelIndicators()))
                  .build())
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
        .type(ServiceLevelObjectiveType.SIMPLE)
        .description(serviceLevelObjectiveDTO.getDescription())
        .identifier(serviceLevelObjectiveDTO.getIdentifier())
        .name(serviceLevelObjectiveDTO.getName())
        .orgIdentifier(serviceLevelObjectiveDTO.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjectiveDTO.getProjectIdentifier())
        .spec(SimpleServiceLevelObjectiveSpec.builder()
                  .healthSourceRef(serviceLevelObjectiveDTO.getHealthSourceRef())
                  .monitoredServiceRef(serviceLevelObjectiveDTO.getMonitoredServiceRef())
                  .serviceLevelIndicators(serviceLevelObjectiveDTO.getServiceLevelIndicators())
                  .serviceLevelIndicatorType(serviceLevelObjectiveDTO.getType())
                  .build())
        .notificationRuleRefs(serviceLevelObjectiveDTO.getNotificationRuleRefs())
        .userJourneyRefs(Collections.singletonList(serviceLevelObjectiveDTO.getUserJourneyRef()))
        .sloTarget(serviceLevelObjectiveDTO.getTarget())
        .tags(serviceLevelObjectiveDTO.getTags())
        .build();
  }
}
