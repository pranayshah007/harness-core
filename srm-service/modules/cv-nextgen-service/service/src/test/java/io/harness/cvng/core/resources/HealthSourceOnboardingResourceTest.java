/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.impl.MetricPackServiceImpl.SIGNALFX_DSL;
import static io.harness.cvng.core.services.impl.MetricPackServiceImpl.SUMOLOGIC_DSL;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.SignalFXMetricDataCollectionInfo;
import io.harness.cvng.beans.SumologicLogDataCollectionInfo;
import io.harness.cvng.beans.SumologicMetricDataCollectionInfo;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.beans.elk.ELKIndexCollectionRequest;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValue;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesResponse;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamsDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsResponse;
import io.harness.cvng.core.beans.healthsource.LogRecord;
import io.harness.cvng.core.beans.healthsource.LogRecordsResponse;
import io.harness.cvng.core.beans.healthsource.MetricRecordsResponse;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.healthsource.TimeSeries;
import io.harness.cvng.core.beans.healthsource.TimeSeriesDataPoint;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.impl.ElasticSearchLogNextGenHealthSourceHelper;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.ng.core.CorrelationContext;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HealthSourceOnboardingResourceTest extends CvNextGenTestBase {
  private static final String TEST_BASE_URL = "http://localhost:9998/account/";
  private static final String HEALTH_SOURCE_RECORDS_API = "/health-source/records";
  private BuilderFactory builderFactory;

  private static final HealthSourceOnboardingResource healthSourceOnboardingResource =
      new HealthSourceOnboardingResource();

  @Inject private Injector injector;
  @Inject private HealthSourceOnboardingService healthSourceOnboardingService;

  @Inject private Map<DataSourceType, NextGenHealthSourceHelper> dataSourceTypeNextGenHelperMapBinder;

  @Inject private ElasticSearchLogNextGenHealthSourceHelper elasticSearchLogNextGenHealthSourceHelper;

  private String accountIdentifier;
  private String orgIdentifier;
  private String tracingId;
  private String projectIdentifier;
  private String connectorIdentifier;

  private String baseURL;
  private ObjectMapper objectMapper;

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(healthSourceOnboardingResource).build();
  @Before
  public void setup() {
    injector.injectMembers(healthSourceOnboardingResource);
    builderFactory = BuilderFactory.getDefault();
    accountIdentifier = builderFactory.getProjectParams().getAccountIdentifier();
    orgIdentifier = builderFactory.getProjectParams().getOrgIdentifier();
    projectIdentifier = builderFactory.getProjectParams().getProjectIdentifier();
    connectorIdentifier = "account.sumologic_try_2";
    tracingId = "tracingId";
    baseURL = TEST_BASE_URL + accountIdentifier + "/org/" + orgIdentifier + "/project/" + projectIdentifier;
    CorrelationContext.setCorrelationId(tracingId);
    objectMapper = new ObjectMapper();
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchRawDataForNonArray() throws JsonProcessingException, IllegalAccessException {
    long startTime = 1671102931000L;
    long endTime = 1671103231000L;
    String metricQuery = "metric=Mem_UsedPercent";
    HealthSourceRecordsRequest healthSourceRecordsRequest = new HealthSourceRecordsRequest();
    healthSourceRecordsRequest.setStartTime(startTime);
    healthSourceRecordsRequest.setEndTime(endTime);
    healthSourceRecordsRequest.setQuery(metricQuery);
    healthSourceRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_METRICS);
    healthSourceRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    OnboardingRequestDTO onboardingRequestDTO = getOnboardingRequestDTOMetric(startTime, endTime, metricQuery);
    mockOnboardingService(onboardingRequestDTO, "xyz");
    Response response = RESOURCES.client()
                            .target(baseURL + HEALTH_SOURCE_RECORDS_API)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceRecordsRequest)));

    assertThat(response.getStatus()).isEqualTo(200);
    HealthSourceRecordsResponse healthSourceRecordsResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceRecordsResponse>>() {}).getResource();
    assertThat(healthSourceRecordsResponse.getRawRecords()).isEqualTo(List.of("xyz"));
    assertThat(healthSourceRecordsResponse.getProviderType()).isEqualTo(DataSourceType.SUMOLOGIC_METRICS);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchRawDataForMultipleJsonArray() throws JsonProcessingException, IllegalAccessException {
    long startTime = 1671102931000L;
    long endTime = 1671103231000L;
    String metricQuery = "metric=*";
    HealthSourceRecordsRequest healthSourceRecordsRequest = new HealthSourceRecordsRequest();
    healthSourceRecordsRequest.setStartTime(startTime);
    healthSourceRecordsRequest.setEndTime(endTime);
    healthSourceRecordsRequest.setQuery(metricQuery);
    healthSourceRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_METRICS);
    healthSourceRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    List<TimeSeriesDataPoint> timeSeriesDataPoints =
        List.of(TimeSeriesDataPoint.builder().timestamp(startTime).value(34.3434).build(),
            TimeSeriesDataPoint.builder().timestamp(1671103231000L).value(12.3434).build());
    OnboardingRequestDTO onboardingRequestDTO = getOnboardingRequestDTOMetric(startTime, endTime, metricQuery);
    mockOnboardingService(onboardingRequestDTO, timeSeriesDataPoints);
    Response response = RESOURCES.client()
                            .target(baseURL + HEALTH_SOURCE_RECORDS_API)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceRecordsRequest)));

    assertThat(response.getStatus()).isEqualTo(200);
    HealthSourceRecordsResponse healthSourceRecordsResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceRecordsResponse>>() {}).getResource();
    assertThat(healthSourceRecordsResponse.getRawRecords().size()).isEqualTo(2);
    assertThat(JsonUtils.asJson(healthSourceRecordsResponse.getRawRecords()))
        .isEqualTo(JsonUtils.asJson(timeSeriesDataPoints));
    assertThat(healthSourceRecordsResponse.getProviderType()).isEqualTo(DataSourceType.SUMOLOGIC_METRICS);
  }

  private OnboardingRequestDTO getOnboardingRequestDTOMetric(long startTime, long endTime, String metricQuery) {
    SumologicMetricSampleDataRequest request = SumologicMetricSampleDataRequest.builder()
                                                   .type(DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA)
                                                   .from(startTime)
                                                   .to(endTime)
                                                   .query(metricQuery)
                                                   .dsl(MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL)
                                                   .build();

    return OnboardingRequestDTO.builder()
        .dataCollectionRequest(request)
        .connectorIdentifier(connectorIdentifier)
        .accountId(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .tracingId(tracingId)
        .build();
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchRawDataForLogs() throws IllegalAccessException, JsonProcessingException {
    String logQuery = "_sourceCategory=windows/performance";
    long startTime = 1668137400000L;
    long endTime = 1668137700000L;
    HealthSourceRecordsRequest healthSourceRecordsRequest = new HealthSourceRecordsRequest();
    healthSourceRecordsRequest.setStartTime(startTime);
    healthSourceRecordsRequest.setEndTime(endTime);
    healthSourceRecordsRequest.setQuery(logQuery);
    healthSourceRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_LOG);
    healthSourceRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_STRING);
    LocalDateTime startTimeInLDT = Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDateTime();
    LocalDateTime endTimeInLDT =
        Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    SumologicLogSampleDataRequest request = SumologicLogSampleDataRequest.builder()
                                                .type(DataCollectionRequestType.SUMOLOGIC_LOG_SAMPLE_DATA)
                                                .from(startTimeInLDT.format(formatter))
                                                .to(endTimeInLDT.format(formatter))
                                                .query(logQuery)
                                                .dsl(MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL)
                                                .build();
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .tracingId(tracingId)
                                                    .build();
    mockOnboardingService(onboardingRequestDTO, "abc");

    Response response = RESOURCES.client()
                            .target(baseURL + HEALTH_SOURCE_RECORDS_API)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceRecordsRequest)));

    HealthSourceRecordsResponse healthSourceRecordsResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceRecordsResponse>>() {}).getResource();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(healthSourceRecordsResponse.getRawRecords()).isEqualTo(List.of("abc"));
    assertThat(healthSourceRecordsResponse.getProviderType()).isEqualTo(DataSourceType.SUMOLOGIC_LOG);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchRawDataForMetricsIncorrectQuery() throws JsonProcessingException, IllegalAccessException {
    long startTime = 1671102931000L;
    long endTime = 2671103231000L;
    String metricQuery = "++--";
    HealthSourceRecordsRequest healthSourceRecordsRequest = new HealthSourceRecordsRequest();
    healthSourceRecordsRequest.setStartTime(startTime);
    healthSourceRecordsRequest.setEndTime(endTime);
    healthSourceRecordsRequest.setQuery(metricQuery);
    healthSourceRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_METRICS);
    healthSourceRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    OnboardingRequestDTO onboardingRequestDTO = getOnboardingRequestDTOMetric(startTime, endTime, metricQuery);
    String errorResponse = "{\n"
        + "    \"status\": 500,\n"
        + "    \"id\": \"1MQ75-2QQ5F-RKA1Y\",\n"
        + "    \"code\": \"internal.error\",\n"
        + "    \"message\": \"Internal server error.\"\n"
        + "}";
    mockOnboardingService(onboardingRequestDTO, errorResponse);
    Response response = RESOURCES.client()
                            .target(baseURL + HEALTH_SOURCE_RECORDS_API)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceRecordsRequest)));

    assertThat(response.getStatus()).isEqualTo(200);
    HealthSourceRecordsResponse healthSourceRecordsResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceRecordsResponse>>() {}).getResource();
    assertThat(healthSourceRecordsResponse.getRawRecords().get(0)).isEqualTo(errorResponse);
    assertThat(healthSourceRecordsResponse.getProviderType()).isEqualTo(DataSourceType.SUMOLOGIC_METRICS);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchMetricDataOneTimeseries() throws JsonProcessingException, IllegalAccessException {
    long startTime = 1668137400000L;
    long endTime = 1668137700000L;
    String metricQuery = "metric=Mem_UsedPercent";

    QueryRecordsRequest queryRecordsRequest = new QueryRecordsRequest();
    queryRecordsRequest.setStartTime(startTime);
    queryRecordsRequest.setEndTime(endTime);
    queryRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    queryRecordsRequest.setQuery(metricQuery);
    queryRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_METRICS);
    queryRecordsRequest.setHealthSourceParams(HealthSourceParamsDTO.builder().build());
    OnboardingRequestDTO onboardingRequestDTO =
        createOnboardingRequestDTOForMetric(startTime, endTime, metricQuery, DataSourceType.SUMOLOGIC_METRICS);
    List<TimeSeriesRecord> timeSeriesRecords =
        generateTimeSeriesRecordData("host", "default", "Mem_UsedPercent", "Mem_UsedPercent", startTime, endTime);
    mockOnboardingService(onboardingRequestDTO, timeSeriesRecords);
    Response response = RESOURCES.client()
                            .target(baseURL + "/health-source/metric-records")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(queryRecordsRequest)));

    MetricRecordsResponse metricRecordsResponse =
        response.readEntity(new GenericType<RestResponse<MetricRecordsResponse>>() {}).getResource();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(metricRecordsResponse.getTimeSeriesData().size()).isEqualTo(1);
    assertThat(metricRecordsResponse.getTimeSeriesData().get(0))
        .isEqualTo(getTimeseriesFromResponse(timeSeriesRecords, "host"));
  }

  private OnboardingRequestDTO createOnboardingRequestDTOForMetric(
      long startTime, long endTime, String metricQuery, DataSourceType type) {
    DataCollectionInfo metricDataCollectionInfo = null;
    if (type == DataSourceType.SUMOLOGIC_METRICS) {
      metricDataCollectionInfo =
          SumologicMetricDataCollectionInfo.builder()
              .groupName("onboarding_default_group")
              .metricDefinitions(List.of(SumologicMetricDataCollectionInfo.MetricCollectionInfo.builder()
                                             .metricName("onboarding_sample_metric")
                                             .metricIdentifier("onboarding_sample_metric")
                                             .query(metricQuery)
                                             .build()))
              .build();
      metricDataCollectionInfo.setCollectHostData(false);
      metricDataCollectionInfo.setDataCollectionDsl(SUMOLOGIC_DSL);
    }
    if (type == DataSourceType.SPLUNK_SIGNALFX_METRICS) {
      metricDataCollectionInfo =
          SignalFXMetricDataCollectionInfo.builder()
              .groupName("onboarding_default_group")
              .metricDefinitions(List.of(SignalFXMetricDataCollectionInfo.MetricCollectionInfo.builder()
                                             .metricName("onboarding_sample_metric")
                                             .metricIdentifier("onboarding_sample_metric")
                                             .query(metricQuery)
                                             .build()))
              .build();
      metricDataCollectionInfo.setCollectHostData(false);
      metricDataCollectionInfo.setDataCollectionDsl(SIGNALFX_DSL);
    }

    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(metricDataCollectionInfo)
                                        .endTime(Instant.ofEpochMilli(endTime))
                                        .startTime(Instant.ofEpochMilli(startTime))
                                        .build();

    return OnboardingRequestDTO.builder()
        .dataCollectionRequest(request)
        .connectorIdentifier(connectorIdentifier)
        .accountId(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .tracingId(tracingId)
        .build();
  }

  private void mockOnboardingService(OnboardingRequestDTO onboardingRequestDTO, Object result)
      throws IllegalAccessException {
    OnboardingService mockedOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(
        elasticSearchLogNextGenHealthSourceHelper, "onboardingService", mockedOnboardingService, true);
    Map<DataSourceType, NextGenHealthSourceHelper> mockedMap = new HashMap<>(dataSourceTypeNextGenHelperMapBinder);
    mockedMap.put(DataSourceType.ELASTICSEARCH, elasticSearchLogNextGenHealthSourceHelper);

    FieldUtils.writeField(
        healthSourceOnboardingResource, "healthSourceOnboardingService", healthSourceOnboardingService, true);
    FieldUtils.writeField(healthSourceOnboardingService, "onboardingService", mockedOnboardingService, true);
    FieldUtils.writeField(healthSourceOnboardingService, "dataSourceTypeNextGenHelperMapBinder", mockedMap, true);

    when(mockedOnboardingService.getOnboardingResponse(
             eq(onboardingRequestDTO.getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(OnboardingResponseDTO.builder()
                        .accountId(onboardingRequestDTO.getAccountId())
                        .connectorIdentifier(onboardingRequestDTO.getConnectorIdentifier())
                        .orgIdentifier(onboardingRequestDTO.getOrgIdentifier())
                        .projectIdentifier(onboardingRequestDTO.getProjectIdentifier())
                        .result(result)
                        .build());
  }

  List<TimeSeriesRecord> generateTimeSeriesRecordData(
      String hostName, String groupName, String metricIdentifer, String metricName, long startTime, long endTime) {
    // generate data for every 1 min
    List<TimeSeriesRecord> timeSeriesRecords = new ArrayList<>();
    long currentTime = startTime;
    while (currentTime <= endTime) {
      double metricValue = ThreadLocalRandom.current().nextInt(50, 100);
      timeSeriesRecords.add(
          new TimeSeriesRecord(hostName, groupName, metricIdentifer, metricName, metricValue, currentTime));
      currentTime = Instant.ofEpochMilli(currentTime).plus(1, ChronoUnit.MINUTES).toEpochMilli();
    }
    return timeSeriesRecords;
  }

  List<LogDataRecord> generateLogRecordData(String hostName, long startTime, long endTime) {
    List<LogDataRecord> logDataRecords = new ArrayList<>();
    long currentTime = startTime;
    while (currentTime <= endTime) {
      double metricValue = ThreadLocalRandom.current().nextInt(50, 100);
      logDataRecords.add(new LogDataRecord(hostName, "ERROR: DUMMY ERROR Message " + metricValue, currentTime));
      currentTime = Instant.ofEpochMilli(currentTime).plus(1, ChronoUnit.MINUTES).toEpochMilli();
    }
    return logDataRecords;
  }

  List<LogRecord> getLogRecordFromResponse(List<LogDataRecord> logDataRecords) {
    return logDataRecords.stream()
        .map(logDataRecord
            -> LogRecord.builder()
                   .message(logDataRecord.getLog())
                   .timestamp(logDataRecord.getTimestamp())
                   .serviceInstance(logDataRecord.getHostname())
                   .build())
        .collect(Collectors.toList());
  }

  TimeSeries getTimeseriesFromResponse(List<TimeSeriesRecord> timeSeriesRecords, String timeseriesName) {
    List<TimeSeriesDataPoint> timeSeriesDataPoints = timeSeriesRecords.stream()
                                                         .map(timeSeriesRecord
                                                             -> TimeSeriesDataPoint.builder()
                                                                    .timestamp(timeSeriesRecord.getTimestamp())
                                                                    .value(timeSeriesRecord.getMetricValue())
                                                                    .build())
                                                         .collect(Collectors.toList());
    return TimeSeries.builder().timeseriesName(timeseriesName).data(timeSeriesDataPoints).build();
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchLogData() throws IllegalAccessException, JsonProcessingException {
    String logQuery = "_sourceCategory=windows/performance";
    long startTime = 1668137400000L;
    long endTime = 1668137700000L;

    QueryRecordsRequest queryRecordsRequest = new QueryRecordsRequest();
    queryRecordsRequest.setStartTime(startTime);
    queryRecordsRequest.setEndTime(endTime);
    queryRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    queryRecordsRequest.setQuery(logQuery);
    queryRecordsRequest.setProviderType(DataSourceType.SUMOLOGIC_LOG);
    queryRecordsRequest.setHealthSourceQueryParams(
        QueryParamsDTO.builder().serviceInstanceField("_sourceHost").build());
    queryRecordsRequest.setHealthSourceParams(HealthSourceParamsDTO.builder().build());
    List<LogDataRecord> logDataRecords = generateLogRecordData("host", startTime, endTime);

    DataCollectionInfo sumologicLogDataCollectionInfo =
        SumologicLogDataCollectionInfo.builder().query(logQuery).serviceInstanceIdentifier("_sourceHost").build();
    sumologicLogDataCollectionInfo.setCollectHostData(false);
    sumologicLogDataCollectionInfo.setDataCollectionDsl(SUMOLOGIC_DSL);

    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(sumologicLogDataCollectionInfo)
                                        .endTime(Instant.ofEpochMilli(endTime))
                                        .startTime(Instant.ofEpochMilli(startTime))
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .tracingId(tracingId)
                                                    .build();

    mockOnboardingService(onboardingRequestDTO, logDataRecords);
    Response response = RESOURCES.client()
                            .target(baseURL + "/health-source/log-records")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(queryRecordsRequest)));

    LogRecordsResponse logRecordsResponse =
        response.readEntity(new GenericType<RestResponse<LogRecordsResponse>>() {}).getResource();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(logRecordsResponse.getLogRecords().size()).isEqualTo(logDataRecords.size());
    assertThat(logRecordsResponse.getLogRecords()).isEqualTo(getLogRecordFromResponse(logDataRecords));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchIndexForElasticSearchLog() throws JsonProcessingException, IllegalAccessException {
    HealthSourceParamValuesRequest healthSourceParamValuesRequest = new HealthSourceParamValuesRequest();
    healthSourceParamValuesRequest.setProviderType(MonitoredServiceDataSourceType.ELASTICSEARCH);
    healthSourceParamValuesRequest.setParamName(QueryParamsDTO.QueryParamKeys.index);
    String connectorIdentifierELK = "account.ELK_Connector";
    healthSourceParamValuesRequest.setConnectorIdentifier(connectorIdentifierELK);

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .accountId(accountIdentifier)
                                                    .connectorIdentifier(connectorIdentifierELK)
                                                    .accountId(accountIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .dataCollectionRequest(ELKIndexCollectionRequest.builder().build())
                                                    .build();
    List<String> indexList = List.of("filebeat-6.8.8-2023.01.20", "filebeat-6.8.8-2023.01.23",
        "filebeat-6.8.8-2023.01.24", "filebeat-6.8.8-2023.01.13", "filebeat-6.8.8-2023.01.26",
        "filebeat-6.8.8-2023.01.12", ".kibana", "filebeat-6.8.8-2023.01.27");
    mockOnboardingService(onboardingRequestDTO, indexList);
    Response response = RESOURCES.client()
                            .target(baseURL + "/health-source/param-values")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceParamValuesRequest)));
    HealthSourceParamValuesResponse paramValuesResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceParamValuesResponse>>() {}).getResource();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(paramValuesResponse.getParamValues()
                   .stream()
                   .map(HealthSourceParamValue::getValue)
                   .collect(Collectors.toList()))
        .isEqualTo(indexList);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchTimestampFormatForElasticSearchLog() throws JsonProcessingException {
    HealthSourceParamValuesRequest healthSourceParamValuesRequest = new HealthSourceParamValuesRequest();
    healthSourceParamValuesRequest.setProviderType(MonitoredServiceDataSourceType.ELASTICSEARCH);
    healthSourceParamValuesRequest.setParamName(QueryParamsDTO.QueryParamKeys.timeStampFormat);
    Response response = RESOURCES.client()
                            .target(baseURL + "/health-source/param-values")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(healthSourceParamValuesRequest)));
    HealthSourceParamValuesResponse paramValuesResponse =
        response.readEntity(new GenericType<RestResponse<HealthSourceParamValuesResponse>>() {}).getResource();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(paramValuesResponse.getParamName()).isEqualTo(QueryParamsDTO.QueryParamKeys.timeStampFormat);
    assertThat(paramValuesResponse.getParamValues()).isNotEmpty();
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void fetchMetricChartSIIMultipleTimeseries() throws JsonProcessingException, IllegalAccessException {
    long startTime = 1668137400000L;
    long endTime = Instant.ofEpochMilli(startTime).plus(5, ChronoUnit.MINUTES).toEpochMilli();
    String metricQuery = "metric=Mem_UsedPercent";

    QueryRecordsRequest queryRecordsRequest = new QueryRecordsRequest();
    queryRecordsRequest.setStartTime(startTime);
    queryRecordsRequest.setEndTime(endTime);
    queryRecordsRequest.setConnectorIdentifier(connectorIdentifier);
    queryRecordsRequest.setQuery(metricQuery);
    queryRecordsRequest.setProviderType(DataSourceType.SPLUNK_SIGNALFX_METRICS);
    queryRecordsRequest.setHealthSourceParams(HealthSourceParamsDTO.builder().build());
    OnboardingRequestDTO onboardingRequestDTO =
        createOnboardingRequestDTOForMetric(startTime, endTime, metricQuery, DataSourceType.SPLUNK_SIGNALFX_METRICS);
    String groupName = "default";
    String metricIdentifier = "Mem_UsedPercent";
    List<TimeSeriesRecord> timeSeriesRecords1 =
        generateTimeSeriesRecordData("host1", groupName, metricIdentifier, "Mem_UsedPercent", startTime, endTime);
    List<TimeSeriesRecord> timeSeriesRecords2 =
        generateTimeSeriesRecordData("host2", groupName, metricIdentifier, "Mem_UsedPercent", startTime, endTime);
    List<TimeSeriesRecord> timeSeriesRecords3 =
        generateTimeSeriesRecordData("host3", groupName, metricIdentifier, "Mem_UsedPercent", startTime, endTime);
    List<TimeSeriesRecord> timeSeriesRecordList = Stream.of(timeSeriesRecords1, timeSeriesRecords2, timeSeriesRecords3)
                                                      .flatMap(Collection::stream)
                                                      .collect(Collectors.toList());

    mockOnboardingService(onboardingRequestDTO, timeSeriesRecordList);
    Response response = RESOURCES.client()
                            .target(baseURL + "/health-source/metric-records")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(queryRecordsRequest)));

    MetricRecordsResponse metricRecordsResponse =
        response.readEntity(new GenericType<RestResponse<MetricRecordsResponse>>() {}).getResource();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(metricRecordsResponse.getTimeSeriesData())
        .contains(getTimeseriesFromResponse(timeSeriesRecords1, "host1"));
    assertThat(metricRecordsResponse.getTimeSeriesData())
        .contains(getTimeseriesFromResponse(timeSeriesRecords2, "host2"));
    assertThat(metricRecordsResponse.getTimeSeriesData())
        .contains(getTimeseriesFromResponse(timeSeriesRecords3, "host3"));
  }
}
