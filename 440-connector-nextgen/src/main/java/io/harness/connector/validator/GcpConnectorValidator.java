/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.TaskType.GCP_TASK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.delegate.task.gcp.request.GcpValidationRequest.GcpValidationRequestBuilder;
import io.harness.exception.InvalidRequestException;

@OwnedBy(CDP)
public class GcpConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorConfig;
    GcpConnectorCredentialDTO gcpConnector = gcpConnectorDTO.getCredential();
    final GcpValidationRequestBuilder<?, ?> gcpValidationRequestBuilder = GcpValidationRequest.builder();
    switch (gcpConnector.getGcpCredentialType()) {
      case MANUAL_CREDENTIALS:
        final GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnector.getConfig();
        GcpValidationRequest manualCredentialsRequest =
            gcpValidationRequestBuilder.gcpManualDetailsDTO(gcpManualDetailsDTO)
                .encryptionDetails(
                    super.getEncryptionDetail(gcpManualDetailsDTO, accountIdentifier, orgIdentifier, projectIdentifier))
                .delegateSelectors(gcpConnectorDTO.getDelegateSelectors())
                .build();
        return GcpTaskParameters.builder()
            .gcpRequest(manualCredentialsRequest)
            .gcpTaskType(GcpTaskType.VALIDATE)
            .build();
      case INHERIT_FROM_DELEGATE:
        GcpValidationRequest inheritDelegateRequest =
            gcpValidationRequestBuilder.delegateSelectors(gcpConnectorDTO.getDelegateSelectors()).build();
        return GcpTaskParameters.builder().gcpRequest(inheritDelegateRequest).gcpTaskType(GcpTaskType.VALIDATE).build();
      case OIDC_AUTHENTICATION:
        final GcpOidcDetailsDTO gcpOidcDetailsDTO = (GcpOidcDetailsDTO) gcpConnector.getConfig();
        return GcpTaskParameters.builder()
            .gcpTaskType(GcpTaskType.VALIDATE)
            .gcpRequest(GcpValidationRequest.builder().gcpOidcDetailsDTO(gcpOidcDetailsDTO).build())
            .build();
      default:
        throw new InvalidRequestException("Invalid credential type: " + gcpConnector.getGcpCredentialType());
    }
  }
  @Override
  public String getTaskType() {
    return GCP_TASK.name();
  }
  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return super.validate(connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}
