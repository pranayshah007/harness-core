/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationAsStringWithoutSuffix;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANOMALOUS_METRICS_PAGE_NUMBER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.N_TOP_MOST_ANOMALOUS_METRICS;

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
import java.time.temporal.ChronoUnit;
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
    Instant roundOffStartInstant =
        roundDownTo5MinBoundary(Instant.ofEpochMilli(startTime)).minus(condition.getPeriod(), ChronoUnit.MILLIS);
    TimeRangeParams timeRangeParams =
        TimeRangeParams.builder().startTime(roundOffStartInstant).endTime(Instant.ofEpochMilli(startTime)).build();
    TimeSeriesAnalysisFilter filter = TimeSeriesAnalysisFilter.builder().anomalousMetricsOnly(true).build();
    PageParams pageParams =
        PageParams.builder().page(ANOMALOUS_METRICS_PAGE_NUMBER).size(N_TOP_MOST_ANOMALOUS_METRICS).build();
    List<TimeSeriesMetricDataDTO> timeSeriesMetricDataDTOS =
        timeSeriesDashboardService.getTimeSeriesMetricData(monitoredServiceParams, timeRangeParams, filter, pageParams)
            .getContent();

    StringBuilder sb = new StringBuilder(256);

    if (timeSeriesMetricDataDTOS.size() == 0) {
      sb.append(NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE);
    } else {
      timeSeriesMetricDataDTOS.forEach(dto -> {
        sb.append("Metric " + dto.getMetricName() + "\n"
            + "Group " + dto.getGroupName() + "\n");
      });
    }

    return sb.toString();
  }
}
