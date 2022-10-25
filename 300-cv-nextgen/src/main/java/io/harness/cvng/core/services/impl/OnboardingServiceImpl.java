/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.services.api.DataSourceConnectivityChecker;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class OnboardingServiceImpl implements OnboardingService {
  @Inject private VerificationManagerService verificationManagerService;
  @Inject private NextGenService nextGenService;
  @Inject private Map<DataSourceType, DataSourceConnectivityChecker> dataSourceTypeToServiceMapBinder;

  @Override
  public OnboardingResponseDTO getOnboardingResponse(String accountId, OnboardingRequestDTO onboardingRequestDTO) {
    Preconditions.checkNotNull(onboardingRequestDTO, "OnboardingRequestDTO cannot be null");
    Preconditions.checkNotNull(onboardingRequestDTO.getTracingId(), "Missing tracingId/requestGuid in request");

    ConnectorInfoDTO connectorInfoDTO = getConnectorConfigDTO(accountId, onboardingRequestDTO.getConnectorIdentifier(),
        onboardingRequestDTO.getOrgIdentifier(), onboardingRequestDTO.getProjectIdentifier());
    onboardingRequestDTO.getDataCollectionRequest().setConnectorInfoDTO(connectorInfoDTO);
    onboardingRequestDTO.getDataCollectionRequest().setTracingId(onboardingRequestDTO.getTracingId());
    String response =
        verificationManagerService.getDataCollectionResponse(accountId, onboardingRequestDTO.getOrgIdentifier(),
            onboardingRequestDTO.getProjectIdentifier(), onboardingRequestDTO.getDataCollectionRequest());
    log.debug(response);
    return OnboardingResponseDTO.builder()
        .accountId(accountId)
        .connectorIdentifier(onboardingRequestDTO.getConnectorIdentifier())
        .orgIdentifier(onboardingRequestDTO.getOrgIdentifier())
        .projectIdentifier(onboardingRequestDTO.getProjectIdentifier())
        .tracingId(onboardingRequestDTO.getTracingId())
        .result(JsonUtils.asObject(response, Object.class))
        .build();
  }

  @Override
  public void checkConnectivity(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String tracingId, DataSourceType dataSourceType) {
    Preconditions.checkNotNull(dataSourceType);
    Preconditions.checkNotNull(dataSourceTypeToServiceMapBinder.containsKey(dataSourceType));
    dataSourceTypeToServiceMapBinder.get(dataSourceType)
        .checkConnectivity(accountId, orgIdentifier, projectIdentifier, connectorIdentifier, tracingId);
  }

  private ConnectorInfoDTO getConnectorConfigDTO(
      String accountId, String connectorIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, connectorIdentifier, orgIdentifier, projectIdentifier);
    Preconditions.checkState(connectorDTO.isPresent(), "ConnectorDTO should not be null");
    return connectorDTO.get();
  }
}
