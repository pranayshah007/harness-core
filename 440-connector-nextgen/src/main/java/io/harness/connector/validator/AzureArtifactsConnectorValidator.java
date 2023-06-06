/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static software.wings.beans.TaskType.AZURE_ARTIFACTS_CONNECTIVITY_TEST_TASK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTestConnectionTaskParams;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
public class AzureArtifactsConnectorValidator extends AbstractConnectorValidator {
  @Inject private SecretManagerClientService secretManagerClientService;

  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO = (AzureArtifactsConnectorDTO) connectorConfig;

    BaseNGAccess baseNGAccess = getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptedDataDetails =
        getAzureArtifactEncryptionDetails(azureArtifactsConnectorDTO, baseNGAccess);

    return AzureArtifactsTestConnectionTaskParams.builder()
        .azureArtifactsConnector(azureArtifactsConnectorDTO)
        .registryUrl(azureArtifactsConnectorDTO.getAzureArtifactsUrl())
        .encryptionDetails(encryptedDataDetails)
        .build();
  }

  private List<EncryptedDataDetail> getAzureArtifactEncryptionDetails(
      @Nonnull AzureArtifactsConnectorDTO azureArtifactsConnectorDTO, @Nonnull NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO = azureArtifactsConnectorDTO.getAuth();

    AzureArtifactsCredentialsDTO credentialsDTO = azureArtifactsAuthenticationDTO.getCredentials();

    AzureArtifactsAuthenticationType azureArtifactsAuthenticationType = credentialsDTO.getType();

    if (azureArtifactsAuthenticationType == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
      AzureArtifactsTokenDTO tokenDTO = credentialsDTO.getCredentialsSpec();

      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, tokenDTO);

    } else {
      throw new InvalidRequestException("Please select the authentication type as Token");
    }

    return encryptedDataDetails;
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  @Override
  public String getTaskType() {
    return AZURE_ARTIFACTS_CONNECTIVITY_TEST_TASK.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO azureArtifactsConnector, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseData = super.validateConnector(
        azureArtifactsConnector, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return responseData.getConnectorValidationResult();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}
