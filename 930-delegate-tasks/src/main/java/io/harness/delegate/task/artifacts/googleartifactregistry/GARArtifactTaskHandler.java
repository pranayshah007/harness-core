/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googleartifactregistry;
import static io.harness.delegate.task.artifacts.mappers.GarRequestResponseMapper.toGarInternalConfig;
import static io.harness.delegate.task.artifacts.mappers.GarRequestResponseMapper.toGarResponse;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GARArtifactTaskHandler extends DelegateArtifactTaskHandler<GarDelegateRequest> {
  private final GarApiService garApiService;
  private final GcpHelperService gcpHelperService;
  private final SecretDecryptionService secretDecryptionService;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(GarDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    GarInternalConfig garInternalConfig;
    try {
      garInternalConfig = getGarInternalConfig(attributesRequest);
    } catch (IOException e) {
      log.error("Google Artifact Registry: Could not get BearerToken", e);
      throw NestedExceptionUtils.hintWithExplanationException("Google Artifact Registry: Could not get Bearer Token",
          "Refresh Token might be not getting generated", new InvalidArtifactServerException(e.getMessage(), USER));
    }

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild =
          garApiService.getLastSuccessfulBuildFromRegex(garInternalConfig, attributesRequest.getVersionRegex());
    } else {
      lastSuccessfulBuild = garApiService.verifyBuildNumber(garInternalConfig, attributesRequest.getVersion());
    }
    GarDelegateResponse garDelegateResponse = toGarResponse(lastSuccessfulBuild, attributesRequest);
    return getSuccessTaskExecutionResponse(Collections.singletonList(garDelegateResponse));
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(GarDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds;
    GarInternalConfig garInternalConfig;
    try {
      garInternalConfig = getGarInternalConfig(attributesRequest);
    } catch (IOException e) {
      log.error("Could not get Bearer Token", e);
      throw NestedExceptionUtils.hintWithExplanationException("Google Artifact Registry: Could not get Bearer Token",
          "", new InvalidArtifactServerException(e.getMessage(), USER));
    }
    builds = garApiService.getBuilds(
        garInternalConfig, attributesRequest.getVersionRegex(), attributesRequest.getMaxBuilds());
    List<GarDelegateResponse> garArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> toGarResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(garArtifactDelegateResponseList);
  }
  private GarInternalConfig getGarInternalConfig(GarDelegateRequest attributesRequest) throws IOException {
    char[] serviceAccountKeyFileContent = new char[0];
    boolean isUseDelegate = false;

    if (attributesRequest.getGcpConnectorDTO() != null) {
      GcpConnectorCredentialDTO credential = attributesRequest.getGcpConnectorDTO().getCredential();
      if (credential.getGcpCredentialType() == GcpCredentialType.INHERIT_FROM_DELEGATE) {
        isUseDelegate = true;
      } else {
        SecretRefData secretRef = ((GcpManualDetailsDTO) credential.getConfig()).getSecretKeyRef();
        if (secretRef.getDecryptedValue() == null) {
          throw new SecretNotFoundRuntimeException("Google Artifact Registry: Could not find secret "
                  + secretRef.getIdentifier() + " under the scope of current " + secretRef.getScope(),
              secretRef.getIdentifier(), secretRef.getScope().toString(), attributesRequest.getConnectorRef());
        }
        serviceAccountKeyFileContent = secretRef.getDecryptedValue();
      }
    }

    String token = getToken(serviceAccountKeyFileContent, isUseDelegate);
    return toGarInternalConfig(attributesRequest, "Bearer " + token);
  }
  public String getToken(char[] serviceAccountKeyFileContent, boolean isUseDelegate) throws IOException {
    GoogleCredential gc = gcpHelperService.getGoogleCredential(serviceAccountKeyFileContent, isUseDelegate);
    gc.refreshToken();
    return gc.getAccessToken();
  }
  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(List<GarDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }
  public void decryptRequestDTOs(GarDelegateRequest garDelegateRequest) {
    if (garDelegateRequest.getGcpConnectorDTO().getCredential() != null
        && garDelegateRequest.getGcpConnectorDTO().getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(garDelegateRequest.getGcpConnectorDTO().getCredential().getConfig(),
          garDelegateRequest.getEncryptedDataDetails());
    }
  }
  boolean isRegex(GarDelegateRequest artifactDelegateRequest) {
    return StringUtils.isBlank(artifactDelegateRequest.getVersion());
  }
}
