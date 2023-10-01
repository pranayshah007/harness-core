/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class DockerRequestResponseMapper {
  public DockerInternalConfig toDockerInternalConfig(DockerArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    if (request.getDockerConnectorDTO().getAuth() != null
        && request.getDockerConnectorDTO().getAuth().getCredentials() != null) {
      DockerUserNamePasswordDTO credentials =
          (DockerUserNamePasswordDTO) request.getDockerConnectorDTO().getAuth().getCredentials();
      if (credentials.getPasswordRef() != null) {
        password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
            ? new String(credentials.getPasswordRef().getDecryptedValue())
            : null;
      }
      username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          credentials.getUsername(), credentials.getUsernameRef());
    }
    return DockerInternalConfig.builder()
        .providerType(request.getDockerConnectorDTO().getProviderType())
        .dockerRegistryUrl(request.getDockerConnectorDTO().getDockerRegistryUrl())
        .username(username)
        .password(password)
        .build();
  }

  public DockerArtifactDelegateResponse toDockerResponse(
      BuildDetailsInternal buildDetailsInternal, DockerArtifactDelegateRequest request) {
    return DockerArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal))
        .imagePath(request.getImagePath())
        .tag(buildDetailsInternal.getNumber())
        .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
        .build();
  }

  public DockerArtifactDelegateResponse toDockerResponse(BuildDetailsInternal buildDetailsInternal,
      DockerArtifactDelegateRequest request, ArtifactMetaInfo artifactMetaInfo) {
    String sha = null;
    String shaV2 = null;
    Map<String, String> label = null;

    if (artifactMetaInfo != null) {
      if (artifactMetaInfo.getLabels() != null) {
        label = artifactMetaInfo.getLabels();
      }
      sha = artifactMetaInfo.getSha();
      shaV2 = artifactMetaInfo.getShaV2();
    }

    return DockerArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal, sha, shaV2))
        .imagePath(request.getImagePath())
        .tag(buildDetailsInternal.getNumber())
        .label(label)
        .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
        .build();
  }

  public List<DockerArtifactDelegateResponse> toDockerResponse(
      List<Map<String, String>> labelsList, DockerArtifactDelegateRequest request) {
    return IntStream.range(0, request.getTagsList().size())
        .mapToObj(i
            -> DockerArtifactDelegateResponse.builder()
                   .buildDetails(
                       ArtifactBuildDetailsMapper.toBuildDetailsNG(labelsList.get(i), request.getTagsList().get(i)))
                   .imagePath(request.getImagePath())
                   .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                   .build())
        .collect(Collectors.toList());
  }
}
