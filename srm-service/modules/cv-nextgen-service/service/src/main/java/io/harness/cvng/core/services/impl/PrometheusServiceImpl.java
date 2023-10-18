/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.aws.AwsDataCollectionRequest;
import io.harness.cvng.beans.aws.AwsDataCollectionRequest.AwsDataCollectionRequestBuilder;
import io.harness.cvng.beans.prometheus.PrometheusFetchSampleDataRequest;
import io.harness.cvng.beans.prometheus.PrometheusLabelNamesFetchRequest;
import io.harness.cvng.beans.prometheus.PrometheusLabelValuesFetchRequest;
import io.harness.cvng.beans.prometheus.PrometheusMetricListFetchRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.PrometheusSampleData;
import io.harness.cvng.core.beans.params.PrometheusConnectionParams;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.PrometheusService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PrometheusServiceImpl implements PrometheusService {
  @Inject OnboardingService onboardingService;
  @Override
  public List<String> getMetricNames(String accountId, String orgIdentifier, String projectIdentifier, String tracingId,
      PrometheusConnectionParams prometheusConnectionParams) {
    DataSourceType dataSourceType = confirmDataSourceType(prometheusConnectionParams.getDataSourceType());
    DataCollectionRequest request;
    switch (dataSourceType) {
      case AWS_PROMETHEUS:
        validateAwsParameters(prometheusConnectionParams);
        request = getAwsDataCollectionRequestBuilder(prometheusConnectionParams, tracingId)
                      .urlServiceSuffix(
                          "workspaces/" + prometheusConnectionParams.getWorkspaceId() + "/api/v1/label/__name__/values")
                      .build();
        break;
      default:
        request = PrometheusMetricListFetchRequest.builder()
                      .type(DataCollectionRequestType.PROMETHEUS_METRIC_LIST_GET)
                      .build();
    }

    OnboardingResponseDTO onboardingResponseDTO = getOnboardingResponseDTO(request, accountId,
        prometheusConnectionParams.getConnectorIdentifier(), orgIdentifier, projectIdentifier, tracingId);
    return toListOfString(onboardingResponseDTO);
  }

  @Override
  public List<String> getLabelNames(String accountId, String orgIdentifier, String projectIdentifier, String tracingId,
      PrometheusConnectionParams prometheusConnectionParams) {
    DataSourceType dataSourceType = confirmDataSourceType(prometheusConnectionParams.getDataSourceType());
    DataCollectionRequest request;
    switch (dataSourceType) {
      case AWS_PROMETHEUS:
        validateAwsParameters(prometheusConnectionParams);
        request = getAwsDataCollectionRequestBuilder(prometheusConnectionParams, tracingId)
                      .urlServiceSuffix("workspaces/" + prometheusConnectionParams.getWorkspaceId() + "/api/v1/labels")
                      .build();
        break;
      default:
        request = PrometheusLabelNamesFetchRequest.builder()
                      .type(DataCollectionRequestType.PROMETHEUS_LABEL_NAMES_GET)
                      .build();
    }

    OnboardingResponseDTO onboardingResponseDTO = getOnboardingResponseDTO(request, accountId,
        prometheusConnectionParams.getConnectorIdentifier(), orgIdentifier, projectIdentifier, tracingId);
    return toListOfString(onboardingResponseDTO);
  }

  @Override
  public List<String> getLabelValues(String accountId, String orgIdentifier, String projectIdentifier, String labelName,
      String tracingId, PrometheusConnectionParams prometheusConnectionParams) {
    DataSourceType dataSourceType = confirmDataSourceType(prometheusConnectionParams.getDataSourceType());
    DataCollectionRequest request;
    switch (dataSourceType) {
      case AWS_PROMETHEUS:
        validateAwsParameters(prometheusConnectionParams);
        request = getAwsDataCollectionRequestBuilder(prometheusConnectionParams, tracingId)
                      .urlServiceSuffix("workspaces/" + prometheusConnectionParams.getWorkspaceId() + "/api/v1/label/"
                          + labelName + "/values")
                      .build();
        break;
      default:
        request = PrometheusLabelValuesFetchRequest.builder()
                      .type(DataCollectionRequestType.PROMETHEUS_LABEL_VALUES_GET)
                      .labelName(labelName)
                      .build();
    }

    OnboardingResponseDTO onboardingResponseDTO = getOnboardingResponseDTO(request, accountId,
        prometheusConnectionParams.getConnectorIdentifier(), orgIdentifier, projectIdentifier, tracingId);
    return toListOfString(onboardingResponseDTO);
  }

  @Override
  public List<PrometheusSampleData> getSampleData(String accountId, String orgIdentifier, String projectIdentifier,
      String query, String tracingId, PrometheusConnectionParams prometheusConnectionParams) {
    Instant endtime = DateTimeUtils.roundDownTo1MinBoundary(Instant.now());
    Instant starttime = endtime.minus(Duration.of(2, ChronoUnit.HOURS));
    DataSourceType dataSourceType = confirmDataSourceType(prometheusConnectionParams.getDataSourceType());
    Preconditions.checkState(query.contains("{") && query.contains("}"),
        "Query must contain filters. Please add blank curly braces({}) in case of absence of filters");
    DataCollectionRequest request;
    switch (dataSourceType) {
      case AWS_PROMETHEUS:
        validateAwsParameters(prometheusConnectionParams);
        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("query", query);
        queryMap.put("end", String.valueOf(endtime.getEpochSecond()));
        queryMap.put("start", String.valueOf(starttime.getEpochSecond()));
        queryMap.put("step", "1m");
        request =
            getAwsDataCollectionRequestBuilder(prometheusConnectionParams, tracingId)
                .urlServiceSuffix("workspaces/" + prometheusConnectionParams.getWorkspaceId() + "/api/v1/query_range")
                .queryParameters(queryMap)
                .build();
        break;
      default:
        request = PrometheusFetchSampleDataRequest.builder()
                      .type(DataCollectionRequestType.PROMETHEUS_SAMPLE_DATA)
                      .query(query)
                      .startTime(starttime)
                      .endTime(endtime)
                      .build();
    }

    OnboardingResponseDTO onboardingResponseDTO = getOnboardingResponseDTO(request, accountId,
        prometheusConnectionParams.getConnectorIdentifier(), orgIdentifier, projectIdentifier, tracingId);
    return toListOfPrometheusSampleData(onboardingResponseDTO);
  }

  private DataSourceType confirmDataSourceType(DataSourceType dataSourceType) {
    return Objects.isNull(dataSourceType) ? DataSourceType.PROMETHEUS : dataSourceType;
  }

  private void validateAwsParameters(PrometheusConnectionParams prometheusConnectionParams) {
    Preconditions.checkNotNull(prometheusConnectionParams.getRegion(), generateErrorMessageFromParam("region"));
    Preconditions.checkNotNull(
        prometheusConnectionParams.getWorkspaceId(), generateErrorMessageFromParam("workspaceId"));
  }

  private AwsDataCollectionRequestBuilder getAwsDataCollectionRequestBuilder(
      PrometheusConnectionParams prometheusConnectionParams, String tracingId) {
    return AwsDataCollectionRequest.builder()
        .type(DataCollectionRequestType.AWS_GENERIC_DATA_COLLECTION_REQUEST)
        .tracingId(tracingId)
        .region(prometheusConnectionParams.getRegion())
        .awsService("aps")
        .urlServicePrefix("aps-workspaces");
  }

  private OnboardingResponseDTO getOnboardingResponseDTO(DataCollectionRequest request, String accountId,
      String connectorIdentifier, String orgIdentifier, String projectIdentifier, String tracingId) {
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    return onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
  }

  private List<String> toListOfString(OnboardingResponseDTO onboardingResponseDTO) {
    try {
      if (onboardingResponseDTO.getResult() instanceof Map) {
        return ((Map<String, List<String>>) onboardingResponseDTO.getResult()).get("data");
      } else if (onboardingResponseDTO.getResult() instanceof List) {
        // to support backward compatibility, till delegate with new DSL changes are deployed
        return (List<String>) onboardingResponseDTO.getResult();
      } else {
        throw new IllegalStateException(
            "Data collection returned unexpected data type: " + onboardingResponseDTO.getResult().getClass());
      }
    } catch (Exception ex) {
      throw new DataCollectionException(ex);
    }
  }

  private List<PrometheusSampleData> toListOfPrometheusSampleData(OnboardingResponseDTO onboardingResponseDTO) {
    try {
      if (onboardingResponseDTO.getResult() instanceof Map) {
        List<PrometheusSampleData> sampleData = new ArrayList<>();
        List<Map<String, Object>> responseData =
            (List<Map<String, Object>>) ((Map<String, Object>) ((
                                             (Map<String, Object>) onboardingResponseDTO.getResult())
                                                                    .get("data")))
                .get("result");
        sampleData.addAll(responseData.stream()
                              .map(data
                                  -> PrometheusSampleData.builder()
                                         .metricDetails((Map<String, String>) data.get("metric"))
                                         .data(((List<List<Object>>) data.get("values"))
                                                   .stream()
                                                   .map(innerList
                                                       -> innerList.stream()
                                                              .map(value -> Double.valueOf(String.valueOf(value)))
                                                              .collect(Collectors.toList()))
                                                   .collect(Collectors.toList()))
                                         .build())
                              .collect(Collectors.toList()));
        return sampleData;
      } else if (onboardingResponseDTO.getResult() instanceof List) {
        // to support backward compatibility, till delegate with new DSL changes are deployed
        return JsonUtils.asList(
            JsonUtils.asJson(onboardingResponseDTO.getResult()), new TypeReference<List<PrometheusSampleData>>() {});
      } else {
        throw new IllegalStateException(
            "Data collection returned unexpected data type: " + onboardingResponseDTO.getResult().getClass());
      }

    } catch (Exception ex) {
      throw new DataCollectionException(ex);
    }
  }
}
