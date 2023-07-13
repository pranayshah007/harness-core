/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.AzureArtifactsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AzureArtifactsTaskHandler extends DelegateArtifactTaskHandler<AzureArtifactsDelegateRequest> {
  private final AzureArtifactsRegistryService azureArtifactsRegistryService;
  private final SecretDecryptionService secretDecryptionService;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    boolean isServerValidated = azureArtifactsRegistryService.validateCredentials(azureArtifactsInternalConfig);

    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    List<BuildDetails> builds = azureArtifactsRegistryService.listPackageVersions(azureArtifactsInternalConfig,
        attributesRequest.getPackageType(), attributesRequest.getPackageName(), attributesRequest.getVersionRegex(),
        attributesRequest.getFeed(), attributesRequest.getProject());

    String versionRegex = attributesRequest.getVersionRegex();
    if (versionRegex != null) {
      Pattern pattern = Pattern.compile(versionRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
      builds = builds.stream()
                   .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
                   .collect(Collectors.toList());
    }

    List<AzureArtifactsDelegateResponse> azureArtifactsDelegateResponseList = new ArrayList<>();

    for (BuildDetails b : builds) {
      AzureArtifactsDelegateResponse artifactDelegateResponse = AzureArtifactsDelegateResponse.builder()
                                                                    .version(b.getNumber())
                                                                    .sourceType(attributesRequest.getSourceType())
                                                                    .build();

      azureArtifactsDelegateResponseList.add(artifactDelegateResponse);
    }

    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(azureArtifactsDelegateResponseList)
        .buildDetails(builds)
        .build();
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(AzureArtifactsDelegateRequest attributesRequest) {
    BuildDetails lastSuccessfulBuild;

    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = azureArtifactsRegistryService.getLastSuccessfulBuildFromRegex(azureArtifactsInternalConfig,
          attributesRequest.getPackageType(), attributesRequest.getPackageName(), attributesRequest.getVersionRegex(),
          attributesRequest.getFeed(), attributesRequest.getProject(), attributesRequest.getScope());

    } else {
      lastSuccessfulBuild = azureArtifactsRegistryService.getBuild(azureArtifactsInternalConfig,
          attributesRequest.getPackageType(), attributesRequest.getPackageName(), attributesRequest.getVersion(),
          attributesRequest.getFeed(), attributesRequest.getProject());
    }

    AzureArtifactsDelegateResponse azureArtifactsDelegateResponse =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsResponse(lastSuccessfulBuild, attributesRequest);

    return getSuccessTaskExecutionResponse(
        Collections.singletonList(azureArtifactsDelegateResponse), Collections.singletonList(lastSuccessfulBuild));
  }

  public ArtifactTaskExecutionResponse getAzureArtifactsProjects(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    List<AzureDevopsProject> azureArtifactsProjects =
        azureArtifactsRegistryService.listProjects(azureArtifactsInternalConfig);

    return ArtifactTaskExecutionResponse.builder().azureArtifactsProjects(azureArtifactsProjects).build();
  }

  public ArtifactTaskExecutionResponse getAzureArtifactsPackages(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    List<AzureArtifactsPackage> azureArtifactsPackages =
        azureArtifactsRegistryService.listPackages(azureArtifactsInternalConfig, attributesRequest.getProject(),
            attributesRequest.getFeed(), attributesRequest.getPackageType());

    return ArtifactTaskExecutionResponse.builder().azureArtifactsPackages(azureArtifactsPackages).build();
  }

  public ArtifactTaskExecutionResponse getAzureArtifactsFeeds(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsRequestResponseMapper.toAzureArtifactsInternalConfig(attributesRequest);

    List<AzureArtifactsFeed> azureArtifactsFeeds =
        azureArtifactsRegistryService.listFeeds(azureArtifactsInternalConfig, attributesRequest.getProject());

    return ArtifactTaskExecutionResponse.builder().azureArtifactsFeeds(azureArtifactsFeeds).build();
  }

  @Override
  public void decryptRequestDTOs(AzureArtifactsDelegateRequest attributesRequest) {
    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO = attributesRequest.getAzureArtifactsConnectorDTO();

    if (azureArtifactsConnectorDTO.getAuth() != null) {
      AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO = azureArtifactsConnectorDTO.getAuth().getCredentials();

      if (azureArtifactsCredentialsDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
        AzureArtifactsTokenDTO usernameTokenDTO = azureArtifactsCredentialsDTO.getCredentialsSpec();

        secretDecryptionService.decrypt(usernameTokenDTO, attributesRequest.getEncryptedDataDetails());
      }
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<AzureArtifactsDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(AzureArtifactsDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getVersionRegex());
  }
}
