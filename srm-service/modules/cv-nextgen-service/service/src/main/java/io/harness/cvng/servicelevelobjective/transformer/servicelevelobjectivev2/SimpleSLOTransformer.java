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
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.SLOTarget;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
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

    if (simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType() != null
        && serviceLevelObjectiveV2DTO.getTags() == null) {
      Map<String, String> tags = new HashMap<>();
      tags.put("serviceLevelIndicatorType", simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType().toString());
      serviceLevelObjectiveV2DTO.setTags(tags);
    }
    SLOTarget sloTarget = sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
                              .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec());
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
        .target(sloTarget)
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
        .sliEvaluationType(((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                               .getServiceLevelIndicators()
                               .get(0)
                               .getType())
        .startedAt(System.currentTimeMillis())
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
                       .type(serviceLevelObjective.getTarget().getType())
                       .spec(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjective.getTarget().getType())
                                 .getSLOTargetSpec(serviceLevelObjective.getTarget()))
                       .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
                       .build())
        .tags(TagMapper.convertToMap(serviceLevelObjective.getTags()))
        .userJourneyRefs(serviceLevelObjective.getUserJourneyIdentifiers())
        .build();
  }
}
