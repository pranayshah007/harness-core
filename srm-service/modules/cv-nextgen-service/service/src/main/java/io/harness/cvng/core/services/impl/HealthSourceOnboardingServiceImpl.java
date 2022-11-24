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
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
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
import io.harness.cvng.core.entities.SumologicLogCVConfig;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.cvng.core.entities.SumologicMetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.ng.core.Status;
import io.harness.serializer.JsonUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HealthSourceOnboardingServiceImpl implements HealthSourceOnboardingService {
  @Inject private OnboardingService onboardingService;

  @Inject private MetricPackService metricPackService;

  @Inject private Map<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Override
  public HealthSourceRecordsResponse fetchSampleRawRecordsForHealthSource(
      HealthSourceRecordsRequest healthSourceRecordsRequest, ProjectParams projectParams) {
    Object result = getResponseFromHealthSourceProvider(healthSourceRecordsRequest,
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    HealthSourceRecordsResponse healthSourceRecordsResponse =
        HealthSourceRecordsResponse.builder().providerType(healthSourceRecordsRequest.getProviderType()).build();
    healthSourceRecordsResponse.getRawRecords().add(result);
    return healthSourceRecordsResponse;
  }

  private Object getResponseFromHealthSourceProvider(
      HealthSourceRecordsRequest healthSourceRecordsRequest, String accountId, String orgId, String projectId) {
    DataCollectionRequest<SumoLogicConnectorDTO> request = null;

    // TODO the idea is we should get the corrector provider data collection info, we cant have it sumologic specific.
    switch (healthSourceRecordsRequest.getProviderType()) {
      case SUMOLOGIC_METRICS:
        request = getSumologicMetricDataCollectionRequest(healthSourceRecordsRequest);
        break;
      case SUMOLOGIC_LOG:
        request = getSumoLogicLogDataCollectionRequest(healthSourceRecordsRequest);
        break;
      default:
        // TODO throw exception
        break;
    }

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(healthSourceRecordsRequest.getConnectorIdentifier())
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .tracingId("tracingId")
            .build();

    // TODO DIfferent DSL no service name filed.
    // TODO Option to override sourceHost

    OnboardingResponseDTO onboardingResponseDTO =
        onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    return onboardingResponseDTO.getResult();
  }

  @Override
  public MetricRecordsResponse fetchMetricData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    SumologicMetricCVConfig sumologicMetricCVConfig =
        SumologicMetricCVConfig.builder()
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .groupName("Default_Group")
            .monitoredServiceIdentifier("fetch_sample_data_test") // TODO What to set here.
            .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
            .category(CVMonitoringCategory.PERFORMANCE) // TODO Why is this performance ?
            .build();

    sumologicMetricCVConfig.setMetricInfos(Collections.singletonList(
        SumologicMetricInfo.builder()
            .query(queryRecordsRequest.getQuery())
            .identifier("sample_metric")
            .metricName("sample_metric")
            .serviceInstanceFieldName(queryRecordsRequest.getHealthSourceQueryParams().getServiceInstanceField())
            .build()));
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(
        accountIdentifier, orgIdentifier, projectIdentifier, DataSourceType.SUMOLOGIC_METRICS);
    sumologicMetricCVConfig.setMetricPack(metricPacks.get(0));
    metricPackService.populateDataCollectionDsl(DataSourceType.SUMOLOGIC_METRICS, metricPacks.get(0));

    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(queryRecordsRequest.getProviderType());

    DataCollectionInfo<ConnectorConfigDTO> sumologicMetricDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(sumologicMetricCVConfig, VerificationTask.TaskType.SLI);

    sumologicMetricDataCollectionInfo.setCollectHostData(false);
    DataCollectionRequest<ConnectorConfigDTO> request =
        SyncDataCollectionRequest.builder()
            .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
            .dataCollectionInfo(sumologicMetricDataCollectionInfo)
            .endTime(Instant.ofEpochMilli(queryRecordsRequest.getEndTime()))
            .startTime(Instant.ofEpochMilli(queryRecordsRequest.getStartTime()))
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .tracingId("tracingId")
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountIdentifier, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
    List<TimeSeriesRecord> timeSeriesRecords = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    TimeSeries timeSeries =
        TimeSeries.builder().timeseriesName("sampleTimeseries").build(); // TODO Understand multiple Timeseries
    timeSeriesRecords.forEach(timeSeriesRecord
        -> timeSeries.getData().add(TimeSeriesDataPoint.builder()
                                        .timestamp(timeSeriesRecord.getTimestamp())
                                        .value(timeSeriesRecord.getMetricValue())
                                        .build()));
    MetricRecordsResponse metricRecordsResponse = MetricRecordsResponse.builder().build();
    metricRecordsResponse.getTimeSeriesData().add(timeSeries);
    return metricRecordsResponse;
  }

  @Override
  public LogRecordsResponse fetchLogData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    SumologicLogCVConfig sumologicLogCVConfig =
        SumologicLogCVConfig.builder()
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .accountId(accountIdentifier)
            .monitoredServiceIdentifier("fetch_sample_data_test")
            .serviceInstanceIdentifier(queryRecordsRequest.getHealthSourceQueryParams().getServiceInstanceField())
            .query(queryRecordsRequest.getQuery())
            .queryName("queryName")
            .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
            .build();
    DataCollectionInfoMapper<DataCollectionInfo<ConnectorConfigDTO>, CVConfig> dataCollectionInfoMapper =
        dataSourceTypeDataCollectionInfoMapperMap.get(queryRecordsRequest.getProviderType());
    DataCollectionInfo<ConnectorConfigDTO> sumologicLogDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(sumologicLogCVConfig, VerificationTask.TaskType.SLI);
    sumologicLogDataCollectionInfo.setDataCollectionDsl(sumologicLogCVConfig.getDataCollectionDsl());
    // TODO Is something also need to be done for host data ?
    DataCollectionRequest<ConnectorConfigDTO> request =
        SyncDataCollectionRequest.builder()
            .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
            .dataCollectionInfo(sumologicLogDataCollectionInfo)
            .endTime(Instant.ofEpochMilli(queryRecordsRequest.getEndTime()))
            .startTime(Instant.ofEpochMilli(queryRecordsRequest.getStartTime()))
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(queryRecordsRequest.getConnectorIdentifier())
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .tracingId("tracingId")
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountIdentifier, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<LogDataRecord>>() {}.getType();
    List<LogDataRecord> logDataRecords = gson.fromJson(JsonUtils.asJson(response.getResult()), type);

    // Where are we setting the DSL?

    // TODO use actual DSL instead of onboard with the option for hostwise.
    // How to get the query name ?
    List<LogRecord> logRecords = new ArrayList<>();
    logDataRecords.forEach(logDataRecord
        -> logRecords.add(LogRecord.builder()
                              .timestamp(logDataRecord.getTimestamp())
                              .message(logDataRecord.getLog())
                              .serviceInstance(logDataRecord.getHostname())
                              .build()));
    LogRecordsResponse logRecordsResponse = LogRecordsResponse.builder().build();
    logRecordsResponse.getLogRecords().addAll(logRecords);
    return logRecordsResponse;
  }

  private static DataCollectionRequest<SumoLogicConnectorDTO> getSumologicMetricDataCollectionRequest(
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

  private static DataCollectionRequest<SumoLogicConnectorDTO> getSumoLogicLogDataCollectionRequest(
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
}