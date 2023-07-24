/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.monitoredService.MSHealthReportService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.entities.NotificationRuleConditionEntity;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MSHealthReportServiceImpl implements MSHealthReportService {
  @Inject private Clock clock;
  @Inject ChangeEventService changeEventService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject NotificationClient notificationClient;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject
  private Map<NotificationRuleConditionType, NotificationRuleTemplateDataGenerator>
      notificationRuleConditionTypeTemplateDataGeneratorMap;

  @Override
  public MSHealthReport getMSHealthReport(
      ProjectParams projectParams, String monitoredServiceIdentifier, Instant startTime) {
    Instant currentTime = clock.instant();

    ChangeSummaryDTO changeSummary =
        changeEventService.getChangeSummary(projectParams, monitoredServiceIdentifier, null, false,
            Arrays.asList(ChangeCategory.values()), Arrays.asList(ChangeSourceType.values()), startTime, currentTime);

    List<SimpleServiceLevelObjective> serviceLevelObjectives =
        serviceLevelObjectiveService.getByMonitoredServiceIdentifiers(
            projectParams, Collections.singleton(monitoredServiceIdentifier));
    List<MSHealthReport.AssociatedSLOsDetails> associatedSLOsDetails =
        serviceLevelObjectives.stream()
            .map(serviceLevelObjective -> {
              SLODashboardWidget.SLOGraphData sloGraphData = sloHealthIndicatorService.getGraphData(projectParams,
                  serviceLevelObjective, TimeRangeParams.builder().startTime(startTime).endTime(currentTime).build());
              double errorBudgetBurned = sloGraphData.getErrorBudgetBurned();
              double currentSLOPerformance = 0;
              double pastSLOPerformance = 0;
              if (sloGraphData.getSloPerformanceTrend().size() > 0) {
                currentSLOPerformance = sloGraphData.getSloPerformanceTrend()
                                            .get(sloGraphData.getSloPerformanceTrend().size() - 1)
                                            .getValue();
                pastSLOPerformance = sloGraphData.getSloPerformanceTrend().get(0).getValue();
              }
              return MSHealthReport.AssociatedSLOsDetails.builder()
                  .identifier(serviceLevelObjective.getIdentifier())
                  .name(serviceLevelObjective.getName())
                  .scopedMonitoredServiceIdentifier(
                      ScopedInformation.getScopedInformation(serviceLevelObjective.getAccountId(),
                          serviceLevelObjective.getOrgIdentifier(), serviceLevelObjective.getProjectIdentifier(),
                          serviceLevelObjective.getMonitoredServiceIdentifier()))
                  .sloTarget(serviceLevelObjective.getSloTargetPercentage())
                  .currentSLOPerformance(currentSLOPerformance)
                  .pastSLOPerformance(pastSLOPerformance)
                  .errorBudgetBurned(errorBudgetBurned)
                  .errorBudgetRemaining(sloGraphData.getErrorBudgetRemainingPercentage())
                  .build();
            })
            .collect(Collectors.toList());

    RiskData currentHealthScore =
        monitoredServiceService
            .getCurrentAndDependentServicesScore(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                     .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                     .build())
            .getCurrentHealthScore();

    return MSHealthReport.builder()
        .changeSummary(changeSummary)
        .associatedSLOsDetails(associatedSLOsDetails)
        .currentHealthScore(currentHealthScore)
        .build();
  }

  @Override
  public void sendReportNotification(ProjectParams projectParams, Map<String, Object> entityDetails,
      NotificationRuleType type, NotificationRuleConditionEntity condition,
      NotificationRule.CVNGNotificationChannel notificationChannel, String monitoredServiceIdentifier) {
    final NotificationRuleTemplateDataGenerator notificationRuleTemplateDataGenerator =
        notificationRuleConditionTypeTemplateDataGeneratorMap.get(condition.getType());
    Map<String, String> templateDataMap =
        notificationRuleTemplateDataGenerator.getTemplateData(projectParams, entityDetails, condition, new HashMap<>());
    String templateId = notificationRuleTemplateDataGenerator.getTemplateId(type, notificationChannel.getType());
    try {
      NotificationResult notificationResult = notificationClient.sendNotificationAsync(
          notificationChannel.toNotificationChannel(projectParams.getAccountIdentifier(),
              projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), templateId, templateDataMap));
      log.info("Report Notification with notification id {} for {} sent", notificationResult.getNotificationId(), type);
    } catch (Exception ex) {
      log.error("Unable to send notification because of following exception.", ex);
    }
  }
}
