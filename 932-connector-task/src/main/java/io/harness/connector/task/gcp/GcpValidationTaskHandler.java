/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.gcp;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.gcp.GcpValidationParams;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.gcp.client.GcpClient;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
@OwnedBy(CI)
public class GcpValidationTaskHandler implements ConnectorValidationHandler {
  @Inject private GcpClient gcpClient;
  @Inject private DecryptionHelper decryptionHelper;
  @Inject ExceptionManager exceptionManager;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final GcpValidationParams gcpValidationParams = (GcpValidationParams) connectorValidationParams;
      GcpConnectorDTO gcpConnectorDTO = gcpValidationParams.getGcpConnectorDTO();
      GcpConnectorCredentialDTO gcpConnectorCredentialDTO = gcpConnectorDTO.getCredential();
      return validateInternal(gcpConnectorCredentialDTO.getGcpCredentialType(), gcpConnectorCredentialDTO.getConfig(),
          gcpValidationParams.getEncryptionDetails(), gcpConnectorDTO.getExecuteOnDelegate());
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(GcpRequest gcpRequest) {
    GcpCredentialType gcpCredentialType =
        (gcpRequest.getGcpManualDetailsDTO() != null && gcpRequest.getGcpManualDetailsDTO().getSecretKeyRef() != null)
        ? GcpCredentialType.MANUAL_CREDENTIALS
        : GcpCredentialType.INHERIT_FROM_DELEGATE;
    return validateInternal(
        gcpCredentialType, gcpRequest.getGcpManualDetailsDTO(), gcpRequest.getEncryptionDetails(), true);
  }

  private ConnectorValidationResult validateInternal(GcpCredentialType gcpCredentialType,
      GcpCredentialSpecDTO gcpCredentialSpecDTO, List<EncryptedDataDetail> encryptionDetails,
      boolean executeOnDelegate) {
    if (!executeOnDelegate && gcpCredentialType == GcpCredentialType.INHERIT_FROM_DELEGATE) {
      throw new InvalidRequestException(
          format("Connector with credential type %s does not support validation through harness", gcpCredentialType));
    }

    if (gcpCredentialType == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO config =
          validateAndDecryptManualCredential((GcpManualDetailsDTO) gcpCredentialSpecDTO, encryptionDetails);
      gcpClient.getGkeContainerService(config.getSecretKeyRef().getDecryptedValue());
    } else {
      gcpClient.validateDefaultCredentials();
    }

    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(System.currentTimeMillis())
        .build();
  }

  private GcpManualDetailsDTO validateAndDecryptManualCredential(
      GcpManualDetailsDTO gcpManualDetailsDTO, List<EncryptedDataDetail> encryptionDetails) {
    GcpManualDetailsDTO config = (GcpManualDetailsDTO) decryptionHelper.decrypt(gcpManualDetailsDTO, encryptionDetails);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(config, encryptionDetails);
    return config;
  }
}
