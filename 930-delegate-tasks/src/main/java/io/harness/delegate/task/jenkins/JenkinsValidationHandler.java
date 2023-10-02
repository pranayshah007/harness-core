/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jenkins;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.jenkins.JenkinsValidationParams;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactTaskHelper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.errorhandling.NGErrorHelper;

import com.google.inject.Inject;
import java.util.Collections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class JenkinsValidationHandler implements ConnectorValidationHandler {
  @Inject private JenkinsArtifactTaskHelper jenkinsArtifactTaskHelper;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final JenkinsValidationParams jenkinsValidationParams = (JenkinsValidationParams) connectorValidationParams;
    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder()
            .accountId(accountIdentifier)
            .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
            .attributes(JenkinsArtifactDelegateRequest.builder()
                            .jenkinsConnectorDTO(jenkinsValidationParams.getJenkinsConnectorDTO())
                            .encryptedDataDetails(jenkinsValidationParams.getEncryptionDataDetails())
                            .build())
            .build();
    ArtifactTaskResponse validationResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    boolean isJenkinsCredentialsValid = false;
    ConnectorValidationResultBuilder validationResultBuilder = ConnectorValidationResult.builder();
    if (validationResponse.getArtifactTaskExecutionResponse() != null) {
      isJenkinsCredentialsValid = validationResponse.getArtifactTaskExecutionResponse().isArtifactServerValid();
    }
    validationResultBuilder.status(isJenkinsCredentialsValid ? ConnectivityStatus.SUCCESS : ConnectivityStatus.FAILURE);
    if (!isJenkinsCredentialsValid) {
      String errorMessage = validationResponse.getErrorMessage();
      validationResultBuilder.errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)));
    }
    return validationResultBuilder.build();
  }
}
