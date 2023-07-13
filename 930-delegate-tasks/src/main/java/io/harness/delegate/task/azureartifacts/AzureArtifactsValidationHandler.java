/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azureartifacts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsValidationParams;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsTaskHelper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.errorhandling.NGErrorHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
public class AzureArtifactsValidationHandler implements ConnectorValidationHandler {
  @Inject private AzureArtifactsTaskHelper azureArtifactsArtifactTaskHelper;

  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final AzureArtifactsValidationParams azureArtifactsValidationParams =
        (AzureArtifactsValidationParams) connectorValidationParams;

    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder()
            .accountId(accountIdentifier)
            .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
            .attributes(AzureArtifactsDelegateRequest.builder()
                            .azureArtifactsConnectorDTO(azureArtifactsValidationParams.getAzureArtifactsConnectorDTO())
                            .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                            .registryUrl(azureArtifactsValidationParams.getRegistryUrl())
                            .encryptedDataDetails(azureArtifactsValidationParams.getEncryptionDataDetails())
                            .build())
            .build();

    ArtifactTaskResponse validationResponse =
        azureArtifactsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    boolean isAzureArtifactsCredentialsValid = false;

    if (validationResponse.getArtifactTaskExecutionResponse() != null) {
      isAzureArtifactsCredentialsValid = validationResponse.getArtifactTaskExecutionResponse().isArtifactServerValid();
    }

    ConnectorValidationResultBuilder validationResultBuilder = ConnectorValidationResult.builder();

    validationResultBuilder.status(
        isAzureArtifactsCredentialsValid ? ConnectivityStatus.SUCCESS : ConnectivityStatus.FAILURE);

    if (!isAzureArtifactsCredentialsValid) {
      String errorMessage = validationResponse.getErrorMessage();

      validationResultBuilder.errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)));
    }

    return validationResultBuilder.build();
  }
}
