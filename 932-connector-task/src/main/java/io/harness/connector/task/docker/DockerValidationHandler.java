package io.harness.connector.task.docker;

import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.docker.DockerValidationParams;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.errorhandling.NGErrorHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class DockerValidationHandler implements ConnectorValidationHandler {
  @Inject private DockerArtifactTaskHelper dockerArtifactTaskHelper;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final DockerValidationParams dockerValidationParams = (DockerValidationParams) connectorValidationParams;

    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder()
            .accountId(accountIdentifier)
            .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
            .attributes(DockerArtifactDelegateRequest.builder()
                            .dockerConnectorDTO(dockerValidationParams.getDockerConnectorDTO())
                            .encryptedDataDetails(dockerValidationParams.getEncryptionDataDetails())
                            .build())
            .build();

    ArtifactTaskResponse validationResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    boolean isDockerCredentialsValid = false;
    if (validationResponse.getArtifactTaskExecutionResponse() != null) {
      isDockerCredentialsValid = validationResponse.getArtifactTaskExecutionResponse().isArtifactServerValid();
    }

    ConnectorValidationResultBuilder validationResultBuilder = ConnectorValidationResult.builder();

    validationResultBuilder.status(isDockerCredentialsValid ? ConnectivityStatus.SUCCESS : ConnectivityStatus.FAILURE);
    if (!isDockerCredentialsValid) {
      String errorMessage = validationResponse.getErrorMessage();
      validationResultBuilder.errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)));
    }

    return validationResultBuilder.build();
  }
}
