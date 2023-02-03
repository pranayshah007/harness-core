/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.beans.elk.ELKIndexCollectionRequest;
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
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.healthsource.TimeSeries;
import io.harness.cvng.core.beans.healthsource.TimeSeriesDataPoint;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.utils.HealthSourceOnboardMappingUtils;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.health.HealthService;
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
import java.util.Optional;
import java.util.stream.Collectors;

public class HealthSourceOnboardingServiceImpl implements HealthSourceOnboardingService {
  @Inject private OnboardingService onboardingService;

  @Inject private MetricPackService metricPackService;

  @Inject private Map<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Inject private HealthService healthService;

  @Override
  public HealthSourceRecordsResponse fetchSampleRawRecordsForHealthSource(
      HealthSourceRecordsRequest healthSourceRecordsRequest, ProjectParams projectParams) {
    DataCollectionRequest<?> request =
        HealthSourceOnboardMappingUtils.getDataCollectionRequest(healthSourceRecordsRequest);

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
        HealthSourceRecordsResponse.builder().providerType(healthSourceRecordsRequest.getProviderType()).build();
    if (!(result instanceof Collection)) {
      healthSourceRecordsResponse.getRawRecords().add(result);
    } else if (((Collection<?>) result).size() > 0) {
      healthSourceRecordsResponse.getRawRecords().addAll((Collection<?>) result);
    }
    return healthSourceRecordsResponse;
  }

  @Override
  public MetricRecordsResponse fetchMetricData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
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
    TimeSeries timeSeries =
        TimeSeries.builder().timeseriesName("sampleTimeseries").build(); // TODO Understand multiple Timeseries
    // map and collect
    timeSeriesRecords.forEach(timeSeriesRecord
        -> timeSeries.getData().add(TimeSeriesDataPoint.builder()
                                        .timestamp(timeSeriesRecord.getTimestamp())
                                        .value(timeSeriesRecord.getMetricValue())
                                        .build()));
    return MetricRecordsResponse.builder().timeSeriesData(Collections.singletonList(timeSeries)).build();
  }

  private DataCollectionInfo<ConnectorConfigDTO> getDataCollectionInfoForMetric(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    CVConfig cvConfig;
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(
        accountIdentifier, orgIdentifier, projectIdentifier, queryRecordsRequest.getProviderType());
    metricPackService.populateDataCollectionDsl(queryRecordsRequest.getProviderType(), metricPacks.get(0));
    if (queryRecordsRequest.getProviderType() == DataSourceType.SUMOLOGIC_METRICS) {
      cvConfig =
          HealthSourceOnboardMappingUtils.getCvConfigForNextGenMetric(queryRecordsRequest, projectParams, metricPacks);
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for health source provider.");
    }

    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(queryRecordsRequest.getProviderType());

    DataCollectionInfo<ConnectorConfigDTO> dataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.SLI);

    dataCollectionInfo.setCollectHostData(false);
    return dataCollectionInfo;
  }

  @Override
  public LogRecordsResponse fetchLogData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
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
    HealthSourceParamValuesResponse healthSourceParamValuesResponse = HealthSourceParamValuesResponse.builder().build();
    if (healthSourceParamValuesRequest.getProviderType() == MonitoredServiceDataSourceType.ELASTICSEARCH) {
      healthSourceParamValuesResponse = getHealthSourceParamValuesResponseForElasticSearch(
          healthSourceParamValuesRequest, projectParams, healthSourceParamValuesResponse);
    }
    return healthSourceParamValuesResponse;
  }

  private HealthSourceParamValuesResponse getHealthSourceParamValuesResponseForElasticSearch(
      HealthSourceParamValuesRequest healthSourceParamValuesRequest, ProjectParams projectParams,
      HealthSourceParamValuesResponse healthSourceParamValuesResponse) {
    if (QueryParamsDTO.QueryParamKeys.index.equals(healthSourceParamValuesRequest.getParamName())) {
      DataCollectionRequest request = ELKIndexCollectionRequest.builder().build();
      OnboardingRequestDTO onboardingRequestDTO =
          OnboardingRequestDTO.builder()
              .dataCollectionRequest(request)
              .connectorIdentifier(healthSourceParamValuesRequest.getConnectorIdentifier())
              .accountId(projectParams.getAccountIdentifier())
              .tracingId(CorrelationContext.getCorrelationId())
              .orgIdentifier(projectParams.getOrgIdentifier())
              .projectIdentifier(projectParams.getProjectIdentifier())
              .build();

      OnboardingResponseDTO response =
          onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
      List<String> indices = JsonUtils.asList(JsonUtils.asJson(response.getResult()), new TypeReference<>() {});
      List<HealthSourceParamValue> healthSourceParamValues =
          Optional.of(indices)
              .orElse(Collections.emptyList())
              .stream()
              .map(index -> HealthSourceParamValue.builder().name(index).value(index).build())
              .collect(Collectors.toList());
      healthSourceParamValuesResponse = HealthSourceParamValuesResponse.builder()
                                            .paramName(healthSourceParamValuesRequest.getParamName())
                                            .paramValues(healthSourceParamValues)
                                            .build();
    } else if (QueryParamsDTO.QueryParamKeys.timeStampFormat.equals(healthSourceParamValuesRequest.getParamName())) {
      List<HealthSourceParamValue> healthSourceParamValues =
          healthService.getTimeStampFormats()
              .stream()
              .map(timeStampFormat
                  -> HealthSourceParamValue.builder().name(timeStampFormat).value(timeStampFormat).build())
              .collect(Collectors.toList());
      healthSourceParamValuesResponse = HealthSourceParamValuesResponse.builder()
                                            .paramName(healthSourceParamValuesRequest.getParamName())
                                            .paramValues(healthSourceParamValues)
                                            .build();
    }
    return healthSourceParamValuesResponse;
  }

  private DataCollectionInfo<ConnectorConfigDTO> getDataCollectionInfoForLog(
      QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    CVConfig cvConfig;
    if (queryRecordsRequest.getProviderType().isNextGenSpec()) {
      cvConfig = HealthSourceOnboardMappingUtils.getCVConfigForNextGenLog(queryRecordsRequest, projectParams);
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for health source provider.");
    }
    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(queryRecordsRequest.getProviderType());
    return dataCollectionInfoMapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.DEPLOYMENT);
  }
}