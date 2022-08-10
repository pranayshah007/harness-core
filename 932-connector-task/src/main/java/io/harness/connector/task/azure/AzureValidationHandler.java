/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.model.AzureConfig;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

@OwnedBy(HarnessTeam.CI)
public class AzureValidationHandler implements ConnectorValidationHandler {
  @Inject AzureNgConfigMapper azureNgConfigMapper;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final AzureValidationParams azureValidationParams = (AzureValidationParams) connectorValidationParams;
    final AzureConnectorDTO azureConnectorDTO = azureValidationParams.getAzureConnectorDTO();
    final List<EncryptedDataDetail> encryptedDataDetails = azureValidationParams.getEncryptedDataDetails();
    return validateInternal(azureConnectorDTO, encryptedDataDetails);
  }

  public ConnectorValidationResult validate(AzureTaskParams azureTaskParams) {
    final AzureConnectorDTO azureConnectorDTO = azureTaskParams.getAzureConnector();
    final List<EncryptedDataDetail> encryptedDataDetails = azureTaskParams.getEncryptionDetails();
    return validateInternal(azureConnectorDTO, encryptedDataDetails);
  }

  private ConnectorValidationResult validateInternal(
      AzureConnectorDTO azureConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    try {
      AzureConfig azureConfig =
          azureNgConfigMapper.mapAzureConfigWithDecryption(azureConnectorDTO, encryptedDataDetails);
      if (!azureAuthorizationClient.validateAzureConnection(azureConfig)) {
        throw new TimeoutException("Testing connection to Azure has timed out.");
      }
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.FAILURE)
                                      .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)))
                                      .errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    }
    return connectorValidationResult;
  }
}
