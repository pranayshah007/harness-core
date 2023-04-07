/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidRequestException;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GithubPackagesRequestResponseMapper {
  public GithubPackagesInternalConfig toGithubPackagesInternalConfig(GithubPackagesArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    String token = "";

    if (request.getGithubConnectorDTO().getAuthentication() != null
        && request.getGithubConnectorDTO().getAuthentication().getCredentials() != null) {
      if (request.getGithubConnectorDTO().getAuthentication().getAuthType() == GitAuthType.HTTP) {
        GithubHttpCredentialsDTO httpDTO =
            (GithubHttpCredentialsDTO) request.getGithubConnectorDTO().getAuthentication().getCredentials();

        if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
          GithubUsernamePasswordDTO githubUsernamePasswordDTO =
              (GithubUsernamePasswordDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernamePasswordDTO.getUsername(), githubUsernamePasswordDTO.getUsernameRef());

        } else if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
          GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());
        }
      }
    }

    GithubApiAccessDTO githubApiAccessDTO = request.getGithubConnectorDTO().getApiAccess();

    if (githubApiAccessDTO == null) {
      throw new InvalidRequestException("Please enable the API Access for the Github Connector");
    }

    GithubApiAccessType githubApiAccessType = githubApiAccessDTO.getType();

    if (githubApiAccessType == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      if (githubTokenSpecDTO.getTokenRef() != null) {
        token = EmptyPredicate.isNotEmpty(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            ? new String(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            : null;

      } else {
        throw new InvalidRequestException("The token reference for the Github Connector is null");
      }

    } else {
      throw new InvalidRequestException("Please select the API Access auth type to Token");
    }

    return GithubPackagesInternalConfig.builder()
        .githubPackagesUrl(request.getGithubConnectorDTO().getUrl())
        .authMechanism(((GithubHttpCredentialsDTO) request.getGithubConnectorDTO().getAuthentication().getCredentials())
                           .getType()
                           .getDisplayName())
        .username(username)
        .password(password)
        .token(token)
        .build();
  }

  public GithubPackagesArtifactDelegateResponse toGithubPackagesResponse(
      BuildDetails buildDetails, GithubPackagesArtifactDelegateRequest request) {
    return GithubPackagesArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .packageName(request.getPackageName())
        .version(buildDetails.getNumber())
        .versionRegex(request.getVersionRegex())
        .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
        .packageUrl(buildDetails.getArtifactPath())
        .label(buildDetails.getLabels())
        .build();
  }

  public List<GithubPackagesArtifactDelegateResponse> toGithubPackagesResponse(
      List<Map<String, String>> map, GithubPackagesArtifactDelegateRequest request) {
    List<GithubPackagesArtifactDelegateResponse> response = new ArrayList<>();

    for (Map<String, String> resMap : map) {
      GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
          GithubPackagesArtifactDelegateResponse.builder()
              .packageId(resMap.get("packageId"))
              .packageName(resMap.get("packageName"))
              .packageType(resMap.get("packageType"))
              .packageVisibility(resMap.get("visibility"))
              .packageUrl(resMap.get("packageUrl"))
              .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
              .build();

      response.add(githubPackagesArtifactDelegateResponse);
    }

    return response;
  }
}
