/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.*;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import software.wings.beans.GithubPackagesConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

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

          if (githubUsernamePasswordDTO.getPasswordRef() != null) {
            password = EmptyPredicate.isNotEmpty(githubUsernamePasswordDTO.getPasswordRef().getDecryptedValue())
                ? new String(githubUsernamePasswordDTO.getPasswordRef().getDecryptedValue())
                : null;
          }
        } else if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
          GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());

          if (githubUsernameTokenDTO.getTokenRef() != null) {
            token = EmptyPredicate.isNotEmpty(githubUsernameTokenDTO.getTokenRef().getDecryptedValue())
                ? new String(githubUsernameTokenDTO.getTokenRef().getDecryptedValue())
                : null;
          }
        } else if (httpDTO.getType() == GithubHttpAuthenticationType.OAUTH) {
          GithubOauthDTO githubOauthDTO = (GithubOauthDTO) httpDTO.getHttpCredentialsSpec();

          if (githubOauthDTO.getTokenRef() != null) {
            token = EmptyPredicate.isNotEmpty(githubOauthDTO.getTokenRef().getDecryptedValue())
                ? new String(githubOauthDTO.getTokenRef().getDecryptedValue())
                : null;
          }
        }
      } else if (request.getGithubConnectorDTO().getAuthentication().getAuthType() == GitAuthType.SSH) {
        GithubSshCredentialsDTO sshDTO =
            (GithubSshCredentialsDTO) request.getGithubConnectorDTO().getAuthentication().getCredentials();

        if (sshDTO.getSshKeyRef() != null) {
          token = EmptyPredicate.isNotEmpty(sshDTO.getSshKeyRef().getDecryptedValue())
              ? new String(sshDTO.getSshKeyRef().getDecryptedValue())
              : null;
        }
      }
    }

    return GithubPackagesInternalConfig.builder()
        .githubPackagesUrl(request.getGithubConnectorDTO().getUrl())
        .authMechanism(request.getGithubConnectorDTO().getAuthentication().getAuthType().getDisplayName())
        .username(username)
        .password(password.toCharArray())
        .token(token.toCharArray())
        .build();
  }

  public GithubPackagesInternalConfig toJenkinsInternalConfig(GithubPackagesConfig githubPackagesConfig) {
    return null;
  }

  public GithubPackagesArtifactDelegateResponse toGithubPackagesResponse(
      BuildDetails buildDetails, GithubPackagesArtifactDelegateRequest request) {
    return GithubPackagesArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .packageName(request.getPackageName())
        .version(buildDetails.getNumber())
        .versionRegex(request.getVersionRegex())
        .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
        .build();
  }
}
