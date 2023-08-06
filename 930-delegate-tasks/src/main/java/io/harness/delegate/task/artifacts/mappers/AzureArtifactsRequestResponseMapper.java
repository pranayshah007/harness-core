/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class AzureArtifactsRequestResponseMapper {
  public AzureArtifactsInternalConfig toAzureArtifactsInternalConfig(AzureArtifactsDelegateRequest request) {
    String password = "";

    String username = "";

    String token = "";

    String authMechanism = "";

    if (request.getAzureArtifactsConnectorDTO().getAuth() != null
        && request.getAzureArtifactsConnectorDTO().getAuth().getCredentials() != null) {
      AzureArtifactsCredentialsDTO httpDTO = request.getAzureArtifactsConnectorDTO().getAuth().getCredentials();

      if (httpDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
        authMechanism = AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN.getDisplayName();

        AzureArtifactsTokenDTO usernameTokenDTO = httpDTO.getCredentialsSpec();

        token = new String(usernameTokenDTO.getTokenRef().getDecryptedValue());
      }
    }

    return AzureArtifactsInternalConfig.builder()
        .authMechanism(authMechanism)
        .registryUrl(request.getAzureArtifactsConnectorDTO().getAzureArtifactsUrl())
        .packageId(request.getPackageId())
        .username(username)
        .password(password)
        .token(token)
        .build();
  }

  public AzureArtifactsInternalConfig toAzureArtifactsInternalConfig(
      AzureArtifactsConnectorDTO azureArtifactsConnectorDTO) {
    String token = null;
    String authMechanism = null;

    if (azureArtifactsConnectorDTO.getAuth() != null && azureArtifactsConnectorDTO.getAuth().getCredentials() != null) {
      AzureArtifactsCredentialsDTO httpDTO = azureArtifactsConnectorDTO.getAuth().getCredentials();

      if (httpDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
        authMechanism = AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN.getDisplayName();
        AzureArtifactsTokenDTO usernameTokenDTO = httpDTO.getCredentialsSpec();
        token = new String(usernameTokenDTO.getTokenRef().getDecryptedValue());
      }
    }

    return AzureArtifactsInternalConfig.builder()
        .authMechanism(authMechanism)
        .registryUrl(azureArtifactsConnectorDTO.getAzureArtifactsUrl())
        .token(token)
        .build();
  }

  public static AzureArtifactsDelegateResponse toAzureArtifactsResponse(
      BuildDetails lastSuccessfulBuild, AzureArtifactsDelegateRequest attributesRequest) {
    return AzureArtifactsDelegateResponse.builder()
        .packageType(attributesRequest.getPackageType())
        .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
        .version(lastSuccessfulBuild.getNumber())
        .versionRegex(attributesRequest.getVersionRegex())
        .feed(attributesRequest.getFeed())
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(lastSuccessfulBuild))
        .packageName(attributesRequest.getPackageName())
        .build();
  }
}
