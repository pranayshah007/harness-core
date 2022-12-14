/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudstorage;

import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.GoogleCloudStorageRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlecloudstorage.GcsHelperService;
import io.harness.googlecloudstorage.GcsInternalConfig;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class GoogleCloudStorageArtifactTaskHandler
    extends DelegateArtifactTaskHandler<GoogleCloudStorageArtifactDelegateRequest> {
  @Inject private GcsHelperService gcsHelperService;
  private final SecretDecryptionService secretDecryptionService;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(
      GoogleCloudStorageArtifactDelegateRequest artifactDelegateRequest) {
    if (StringUtils.isBlank(artifactDelegateRequest.getProject())) {
      throw new InvalidRequestException("Please specify the project for the GCS artifact source.");
    }
    if (StringUtils.isBlank(artifactDelegateRequest.getBucket())) {
      throw new InvalidRequestException("Please specify the bucket for the GCS artifact source.");
    }
    if (StringUtils.isBlank(artifactDelegateRequest.getArtifactPath())) {
      throw new InvalidRequestException("Please specify the artifact path for the GCS artifact source.");
    }
    GcsInternalConfig gcsInternalConfig = getGcsInternalConfig(artifactDelegateRequest);

    List<BuildDetails> builds = gcsHelperService.listBuilds(gcsInternalConfig);
    if (builds.isEmpty()) {
      throw new InvalidRequestException("No last successful build");
    }
    BuildDetails expectedBuildDetail = new BuildDetails();
    for (BuildDetails buildDetail : builds) {
      if (artifactDelegateRequest.getArtifactPath().equals(buildDetail.getArtifactPath())) {
        expectedBuildDetail = buildDetail;
        break;
      }
    }
    if (!artifactDelegateRequest.getArtifactPath().equals(expectedBuildDetail.getArtifactPath())) {
      throw new InvalidRequestException("No build exist for the given file path.");
    }
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponse(GoogleCloudStorageRequestResponseMapper.toGoogleCloudStorageResponse(
            expectedBuildDetail, artifactDelegateRequest))
        .build();
  }

  @Override
  public void decryptRequestDTOs(GoogleCloudStorageArtifactDelegateRequest artifactDelegateRequest) {
    if (artifactDelegateRequest.getGcpConnectorDTO() != null
        && artifactDelegateRequest.getGcpConnectorDTO().getCredential() != null
        && artifactDelegateRequest.getGcpConnectorDTO().getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(artifactDelegateRequest.getGcpConnectorDTO().getCredential().getConfig(),
          artifactDelegateRequest.getEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          artifactDelegateRequest.getGcpConnectorDTO().getCredential().getConfig(),
          artifactDelegateRequest.getEncryptedDataDetails());
    }
  }

  private GcsInternalConfig getGcsInternalConfig(GoogleCloudStorageArtifactDelegateRequest artifactDelegateRequest) {
    if (artifactDelegateRequest.getGcpConnectorDTO() == null) {
      throw new InvalidArgumentsException("GCP Connector cannot be null");
    }
    boolean isUseDelegate = false;
    char[] serviceAccountKeyFileContent = new char[0];
    GcpConnectorCredentialDTO credential = artifactDelegateRequest.getGcpConnectorDTO().getCredential();
    if (credential == null) {
      throw new InvalidArgumentsException("GCP Connector credential cannot be null");
    }
    if (INHERIT_FROM_DELEGATE == credential.getGcpCredentialType()) {
      isUseDelegate = true;
    } else {
      SecretRefData secretRef = ((GcpManualDetailsDTO) credential.getConfig()).getSecretKeyRef();
      if (secretRef.getDecryptedValue() == null) {
        throw new SecretNotFoundRuntimeException("Could not find secret " + secretRef.getIdentifier()
                + " under the scope of current " + secretRef.getScope(),
            secretRef.getIdentifier(), secretRef.getScope().toString(), artifactDelegateRequest.getConnectorRef());
      }
      serviceAccountKeyFileContent = secretRef.getDecryptedValue();
    }
    return GcsInternalConfig.builder()
        .serviceAccountKeyFileContent(serviceAccountKeyFileContent)
        .isUseDelegate(isUseDelegate)
        .bucket(artifactDelegateRequest.getBucket())
        .project(artifactDelegateRequest.getProject())
        .build();
  }
}
