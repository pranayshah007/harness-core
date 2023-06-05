/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.MSHealthReport;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.monitoredService.MSHealthReportService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.utils.MathUtils;
import io.harness.cvng.utils.ScopedInformation;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MSHealthReportServiceImpl implements MSHealthReportService {
  final int PAST_HEALTH_SCORE_INDEX = 12;
  @Inject private Clock clock;
  @Inject ChangeEventService changeEventService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;

  @Override
  public MSHealthReport getMSHealthReport(ProjectParams projectParams, String monitoredServiceIdentifier) {
    Instant currentTime = clock.instant();

    ChangeSummaryDTO changeSummary = changeEventService.getChangeSummary(projectParams, monitoredServiceIdentifier,
        null, false, Arrays.asList(ChangeCategory.values()), Arrays.asList(ChangeSourceType.values()),
        currentTime.minus(Duration.ofHours(1)), currentTime);

    List<SimpleServiceLevelObjective> serviceLevelObjectives =
        serviceLevelObjectiveService.getByMonitoredServiceIdentifiers(
            projectParams, Collections.singleton(monitoredServiceIdentifier));
    List<MSHealthReport.AssociatedSLOsDetails> associatedSLOsDetails =
        serviceLevelObjectives.stream()
            .map(serviceLevelObjective -> {
              SLODashboardWidget.SLOGraphData sloGraphData =
                  sloHealthIndicatorService.getGraphData(projectParams, serviceLevelObjective,
                      TimeRangeParams.builder()
                          .startTime(clock.instant().minus(Duration.ofHours(1)))
                          .endTime(clock.instant())
                          .build());
              double errorBudgetBurnRate = 0;
              double sloPerformance = 0;
              if (sloGraphData.getErrorBudgetBurndown().size() > 1) {
                errorBudgetBurnRate = sloGraphData.getErrorBudgetBurndown().get(0).getValue()
                    - sloGraphData.getErrorBudgetBurndown()
                          .get(sloGraphData.getErrorBudgetBurndown().size() - 1)
                          .getValue();
              }
              if (sloGraphData.getSloPerformanceTrend().size() > 0) {
                sloPerformance = sloGraphData.getSloPerformanceTrend()
                                     .get(sloGraphData.getSloPerformanceTrend().size() - 1)
                                     .getValue();
              }
              return MSHealthReport.AssociatedSLOsDetails.builder()
                  .identifier(serviceLevelObjective.getIdentifier())
                  .name(serviceLevelObjective.getName())
                  .scopedMonitoredServiceIdentifier(
                      ScopedInformation.getScopedInformation(serviceLevelObjective.getAccountId(),
                          serviceLevelObjective.getOrgIdentifier(), serviceLevelObjective.getProjectIdentifier(),
                          serviceLevelObjective.getMonitoredServiceIdentifier()))
                  .sloPerformance(sloPerformance)
                  .errorBudgetBurnRate(errorBudgetBurnRate)
                  .build();
            })
            .collect(Collectors.toList());

    long currentHealthScore = 0;
    long pastHealthScore = 0;
    List<RiskData> overAllHealthScore =
        monitoredServiceService
            .getOverAllHealthScore(projectParams, monitoredServiceIdentifier, DurationDTO.FOUR_HOURS, currentTime)
            .getHealthScores();
    if (overAllHealthScore.size() > 0) {
      Collections.reverse(overAllHealthScore);
      pastHealthScore = overAllHealthScore.get(PAST_HEALTH_SCORE_INDEX).getRiskStatus() != Risk.NO_DATA
          ? overAllHealthScore.get(PAST_HEALTH_SCORE_INDEX).getHealthScore()
          : 0;
      for (int i = 0; i < PAST_HEALTH_SCORE_INDEX; i++) {
        if (overAllHealthScore.get(i).getRiskStatus() != Risk.NO_DATA) {
          currentHealthScore = overAllHealthScore.get(i).getHealthScore();
          break;
        }
      }
    }

    return MSHealthReport.builder()
        .changeSummary(changeSummary)
        .associatedSLOsDetails(associatedSLOsDetails)
        .serviceHealthDetails(MSHealthReport.ServiceHealthDetails.builder()
                                  .currentHealthScore(currentHealthScore)
                                  .pastHealthScore(pastHealthScore)
                                  .percentageChange(MathUtils.getPercentageChange(currentHealthScore, pastHealthScore))
                                  .build())
        .build();
  }
}
