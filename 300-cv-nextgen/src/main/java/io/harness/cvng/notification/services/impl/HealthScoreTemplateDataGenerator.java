/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationAsStringWithoutSuffix;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;

import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.TimeSeriesAnalysisFilter;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class HealthScoreTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceHealthScoreCondition> {
  @Inject private TimeSeriesDashboardService timeSeriesDashboardService;

  @Override
  public String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "health score drops below " + notificationDataMap.get(CURRENT_HEALTH_SCORE) + " for";
  }

  @Override
  public String getTriggerMessage(MonitoredServiceHealthScoreCondition condition) {
    String durationAsString = getDurationAsStringWithoutSuffix(condition.getPeriod());
    return "When service health score drops below " + condition.getThreshold().intValue() + " for longer than "
        + durationAsString + " minutes";
  }

  @Override
  public String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, MonitoredServiceHealthScoreCondition condition) {
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builderWithProjectParams(projectParams).monitoredServiceIdentifier(identifier).build();
    TimeRangeParams timeRangeParams = TimeRangeParams.builder()
                                          .startTime(Instant.ofEpochMilli(startTime * 1000 - condition.getPeriod()))
                                          .endTime(Instant.ofEpochMilli(startTime * 1000))
                                          .build();
    TimeSeriesAnalysisFilter filter = TimeSeriesAnalysisFilter.builder().anomalousMetricsOnly(true).build();
    List<TimeSeriesMetricDataDTO> timeSeriesMetricDataDTOS =
        timeSeriesDashboardService
            .getTimeSeriesMetricData(monitoredServiceParams, timeRangeParams, filter, PageParams.builder().build())
            .getContent();

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < 5; i++) {
      if (i >= timeSeriesMetricDataDTOS.size()) {
        break;
      }
      TimeSeriesMetricDataDTO timeSeriesMetricDataDTO = timeSeriesMetricDataDTOS.get(i);
      sb.append("Metric " + timeSeriesMetricDataDTO.getMetricName() + "\n");
      sb.append("Group " + timeSeriesMetricDataDTO.getGroupName() + "\n");
    }

    return sb.toString();
  }
}
