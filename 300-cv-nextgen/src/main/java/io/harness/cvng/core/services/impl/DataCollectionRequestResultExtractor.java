/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.serializer.JsonUtils;

import com.google.gson.Gson;
import java.lang.reflect.Type;
import org.slf4j.Logger;

public interface DataCollectionRequestResultExtractor<ConnectorConfig extends ConnectorConfigDTO> {
  default<T> T performRequestAndGetDataResult(DataCollectionRequest<ConnectorConfig> dataCollectionRequest,
      OnboardingService onboardingService, Type type, ProjectParams projectParams, String connectorIdentifier,
      String tracingId, Logger logger) {
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(dataCollectionRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .tracingId(tracingId)
                                                    .build();
    logger.info("Triggering {} onboarding request. TracingId: {}", dataCollectionRequest.getType(), tracingId);
    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    logger.info("{} onboarding request was successful. Tracing id: {}", dataCollectionRequest.getType(), tracingId);
    return new Gson().fromJson(JsonUtils.asJson(response.getResult()), type);
  }
}
