/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HealthSourceOnboardMappingUtils {
  private static final String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";

  public static DataCollectionRequest<SumoLogicConnectorDTO> getSumoLogicLogDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<SumoLogicConnectorDTO> request;
    LocalDateTime startTime = Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime())
                                  .atZone(ZoneId.systemDefault())
                                  .toLocalDateTime();
    LocalDateTime endTime =
        Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_STRING);

    request = SumologicLogSampleDataRequest.builder()
                  .from(startTime.format(formatter))
                  .to(endTime.format(formatter))
                  .dsl(MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL)
                  .query(healthSourceRecordsRequest.getQuery().trim())
                  .type(DataCollectionRequestType.SUMOLOGIC_LOG_SAMPLE_DATA)
                  .build();
    return request;
  }

  public static DataCollectionRequest<SumoLogicConnectorDTO> getSumologicMetricDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<SumoLogicConnectorDTO> request;
    request = SumologicMetricSampleDataRequest.builder()
                  .from(healthSourceRecordsRequest.getStartTime())
                  .to(healthSourceRecordsRequest.getEndTime())
                  .dsl(MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL)
                  .query(healthSourceRecordsRequest.getQuery().trim())
                  .type(DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA)
                  .build();
    return request;
  }

  public static CVConfig getCvConfigForNextGenMetric(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams, List<MetricPack> metricPacks) {
    NextGenMetricCVConfig nextGenMetricCVConfig = NextGenMetricCVConfig.builder()
                                                      .accountId(projectParams.getAccountIdentifier())
                                                      .orgIdentifier(projectParams.getOrgIdentifier())
                                                      .projectIdentifier(projectParams.getProjectIdentifier())
                                                      .dataSourceType(queryRecordsRequest.getProviderType())
                                                      .groupName("Default_Group")
                                                      .monitoredServiceIdentifier("fetch_sample_data_test")
                                                      .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                                                      .category(CVMonitoringCategory.PERFORMANCE)
                                                      .build();
    nextGenMetricCVConfig.setMetricInfos(Collections.singletonList(
        NextGenMetricInfo.builder()
            .query(queryRecordsRequest.getQuery().trim())
            .identifier("sample_metric")
            .metricName("sample_metric")
            .queryParams(queryRecordsRequest.getHealthSourceQueryParams().getQueryParamsEntity())
            .build()));
    nextGenMetricCVConfig.setMetricPack(metricPacks.get(0));
    return nextGenMetricCVConfig;
  }

  public static NextGenLogCVConfig getCVConfigForNextGenLog(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    return NextGenLogCVConfig.builder()
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .dataSourceType(queryRecordsRequest.getProviderType())
        .accountId(projectParams.getAccountIdentifier())
        .monitoredServiceIdentifier("fetch_sample_data_MS")
        .queryParams(queryRecordsRequest.getHealthSourceQueryParams().getQueryParamsEntity())
        .query(queryRecordsRequest.getQuery().trim())
        .queryName("queryName")
        .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
        .build();
  }
}
