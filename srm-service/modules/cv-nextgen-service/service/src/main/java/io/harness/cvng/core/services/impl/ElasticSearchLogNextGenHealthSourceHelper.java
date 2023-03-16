/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.elk.ELKIndexCollectionRequest;
import io.harness.cvng.beans.elk.ELKSampleDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValue;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamValuesRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.health.HealthService;
import io.harness.ng.core.CorrelationContext;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearchLogNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Inject private OnboardingService onboardingService;

  @Inject private HealthService healthService;

  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    return ELKSampleDataCollectionRequest.builder()
        .query(healthSourceRecordsRequest.getQuery())
        .index(healthSourceRecordsRequest.getHealthSourceQueryParams().getIndex())
        .build();
  }

  @Override
  public List<HealthSourceParamValue> fetchHealthSourceParamValues(
      HealthSourceParamValuesRequest healthSourceParamValuesRequest, ProjectParams projectParams) {
    if (QueryParamsDTO.QueryParamKeys.index.equals(healthSourceParamValuesRequest.getParamName())) {
      DataCollectionRequest<? extends ConnectorConfigDTO> request = ELKIndexCollectionRequest.builder().build();
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
      return CollectionUtils.emptyIfNull(indices)
          .stream()
          .map(index -> HealthSourceParamValue.builder().name(index).value(index).build())
          .collect(Collectors.toList());
    } else if (QueryParamsDTO.QueryParamKeys.timeStampFormat.equals(healthSourceParamValuesRequest.getParamName())) {
      return healthService.getTimeStampFormats()
          .stream()
          .map(timeStampFormat -> HealthSourceParamValue.builder().name(timeStampFormat).value(timeStampFormat).build())
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }
}
