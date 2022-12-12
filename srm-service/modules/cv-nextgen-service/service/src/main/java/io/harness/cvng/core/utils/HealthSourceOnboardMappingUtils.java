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
import io.harness.cvng.core.beans.healthsource.HealthSourceQueryParams;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.SumologicLogCVConfig;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.cvng.core.entities.SumologicMetricInfo;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HealthSourceOnboardMappingUtils {
  public static DataCollectionRequest<SumoLogicConnectorDTO> getSumoLogicLogDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<SumoLogicConnectorDTO> request;
    LocalDateTime startTime = Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime())
                                  .atZone(ZoneId.systemDefault())
                                  .toLocalDateTime();
    LocalDateTime endTime =
        Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    request = SumologicLogSampleDataRequest.builder()
                  .from(startTime.format(formatter))
                  .to(endTime.format(formatter))
                  .dsl(MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL)
                  .query(healthSourceRecordsRequest.getQuery())
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
                  .query(healthSourceRecordsRequest.getQuery())
                  .type(DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA)
                  .build();
    return request;
  }

  public static CVConfig getCvConfigForSumologicMetric(QueryRecordsRequest queryRecordsRequest,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<MetricPack> metricPacks) {
    SumologicMetricCVConfig sumologicMetricCVConfig =
        SumologicMetricCVConfig.builder()
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .groupName("Default_Group")
            .monitoredServiceIdentifier("fetch_sample_data_test")
            .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
            .category(CVMonitoringCategory.PERFORMANCE)
            .build();
    String serviceInstanceField = Optional.ofNullable(queryRecordsRequest.getHealthSourceQueryParams())
                                      .map(HealthSourceQueryParams::getServiceInstanceField)
                                      .orElse(null);
    MetricResponseMapping responseMapping =
        MetricResponseMapping.builder().serviceInstanceJsonPath(serviceInstanceField).build();
    sumologicMetricCVConfig.setMetricInfos(Collections.singletonList(SumologicMetricInfo.builder()
                                                                         .query(queryRecordsRequest.getQuery())
                                                                         .identifier("sample_metric")
                                                                         .metricName("sample_metric")
                                                                         .responseMapping(responseMapping)
                                                                         .build()));
    sumologicMetricCVConfig.setMetricPack(metricPacks.get(0));
    return sumologicMetricCVConfig;
  }

  public static SumologicLogCVConfig getCVConfigForSumologicLog(QueryRecordsRequest queryRecordsRequest,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceInstanceField) {
    return SumologicLogCVConfig.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .accountId(accountIdentifier)
        .monitoredServiceIdentifier("fetch_sample_data_MS")
        .serviceInstanceIdentifier(serviceInstanceField)
        .query(queryRecordsRequest.getQuery())
        .queryName("queryName")
        .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
        .build();
  }
}
