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
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConstant;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;

import software.wings.beans.JenkinsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class JenkinsRequestResponseMapper {
  public JenkinsInternalConfig toJenkinsInternalConfig(JenkinsArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    String token = "";
    if (request.getJenkinsConnectorDTO().getAuth() != null
        && request.getJenkinsConnectorDTO().getAuth().getCredentials() != null) {
      if (request.getJenkinsConnectorDTO().getAuth().getAuthType().getDisplayName() == JenkinsConstant.BEARER_TOKEN) {
        JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
            (JenkinsBearerTokenDTO) request.getJenkinsConnectorDTO().getAuth().getCredentials();
        if (jenkinsBearerTokenDTO.getTokenRef() != null) {
          token = EmptyPredicate.isNotEmpty(jenkinsBearerTokenDTO.getTokenRef().getDecryptedValue())
              ? new String(jenkinsBearerTokenDTO.getTokenRef().getDecryptedValue())
              : null;
        }
      } else if (request.getJenkinsConnectorDTO().getAuth().getAuthType().getDisplayName()
          == JenkinsConstant.USERNAME_PASSWORD) {
        JenkinsUserNamePasswordDTO credentials =
            (JenkinsUserNamePasswordDTO) request.getJenkinsConnectorDTO().getAuth().getCredentials();
        if (credentials.getPasswordRef() != null) {
          password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
              ? new String(credentials.getPasswordRef().getDecryptedValue())
              : null;
        }
        if (credentials.getPasswordRef() != null) {
          password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
              ? new String(credentials.getPasswordRef().getDecryptedValue())
              : null;
        }
        username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
            credentials.getUsername(), credentials.getUsernameRef());
      }
    }
    return JenkinsInternalConfig.builder()
        .jenkinsUrl(request.getJenkinsConnectorDTO().getJenkinsUrl())
        .authMechanism(request.getJenkinsConnectorDTO().getAuth().getAuthType().getDisplayName())
        .username(username)
        .password(password.toCharArray())
        .useConnectorUrlForJobExecution(request.isUseConnectorUrlForJobExecution())
        .token(token.toCharArray())
        .build();
  }

  public JenkinsInternalConfig toJenkinsInternalConfig(JenkinsConfig jenkinsConfig) {
    return JenkinsInternalConfig.builder()
        .jenkinsUrl(jenkinsConfig.getJenkinsUrl())
        .authMechanism(jenkinsConfig.getAuthMechanism())
        .username(jenkinsConfig.getUsername())
        .password(jenkinsConfig.getPassword())
        .token(jenkinsConfig.getToken())
        .build();
  }

  public JenkinsArtifactDelegateResponse toJenkinsArtifactDelegateResponse(
      BuildDetails buildDetails, JenkinsArtifactDelegateRequest attributeRequest) {
    return JenkinsArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .sourceType(ArtifactSourceType.JENKINS)
        .artifactPath(attributeRequest.getArtifactPaths().get(0))
        .build(buildDetails.getNumber())
        .jobName(attributeRequest.getJobName())
        .build();
  }
}
