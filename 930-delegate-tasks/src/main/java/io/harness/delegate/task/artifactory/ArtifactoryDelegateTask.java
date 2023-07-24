/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifactory;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryImagePath;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchBuildsResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchImagePathResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchRepositoriesResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskResponse;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class ArtifactoryDelegateTask extends AbstractDelegateRunnableTask {
  @Inject SecretDecryptionService decryptionService;
  @Inject ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject NGErrorHelper ngErrorHelper;
  @Inject ArtifactoryValidationHandler artifactoryValidationHandler;
  @Inject ArtifactoryNgService artifactoryNgService;

  public ArtifactoryDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final ArtifactoryTaskParams artifactoryTaskParams = (ArtifactoryTaskParams) parameters;
    final ArtifactoryConnectorDTO artifactoryConnectorDTO = artifactoryTaskParams.getArtifactoryConnectorDTO();
    final List<EncryptedDataDetail> encryptedDataDetails = artifactoryTaskParams.getEncryptedDataDetails();
    final ArtifactoryAuthCredentialsDTO credentials = artifactoryConnectorDTO.getAuth().getCredentials();
    decryptionService.decrypt(credentials, encryptedDataDetails);
    final TaskType taskType = artifactoryTaskParams.getTaskType();
    switch (taskType) {
      case VALIDATE:
        return validateArtifactoryConfig(artifactoryConnectorDTO, encryptedDataDetails);
      case FETCH_REPOSITORIES:
        return fetchRepositories(artifactoryConnectorDTO, artifactoryTaskParams.getRepoType());
      case FETCH_BUILDS:
        return fetchFileBuilds(artifactoryTaskParams);
      case FETCH_IMAGE_PATHS:
        return fetchImagePath(artifactoryConnectorDTO, artifactoryTaskParams);
      default:
        throw new InvalidRequestException("No task found for " + taskType.name());
    }
  }

  private DelegateResponseData fetchFileBuilds(ArtifactoryTaskParams params) {
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(params.getArtifactoryConnectorDTO());

    List<BuildDetails> buildDetails = artifactoryNgService.getBuildDetails(
        artifactoryConfigRequest, params.getRepoName(), params.getFilePath(), params.getMaxVersions());

    return ArtifactoryFetchBuildsResponse.builder().commandExecutionStatus(SUCCESS).buildDetails(buildDetails).build();
  }

  private DelegateResponseData fetchRepositories(ArtifactoryConnectorDTO artifactoryConnectorDTO, String repoType) {
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);

    Map<String, String> repositories = artifactoryNgService.getRepositories(artifactoryConfigRequest, repoType);
    return ArtifactoryFetchRepositoriesResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .repositories(repositories)
        .build();
  }
  private DelegateResponseData fetchImagePath(
      ArtifactoryConnectorDTO artifactoryConnectorDTO, ArtifactoryTaskParams artifactoryTaskParams) {
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);

    List<ArtifactoryImagePath> imagePaths =
        artifactoryNgService.getImagePaths(artifactoryConfigRequest, artifactoryTaskParams.getRepoName());
    return ArtifactoryFetchImagePathResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .artifactoryImagePathsFetchDTO(imagePaths)
        .build();
  }

  private DelegateResponseData validateArtifactoryConfig(
      ArtifactoryConnectorDTO artifactoryConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    final ArtifactoryValidationParams artifactoryValidationParams =
        ArtifactoryValidationParams.builder()
            .encryptedDataDetails(encryptedDataDetails)
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .build();
    ConnectorValidationResult connectorValidationResult =
        artifactoryValidationHandler.validate(artifactoryValidationParams, getAccountId());
    connectorValidationResult.setDelegateId(getDelegateId());
    return ArtifactoryTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}