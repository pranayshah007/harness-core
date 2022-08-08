/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static software.wings.beans.TaskType.NG_AZURE_TASK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.task.TaskParameters;

@OwnedBy(HarnessTeam.CDP)
public class AzureConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    AzureConnectorDTO connectorDTO = (AzureConnectorDTO) connectorConfig;
    AzureAuthCredentialDTO tmpAzureAuthCredentialDTO = null;
    if (connectorDTO.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
      tmpAzureAuthCredentialDTO =
          ((AzureManualDetailsDTO) connectorDTO.getCredential().getConfig()).getAuthDTO().getCredentials();
    } else {
      AzureMSIAuthDTO azureMSIAuthDTO =
          ((AzureInheritFromDelegateDetailsDTO) connectorDTO.getCredential().getConfig()).getAuthDTO();
      if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
        tmpAzureAuthCredentialDTO = ((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials();
      }
    }

    final AzureAuthCredentialDTO azureAuthCredentialDTO = tmpAzureAuthCredentialDTO;
    return AzureTaskParams.builder()
        .azureTaskType(AzureTaskType.VALIDATE)
        .azureConnector(connectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(azureAuthCredentialDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return NG_AZURE_TASK.name();
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
