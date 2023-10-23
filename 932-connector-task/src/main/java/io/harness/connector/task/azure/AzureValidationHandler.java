/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_CERT_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.model.AzureConfig;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureconnector.AzureValidationParams;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class AzureValidationHandler implements ConnectorValidationHandler {
  @Inject AzureNgConfigMapper azureNgConfigMapper;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject ExceptionManager exceptionManager;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try (LazyAutoCloseableWorkingDirectory authWorkingDirectory =
             new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
      final AzureValidationParams azureValidationParams = (AzureValidationParams) connectorValidationParams;
      AzureConfigContext azureConfigContext = AzureConfigContext.builder()
                                                  .azureConnector(azureValidationParams.getAzureConnectorDTO())
                                                  .encryptedDataDetails(azureValidationParams.getEncryptedDataDetails())
                                                  .certificateWorkingDirectory(authWorkingDirectory)
                                                  .build();
      return validateInternal(azureConfigContext);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(AzureConfigContext azureConfigContext) throws IOException {
    return validateInternal(azureConfigContext);
  }

  private ConnectorValidationResult validateInternal(AzureConfigContext azureConfigContext) throws IOException {
    AzureConfig azureConfig = azureNgConfigMapper.mapAzureConfigWithDecryption(azureConfigContext.getAzureConnector(),
        azureConfigContext.getEncryptedDataDetails(), azureConfigContext.getCertificateWorkingDirectory());
    if (azureAuthorizationClient.validateAzureConnection(azureConfig)) {
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    }

    String errorMessage = "Testing connection to Azure has timed out.";
    throw NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure ConnectorDisconnectHandler",
        "Please check you Azure ConnectorDisconnectHandler configuration.", new AzureAuthenticationException(errorMessage));
  }
}
