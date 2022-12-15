/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.security.encryption.SecretDecryptionService;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class JenkinsArtifactsUtils {
  private static final String JOB = "job";
  private static final String PATH_SEPARATOR = "/";

  public static String getJenkinsAuthHeader(
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, SecretDecryptionService secretDecryptionService) {
    String authHeader = null;
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    JenkinsAuthType authType = jenkinsConnectorDto.getAuth().getAuthType();
    if (JenkinsAuthType.USER_PASSWORD.equals(authType)) {
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          (JenkinsUserNamePasswordDTO) jenkinsConnectorDto.getAuth().getCredentials();
      String pair = jenkinsUserNamePasswordDTO.getUsername() + ":"
          + String.copyValueOf((jenkinsUserNamePasswordDTO.isDecrypted()
                  ? jenkinsUserNamePasswordDTO
                  : decrypt(jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig, secretDecryptionService))
                                   .getPasswordRef()
                                   .getDecryptedValue());
      authHeader = "Basic " + encodeBase64(pair);
    } else if (JenkinsAuthType.BEARER_TOKEN.equals(authType)) {
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
          (JenkinsBearerTokenDTO) jenkinsConnectorDto.getAuth().getCredentials();
      if (!jenkinsBearerTokenDTO.isDecrypted()) {
        jenkinsBearerTokenDTO = decrypt(jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig, secretDecryptionService);
      }
      authHeader = "Bearer "
          + String.copyValueOf((jenkinsBearerTokenDTO.isDecrypted()
                  ? jenkinsBearerTokenDTO
                  : decrypt(jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig, secretDecryptionService))
                                   .getTokenRef()
                                   .getDecryptedValue());
    }
    return authHeader;
  }

  public static String getJenkinsUrl(JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, String artifactPath) {
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    String url = jenkinsConnectorDto.getJenkinsUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return new StringBuilder().append(url).append(JOB).append(PATH_SEPARATOR).append(artifactPath).toString();
  }

  private JenkinsUserNamePasswordDTO decrypt(JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO,
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, SecretDecryptionService secretDecryptionService) {
    return (JenkinsUserNamePasswordDTO) secretDecryptionService.decrypt(
        jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }

  private JenkinsBearerTokenDTO decrypt(JenkinsBearerTokenDTO jenkinsBearerTokenDTO,
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, SecretDecryptionService secretDecryptionService) {
    return (JenkinsBearerTokenDTO) secretDecryptionService.decrypt(
        jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }
}
