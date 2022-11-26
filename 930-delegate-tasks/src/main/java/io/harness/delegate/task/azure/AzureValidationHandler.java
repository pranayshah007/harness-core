/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_CERT_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureconnector.AzureValidationParams;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AzureValidationHandler implements ConnectorValidationHandler {
  @Inject private AzureAsyncTaskHelper azureAsyncTaskHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final AzureValidationParams azureValidationParams = (AzureValidationParams) connectorValidationParams;
    try (LazyAutoCloseableWorkingDirectory workingDirectory =
             new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
      AzureConfigContext azureConfigContext = AzureConfigContext.builder()
                                                  .azureConnector(azureValidationParams.getAzureConnectorDTO())
                                                  .encryptedDataDetails(azureValidationParams.getEncryptedDataDetails())
                                                  .certificateWorkingDirectory(workingDirectory)
                                                  .build();
      return azureAsyncTaskHelper.getConnectorValidationResult(azureConfigContext);
    }
  }
}
