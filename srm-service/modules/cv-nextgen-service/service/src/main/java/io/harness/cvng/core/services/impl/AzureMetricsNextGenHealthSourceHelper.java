/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.azure.AzureMetricsSampleDataRequest;
import io.harness.cvng.beans.azure.AzureServiceInstanceFieldDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValue;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.utils.AggregationType;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.ng.core.CorrelationContext;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureMetricsNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Inject private OnboardingService onboardingService;

  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    return AzureMetricsSampleDataRequest.builder()
        .dsl(MetricPackServiceImpl.AZURE_METRICS_SAMPLE_DATA_DSL)
        .from(Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime()))
        .to(Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()))
        .metricName(healthSourceRecordsRequest.getHealthSourceQueryParams().getHealthSourceMetricName())
        .metricNamespace(healthSourceRecordsRequest.getHealthSourceQueryParams().getHealthSourceMetricNamespace())
        .aggregationType(
            Optional.ofNullable(healthSourceRecordsRequest.getHealthSourceQueryParams().getAggregationType())
                .orElse(AggregationType.AVERAGE)
                .toString())
        .resourceId(healthSourceRecordsRequest.getHealthSourceQueryParams().getIndex())
        .type(DataCollectionRequestType.AZURE_METRICS_SAMPLE_DATA)
        .build();
  }

  @Override
  public List<HealthSourceParamValue> fetchHealthSourceParamValues(
      HealthSourceParamValuesRequest healthSourceParamValuesRequest, ProjectParams projectParams) {
    if (QueryParamsDTO.QueryParamKeys.serviceInstanceField.equals(healthSourceParamValuesRequest.getParamName())) {
      DataCollectionRequest<? extends ConnectorConfigDTO> request =
          AzureServiceInstanceFieldDataRequest.builder()
              .metricNamespace(
                  healthSourceParamValuesRequest.getHealthSourceQueryParams().getHealthSourceMetricNamespace())
              .metricName(healthSourceParamValuesRequest.getHealthSourceQueryParams().getHealthSourceMetricName())
              .resourceId(healthSourceParamValuesRequest.getHealthSourceQueryParams().getIndex())
              .type(DataCollectionRequestType.AZURE_SERVICE_INSTANCE_FIELD_DATA)
              .dsl(MetricPackServiceImpl.AZURE_SERVICE_INSTANCE_FIELD_DSL)
              .build();
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
      List<HashMap<String, String>> dimensions =
          JsonUtils.asList(JsonUtils.asJson(response.getResult()), new TypeReference<>() {});
      return CollectionUtils.emptyIfNull(dimensions)
          .stream()
          .map(dimension
              -> HealthSourceParamValue.builder()
                     .name(dimension.get("localizedValue"))
                     .value(dimension.get("value"))
                     .build())
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }
}
