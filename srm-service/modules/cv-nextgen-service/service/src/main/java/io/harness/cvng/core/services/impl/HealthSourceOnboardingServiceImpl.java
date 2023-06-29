/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValue;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesResponse;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsResponse;
import io.harness.cvng.core.beans.healthsource.LogRecord;
import io.harness.cvng.core.beans.healthsource.LogRecordsResponse;
import io.harness.cvng.core.beans.healthsource.MetricRecordsResponse;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.healthsource.TimeSeries;
import io.harness.cvng.core.beans.healthsource.TimeSeriesDataPoint;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;
import io.harness.cvng.models.VerificationType;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.ng.core.CorrelationContext;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class HealthSourceOnboardingServiceImpl implements HealthSourceOnboardingService {
  private static final String ONBOARDING_PREFIX = "onboarding_";
  private static final String SAMPLE_METRIC_INDENTIFIER = ONBOARDING_PREFIX + "sample_metric";
  private static final String DEFAULT_GROUP = ONBOARDING_PREFIX + "default_group";
  private static final String MONITORED_SERVICE_IDENTIFIER = ONBOARDING_PREFIX + "monitored_service";
  private static final String QUERY_NAME = ONBOARDING_PREFIX + "query_name";
  public static final String SAMPLE_TIMESERIES = "sampleTimeseries";
  public static final int TIME_SERIES_LIMIT_FOR_CHART = 50;
  @Inject private OnboardingService onboardingService;

  @Inject private MetricPackService metricPackService;

  @Inject private Map<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Inject private Map<DataSourceType, NextGenHealthSourceHelper> dataSourceTypeNextGenHelperMapBinder;

  @Override
  public HealthSourceRecordsResponse fetchSampleRawRecordsForHealthSource(
      HealthSourceRecordsRequest healthSourceRecordsRequest, ProjectParams projectParams) {
    healthSourceRecordsRequest.validate();
    DataSourceType dataSourceType = getDataSourceType(healthSourceRecordsRequest);
    NextGenHealthSourceHelper nextGenHealthSourceHelper = dataSourceTypeNextGenHelperMapBinder.get(dataSourceType);
    DataCollectionRequest<?> request = nextGenHealthSourceHelper.getDataCollectionRequest(healthSourceRecordsRequest);
    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(healthSourceRecordsRequest.getConnectorIdentifier())
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .tracingId(CorrelationContext.getCorrelationId())
            .build();
    OnboardingResponseDTO onboardingResponseDTO =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    Object result = onboardingResponseDTO.getResult();
    HealthSourceRecordsResponse healthSourceRecordsResponse =
        HealthSourceRecordsResponse.builder().providerType(dataSourceType).build();
    if (!(result instanceof Collection)) {
      healthSourceRecordsResponse.getRawRecords().add(result);
    } else if (((Collection<?>) result).size() > 0) {
      healthSourceRecordsResponse.getRawRecords().addAll((Collection<?>) result);
    }
    return healthSourceRecordsResponse;
  }

  private static DataSourceType getDataSourceType(HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataSourceType dataSourceType =
        MonitoredServiceDataSourceType.getDataSourceType(healthSourceRecordsRequest.getHealthSourceType());
    if (dataSourceType == null) {
      dataSourceType = healthSourceRecordsRequest.getProviderType();
    }
    return dataSourceType;
  }

  @Override
  public MetricRecordsResponse fetchMetricData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    queryRecordsRequest.validate();
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    DataCollectionInfo<ConnectorConfigDTO> dataCollectionInfo =
        getDataCollectionInfoForMetric(queryRecordsRequest, projectParams);
    DataCollectionRequest<ConnectorConfigDTO> request =
        SyncDataCollectionRequest.builder()
            .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
            .dataCollectionInfo(dataCollectionInfo)
            .endTime(Instant.ofEpochMilli(queryRecordsRequest.getEndTime()))
            .startTime(Instant.ofEpochMilli(queryRecordsRequest.getStartTime()))
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(CorrelationContext.getCorrelationId())
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();
    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountIdentifier, onboardingRequestDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        JsonUtils.asList(JsonUtils.asJson(response.getResult()), new TypeReference<>() {});
    Map<String, List<TimeSeriesRecord>> timeSeriesRecordHostsMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(timeSeriesRecord
            -> StringUtils.isEmpty(timeSeriesRecord.getHostname()) ? SAMPLE_TIMESERIES
                                                                   : timeSeriesRecord.getHostname()));
    List<TimeSeries> timeSeriesList = new ArrayList<>();
    for (Map.Entry<String, List<TimeSeriesRecord>> timeSeriesRecordForHost : timeSeriesRecordHostsMap.entrySet()) {
      List<TimeSeriesDataPoint> timeSeriesDataPoints = timeSeriesRecordForHost.getValue()
                                                           .stream()
                                                           .map(timeSeriesRecord
                                                               -> TimeSeriesDataPoint.builder()
                                                                      .timestamp(timeSeriesRecord.getTimestamp())
                                                                      .value(timeSeriesRecord.getMetricValue())
                                                                      .build())
                                                           .collect(Collectors.toList());
      String timeSeriesName = timeSeriesRecordForHost.getKey();
      TimeSeries timeSeriesForHost =
          TimeSeries.builder().timeseriesName(timeSeriesName).data(timeSeriesDataPoints).build();
      timeSeriesList.add(timeSeriesForHost);
      if (timeSeriesList.size() >= TIME_SERIES_LIMIT_FOR_CHART) {
        break;
      }
    }
    return MetricRecordsResponse.builder()
        .serviceInstances(new ArrayList<>(timeSeriesRecordHostsMap.keySet()))
        .timeSeriesData(timeSeriesList)
        .build();
  }

  private DataCollectionInfo<ConnectorConfigDTO> getDataCollectionInfoForMetric(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    CVConfig cvConfig;
    DataSourceType providerType = getDataSourceType(queryRecordsRequest);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountIdentifier, orgIdentifier, projectIdentifier, providerType);
    metricPackService.populateDataCollectionDsl(providerType, metricPacks.get(0));
    if (providerType.isNextGenSpec() && providerType.getVerificationType() == VerificationType.TIME_SERIES) {
      cvConfig = NextGenMetricCVConfig.builder()
                     .accountId(accountIdentifier)
                     .orgIdentifier(orgIdentifier)
                     .projectIdentifier(projectIdentifier)
                     .dataSourceType(providerType)
                     .groupName(DEFAULT_GROUP)
                     .monitoredServiceIdentifier(MONITORED_SERVICE_IDENTIFIER)
                     .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                     .category(CVMonitoringCategory.PERFORMANCE)
                     .metricInfos(Collections.singletonList(
                         NextGenMetricInfo.builder()
                             .query(queryRecordsRequest.getQuery().trim())
                             .identifier(SAMPLE_METRIC_INDENTIFIER)
                             .metricName(SAMPLE_METRIC_INDENTIFIER)
                             .queryParams(queryRecordsRequest.getHealthSourceQueryParams().getQueryParamsEntity())
                             .build()))
                     .metricPack(metricPacks.get(0))
                     .healthSourceParams(queryRecordsRequest.getHealthSourceParams().getHealthSourceParamsEntity())
                     .build();
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for health source provider.");
    }

    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(providerType);
    DataCollectionInfo<ConnectorConfigDTO> dataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.SLI);
    dataCollectionInfo.setCollectHostData(
        StringUtils.isNotEmpty(queryRecordsRequest.getHealthSourceQueryParams().getServiceInstanceField()));
    return dataCollectionInfo;
  }

  @Override
  public LogRecordsResponse fetchLogData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    queryRecordsRequest.validate();
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    DataCollectionInfo<ConnectorConfigDTO> dataCollectionInfo =
        getDataCollectionInfoForLog(queryRecordsRequest, projectParams);
    dataCollectionInfo.setCollectHostData(false);
    DataCollectionRequest<ConnectorConfigDTO> request =
        SyncDataCollectionRequest.builder()
            .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
            .dataCollectionInfo(dataCollectionInfo)
            .endTime(Instant.ofEpochMilli(queryRecordsRequest.getEndTime()))
            .startTime(Instant.ofEpochMilli(queryRecordsRequest.getStartTime()))
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .tracingId(CorrelationContext.getCorrelationId())
                                                    .build();
    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountIdentifier, onboardingRequestDTO);
    List<LogDataRecord> logDataRecords =
        JsonUtils.asList(JsonUtils.asJson(response.getResult()), new TypeReference<>() {});
    List<LogRecord> logRecords = new ArrayList<>();
    logDataRecords.forEach(logDataRecord
        -> logRecords.add(LogRecord.builder()
                              .timestamp(logDataRecord.getTimestamp())
                              .message(logDataRecord.getLog())
                              .serviceInstance(logDataRecord.getHostname())
                              .build()));
    return LogRecordsResponse.builder().logRecords(logRecords).build();
  }

  @Override
  public HealthSourceParamValuesResponse fetchHealthSourceParamValues(
      HealthSourceParamValuesRequest healthSourceParamValuesRequest, ProjectParams projectParams) {
    healthSourceParamValuesRequest.validate();
    DataSourceType dataSourceType =
        MonitoredServiceDataSourceType.getDataSourceType(healthSourceParamValuesRequest.getProviderType());
    NextGenHealthSourceHelper nextGenHealthSourceHelper = dataSourceTypeNextGenHelperMapBinder.get(dataSourceType);
    List<HealthSourceParamValue> healthSourceParamValues =
        nextGenHealthSourceHelper.fetchHealthSourceParamValues(healthSourceParamValuesRequest, projectParams);
    return HealthSourceParamValuesResponse.builder()
        .paramName(healthSourceParamValuesRequest.getParamName())
        .paramValues(healthSourceParamValues)
        .build();
  }
  private DataCollectionInfo<ConnectorConfigDTO> getDataCollectionInfoForLog(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    CVConfig cvConfig;
    DataSourceType providerType = getDataSourceType(queryRecordsRequest);
    if (providerType.isNextGenSpec() && providerType.getVerificationType() == VerificationType.LOG) {
      cvConfig = NextGenLogCVConfig.builder()
                     .orgIdentifier(projectParams.getOrgIdentifier())
                     .projectIdentifier(projectParams.getProjectIdentifier())
                     .dataSourceType(providerType)
                     .accountId(projectParams.getAccountIdentifier())
                     .monitoredServiceIdentifier(MONITORED_SERVICE_IDENTIFIER)
                     .queryParams(queryRecordsRequest.getHealthSourceQueryParams().getQueryParamsEntity())
                     .healthSourceParams(queryRecordsRequest.getHealthSourceParams().getHealthSourceParamsEntity())
                     .query(queryRecordsRequest.getQuery().trim())
                     .queryName(QUERY_NAME)
                     .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                     .build();
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for health source provider.");
    }
    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(providerType);
    return dataCollectionInfoMapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.DEPLOYMENT);
  }
}