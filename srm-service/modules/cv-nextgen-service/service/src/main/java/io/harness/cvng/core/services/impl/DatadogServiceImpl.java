/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DatadogLogDataCollectionInfo;
import io.harness.cvng.beans.datadog.DatadogActiveMetricsRequest;
import io.harness.cvng.beans.datadog.DatadogDashboardDetailsRequest;
import io.harness.cvng.beans.datadog.DatadogDashboardListRequest;
import io.harness.cvng.beans.datadog.DatadogLogIndexesRequest;
import io.harness.cvng.beans.datadog.DatadogLogSampleDataRequest;
import io.harness.cvng.beans.datadog.DatadogMetricTagsRequest;
import io.harness.cvng.beans.datadog.DatadogTimeSeriesPointsRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDetail;
import io.harness.cvng.core.beans.datadog.MetricTagResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DatadogService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.exception.OnboardingException;
import io.harness.cvng.utils.DatadogQueryUtils;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PageUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class DatadogServiceImpl implements DatadogService {
  public static final int MAX_ACTIVE_METRICS_COUNT = 1000;
  public static final int MAX_METRIC_TAGS_COUNT = 1000;
  @Inject private FeatureFlagService featureFlagService;
  public static final URL DATADOG_SAMPLE_DSL_PATH =
      MetricPackServiceImpl.class.getResource("/datadog/dsl/datadog-time-series-points.datacollection");

  public static final URL DATADOG_SAMPLE_V2_DSL_PATH =
      MetricPackServiceImpl.class.getResource("/datadog/dsl/datadog-time-series-points-v2.datacollection");

  @Inject private OnboardingService onboardingService;
  @Inject private Clock clock;

  @Override
  public PageResponse<DatadogDashboardDTO> getAllDashboards(ProjectParams projectParams, String connectorIdentifier,
      int pageSize, int offset, String filter, String tracingId) {
    DataCollectionRequest<DatadogConnectorDTO> request =
        DatadogDashboardListRequest.builder().type(DataCollectionRequestType.DATADOG_DASHBOARD_LIST).build();

    Type type = new TypeToken<List<DatadogDashboardDTO>>() {}.getType();
    List<DatadogDashboardDTO> dashboardList =
        performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);

    List<DatadogDashboardDTO> filteredList = dashboardList;
    if (isNotEmpty(filter)) {
      filteredList = dashboardList.stream()
                         .filter(dashboardDto
                             -> dashboardDto.getName() != null
                                 && dashboardDto.getName().toLowerCase().contains(filter.toLowerCase()))
                         .collect(Collectors.toList());
    }
    return PageUtils.offsetAndLimit(filteredList, offset, pageSize);
  }

  @Override
  public List<DatadogDashboardDetail> getDashboardDetails(
      ProjectParams projectParams, String connectorIdentifier, String dashboardId, String tracingId) {
    DataCollectionRequest<DatadogConnectorDTO> request = DatadogDashboardDetailsRequest.builder()
                                                             .dashboardId(dashboardId)
                                                             .type(DataCollectionRequestType.DATADOG_DASHBOARD_DETAILS)
                                                             .build();

    Type type = new TypeToken<List<DatadogDashboardDetail>>() {}.getType();
    return performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
  }
  @Override
  public List<String> getMetricTagsList(
      ProjectParams projectParams, String connectorIdentifier, String metricName, String tracingId) {
    DataCollectionRequest<DatadogConnectorDTO> request = DatadogMetricTagsRequest.builder()
                                                             .metric(metricName)
                                                             .type(DataCollectionRequestType.DATADOG_METRIC_TAGS)
                                                             .build();

    Type type = new TypeToken<List<String>>() {}.getType();
    List<String> metricTagsList =
        performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
    // limit to 1000 items (datadog api doesn't provide additional filtering)
    return metricTagsList.stream().limit(MAX_METRIC_TAGS_COUNT).collect(Collectors.toList());
  }

  @Override
  public List<String> getActiveMetrics(
      ProjectParams projectParams, String connectorIdentifier, String filter, String tracingId) {
    long fromEpochSecond = clock.instant().minus(Duration.ofDays(1)).getEpochSecond();
    DataCollectionRequest<DatadogConnectorDTO> request = DatadogActiveMetricsRequest.builder()
                                                             .from(fromEpochSecond)
                                                             .type(DataCollectionRequestType.DATADOG_ACTIVE_METRICS)
                                                             .build();

    Type type = new TypeToken<List<String>>() {}.getType();
    List<String> activeMetricsList =
        performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);

    // limit to 1000 items (datadog api doesn't provide additional filtering)
    return activeMetricsList.stream()
        .filter(metric -> isEmpty(filter) || metric.toLowerCase().contains(filter.toLowerCase()))
        .limit(MAX_ACTIVE_METRICS_COUNT)
        .collect(Collectors.toList());
  }

  @SneakyThrows
  @Override
  public List<TimeSeriesSampleDTO> getTimeSeriesPoints(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId) {
    Instant now = DateTimeUtils.roundDownTo1MinBoundary(Instant.now());
    DataCollectionRequest<DatadogConnectorDTO> request;
    if (featureFlagService.isFeatureFlagEnabled(
            projectParams.getAccountIdentifier(), FeatureName.SRM_DATADOG_METRICS_FORMULA_SUPPORT.name())) {
      Pair<String, List<String>> formulaQueriesPair = DatadogQueryUtils.processCompositeQuery(query, null, false);
      String formula = formulaQueriesPair.getLeft();
      List<String> formulaQueries = formulaQueriesPair.getRight();
      request = DatadogTimeSeriesPointsRequest.builder()
                    .type(DataCollectionRequestType.DATADOG_TIME_SERIES_POINTS)
                    .from(now.minus(1, ChronoUnit.HOURS).toEpochMilli())
                    .to(now.toEpochMilli())
                    .DSL(Resources.toString(DATADOG_SAMPLE_V2_DSL_PATH, Charsets.UTF_8))
                    .formula(formula)
                    .formulaQueriesList(formulaQueries)
                    .query(query)
                    .build();
    } else {
      request = DatadogTimeSeriesPointsRequest.builder()
                    .type(DataCollectionRequestType.DATADOG_TIME_SERIES_POINTS)
                    .from(now.minus(1, ChronoUnit.HOURS).getEpochSecond())
                    .to(now.getEpochSecond())
                    .DSL(Resources.toString(DATADOG_SAMPLE_DSL_PATH, Charsets.UTF_8))
                    .query(query)
                    .build();
    }
    Type type = new TypeToken<List<TimeSeriesSampleDTO>>() {}.getType();
    return performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public List<LinkedHashMap> getSampleLogData(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId) {
    try {
      Instant now = DateTimeUtils.roundDownTo1MinBoundary(Instant.now());

      DataCollectionRequest<DatadogConnectorDTO> request = DatadogLogSampleDataRequest.builder()
                                                               .type(DataCollectionRequestType.DATADOG_LOG_SAMPLE_DATA)
                                                               .from(now.minus(Duration.ofMinutes(1000)).toEpochMilli())
                                                               .to(now.toEpochMilli())
                                                               .limit(DatadogLogDataCollectionInfo.LOG_MAX_LIMIT)
                                                               .query(query)
                                                               .build();

      Type type = new TypeToken<List<LinkedHashMap>>() {}.getType();
      return performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
    } catch (Exception ex) {
      String msg = "Exception while trying to fetch sample data. Please ensure that the query is valid.";
      log.error(msg, ex);
      throw new OnboardingException(msg);
    }
  }

  @Override
  public List<String> getLogIndexes(ProjectParams projectParams, String connectorIdentifier, String tracingId) {
    DataCollectionRequest<DatadogConnectorDTO> request =
        DatadogLogIndexesRequest.builder().type(DataCollectionRequestType.DATADOG_LOG_INDEXES).build();

    Type type = new TypeToken<List<String>>() {}.getType();
    return performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public MetricTagResponseDTO getMetricTagsResponse(
      ProjectParams projectParams, String connectorIdentifier, String metricName, String filter, String tracingId) {
    DataCollectionRequest<DatadogConnectorDTO> request = DatadogMetricTagsRequest.builder()
                                                             .metric(metricName)
                                                             .type(DataCollectionRequestType.DATADOG_METRIC_TAGS)
                                                             .build();
    Type type = new TypeToken<List<String>>() {}.getType();
    List<String> metricTagsList =
        performRequestAndGetDataResult(request, type, projectParams, connectorIdentifier, tracingId);
    Set<String> tagKeys = metricTagsList.stream()
                              .map(tag -> tag.split(":")[0])
                              .distinct()
                              .limit(MAX_METRIC_TAGS_COUNT)
                              .collect(Collectors.toSet());
    List<String> metricTags =
        metricTagsList.stream()
            .filter(metricTag -> isEmpty(filter) || metricTag.toLowerCase().contains(filter.toLowerCase()))
            .limit(MAX_METRIC_TAGS_COUNT)
            .collect(Collectors.toList());
    return MetricTagResponseDTO.builder().metricTags(metricTags).tagKeys(tagKeys).build();
  }

  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {
    getAllDashboards(ProjectParams.builder()
                         .accountIdentifier(accountId)
                         .orgIdentifier(orgIdentifier)
                         .projectIdentifier(projectIdentifier)
                         .build(),
        connectorIdentifier, 1, 0, null, tracingId);
  }

  private <T> T performRequestAndGetDataResult(DataCollectionRequest<DatadogConnectorDTO> dataCollectionRequest,
      Type type, ProjectParams projectParams, String connectorIdentifier, String tracingId) {
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(dataCollectionRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .tracingId(tracingId)
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    return new Gson().fromJson(JsonUtils.asJson(response.getResult()), type);
  }
}
