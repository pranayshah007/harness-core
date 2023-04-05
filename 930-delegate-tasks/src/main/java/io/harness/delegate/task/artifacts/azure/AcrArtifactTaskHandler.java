/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_CERT_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.azure.model.AzureConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.AcrRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.AzureClientException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AcrArtifactTaskHandler extends DelegateArtifactTaskHandler<AcrArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  private final AzureAsyncTaskHelper azureAsyncTaskHelper;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(AcrArtifactDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    AzureConfig azureConfig = null;

    try (LazyAutoCloseableWorkingDirectory workingDirectory =
             new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
      azureConfig =
          AcrRequestResponseMapper.toAzureInternalConfig(attributesRequest, secretDecryptionService, workingDirectory);

      if (isRegex(attributesRequest)) {
        lastSuccessfulBuild =
            azureAsyncTaskHelper.getLastSuccessfulBuildFromRegex(azureConfig, attributesRequest.getSubscription(),
                attributesRequest.getRegistry(), attributesRequest.getRepository(), attributesRequest.getTagRegex());
      } else {
        lastSuccessfulBuild = azureAsyncTaskHelper.verifyBuildNumber(azureConfig, attributesRequest.getSubscription(),
            attributesRequest.getRegistry(), attributesRequest.getRepository(), attributesRequest.getTag());
      }

      AcrArtifactDelegateResponse acrArtifactDelegateResponse =
          AcrRequestResponseMapper.toAcrResponse(lastSuccessfulBuild, attributesRequest);

      return getSuccessTaskExecutionResponse(Collections.singletonList(acrArtifactDelegateResponse));
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("There was an issue with creating temp cert file",
          "Possible permissions issues on delegate filesystem",
          new AzureClientException("Issue with using certficate", e));
    }
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(AcrArtifactDelegateRequest attributesRequest) {
    AzureConfig azureConfig = null;

    try (LazyAutoCloseableWorkingDirectory workingDirectory =
             new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
      azureConfig =
          AcrRequestResponseMapper.toAzureInternalConfig(attributesRequest, secretDecryptionService, workingDirectory);

      List<BuildDetailsInternal> builds = azureAsyncTaskHelper.getImageTags(azureConfig,
          attributesRequest.getSubscription(), attributesRequest.getRegistry(), attributesRequest.getRepository());
      List<AcrArtifactDelegateResponse> acrArtifactDelegateResponseList =
          builds.stream()
              .sorted(new BuildDetailsInternalComparatorDescending())
              .map(build -> AcrRequestResponseMapper.toAcrResponse(build, attributesRequest))
              .collect(Collectors.toList());
      return getSuccessTaskExecutionResponse(acrArtifactDelegateResponseList);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("There was an issue with creating temp cert file",
          "Possible permissions issues on delegate filesystem",
          new AzureClientException("Issue with using certficate", e));
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<AcrArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  public void decryptRequestDTOs(AcrArtifactDelegateRequest acrArtifactDelegateRequestRequest) {
    if (acrArtifactDelegateRequestRequest.getAzureConnectorDTO().getCredential().getConfig()
            instanceof AzureManualDetailsDTO) {
      AzureManualDetailsDTO azureManualDetailsDTO =
          (AzureManualDetailsDTO) acrArtifactDelegateRequestRequest.getAzureConnectorDTO().getCredential().getConfig();
      if (azureManualDetailsDTO != null) {
        secretDecryptionService.decrypt(azureManualDetailsDTO.getAuthDTO().getCredentials(),
            acrArtifactDelegateRequestRequest.getEncryptedDataDetails());
      }
    }
  }

  boolean isRegex(AcrArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }
}
