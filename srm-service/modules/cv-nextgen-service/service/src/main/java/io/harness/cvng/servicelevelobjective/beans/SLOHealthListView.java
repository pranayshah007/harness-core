/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.DowntimeStatusDetails;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.ng.core.mapper.TagMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLOHealthListView {
  @NotNull String sloIdentifier;
  @NotNull String name;
  String orgName;
  String projectName;
  String monitoredServiceIdentifier;
  String monitoredServiceName;
  String healthSourceIdentifier;
  String healthSourceName;
  String serviceIdentifier;
  String serviceName;
  String environmentIdentifier;
  String environmentName;
  Map<String, String> tags;
  String description;
  String userJourneyName;
  @NotNull List<UserJourneyDTO> userJourneys;
  @NotNull double burnRate;
  @NotNull double errorBudgetRemainingPercentage;
  @NotNull int errorBudgetRemaining;
  @NotNull int totalErrorBudget;
  @NotNull SLOTargetType sloTargetType;
  ServiceLevelIndicatorType sliType;
  @JsonIgnore String sliIdentifier;
  @NotNull ServiceLevelObjectiveType sloType;
  @NotNull double sloTargetPercentage;
  @NotNull int noOfActiveAlerts;
  @NotNull SLIEvaluationType evaluationType;
  DowntimeStatusDetails downtimeStatusDetails;
  @NotNull ProjectParams projectParams;

  @NotNull boolean failedState;
  @NotNull
  public ErrorBudgetRisk getErrorBudgetRisk() {
    return ErrorBudgetRisk.getFromPercentage(errorBudgetRemainingPercentage);
  }

  public static SLOHealthListViewBuilder getSLOHealthListViewBuilder(
      AbstractServiceLevelObjective serviceLevelObjective, List<UserJourneyDTO> userJourneys,
      int totalErrorBudgetMinutes, SLOHealthIndicator sloHealthIndicator,
      Map<AbstractServiceLevelObjective, SLIEvaluationType> serviceLevelObjectiveSLIEvaluationTypeMap) {
    return SLOHealthListView.builder()
        .sloIdentifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .sloTargetType(serviceLevelObjective.getTarget().getType())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .userJourneys(userJourneys)
        .userJourneyName(userJourneys.get(0).getName())
        .tags(TagMapper.convertToMap(serviceLevelObjective.getTags()))
        .description(serviceLevelObjective.getDesc())
        .totalErrorBudget(totalErrorBudgetMinutes)
        .errorBudgetRemaining(sloHealthIndicator.getErrorBudgetRemainingMinutes())
        .errorBudgetRemainingPercentage(sloHealthIndicator.getErrorBudgetRemainingPercentage())
        .burnRate(sloHealthIndicator.getErrorBudgetBurnRate())
        .noOfActiveAlerts(serviceLevelObjective.getNotificationRuleRefs().size())
        .sloType(serviceLevelObjective.getType())
        .evaluationType(serviceLevelObjectiveSLIEvaluationTypeMap.get(serviceLevelObjective))
        .projectParams(ProjectParams.builder()
                           .accountIdentifier(serviceLevelObjective.getAccountId())
                           .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                           .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                           .build())
        .failedState(sloHealthIndicator.getFailedState() != null && sloHealthIndicator.getFailedState());
  }
}
