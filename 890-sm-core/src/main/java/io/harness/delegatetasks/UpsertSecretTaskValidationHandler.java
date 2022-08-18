/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultValidationParams;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultValidationParams;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.SecretManagerConfigDTOMapper;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.VaultConfig;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

@OwnedBy(PL)
public class UpsertSecretTaskValidationHandler implements ConnectorValidationHandler {
  @Inject VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Inject NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    long currentTime = System.currentTimeMillis();
    UpsertSecretTaskResponse upsertSecretTaskResponse;
    try {
      UpsertSecretTaskParameters upsertSecretTaskParameters =
          getTaskParams(connectorValidationParams, accountIdentifier);
      upsertSecretTaskResponse = UpsertSecretTask.run(upsertSecretTaskParameters, vaultEncryptorsRegistry);
    } catch (Exception exception) {
      String errorMessage = exception.getMessage();
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .testedAt(currentTime)
          .errorSummary(ngErrorHelper.createErrorSummary("Invalid Credentials", errorMessage))
          .errors(getErrorDetail(errorMessage))
          .build();
    }

    if (upsertSecretTaskResponse.getEncryptedRecord() != null) {
      return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).testedAt(currentTime).build();
    } else {
      return ConnectorValidationResult.builder().status(ConnectivityStatus.FAILURE).testedAt(currentTime).build();
    }
  }

  private UpsertSecretTaskParameters getTaskParams(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    if (ConnectorType.VAULT.equals(connectorValidationParams.getConnectorType())) {
      VaultConnectorDTO vaultConnectorDTO = ((VaultValidationParams) connectorValidationParams).getVaultConnectorDTO();
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().connectorConfig(vaultConnectorDTO).connectorType(ConnectorType.VAULT).build();
      SecretManagerConfigDTO secretManagerConfigDTO = SecretManagerConfigDTOMapper.fromConnectorDTO(
          accountIdentifier, ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), vaultConnectorDTO);
      return UpsertSecretTaskParameters.builder()
          .encryptionConfig(SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO))
          .taskType(UpsertSecretTaskType.CREATE)
          .name(VaultConfig.VAULT_VAILDATION_URL)
          .plaintext(Boolean.TRUE.toString())
          .existingRecord(null)
          .build();
    } else if (ConnectorType.AZURE_KEY_VAULT.equals(connectorValidationParams.getConnectorType())) {
      AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO =
          ((AzureKeyVaultValidationParams) connectorValidationParams).getAzurekeyvaultConnectorDTO();
      ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                              .connectorConfig(azureKeyVaultConnectorDTO)
                                              .connectorType(ConnectorType.AZURE_KEY_VAULT)
                                              .build();
      SecretManagerConfigDTO secretManagerConfigDTO = SecretManagerConfigDTOMapper.fromConnectorDTO(
          accountIdentifier, ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), azureKeyVaultConnectorDTO);
      return UpsertSecretTaskParameters.builder()
          .encryptionConfig(SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO))
          .taskType(UpsertSecretTaskType.CREATE)
          .name(AzureVaultConfig.AZURE_VAULT_VALIDATION_URL)
          .plaintext(Boolean.TRUE.toString())
          .existingRecord(null)
          .build();
    }
    throw new SecretManagementDelegateException(
        SECRET_MANAGEMENT_ERROR, "Secret Manager not supported for upsert secret task type.", USER);
  }

  private List<ErrorDetail> getErrorDetail(String errorMessage) {
    return Collections.singletonList(
        ErrorDetail.builder().message(errorMessage).code(450).reason("Invalid Credentials").build());
  }
}
