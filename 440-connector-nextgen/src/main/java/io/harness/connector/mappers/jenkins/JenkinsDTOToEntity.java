/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.jenkins;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.jenkins.JenkinsBearerTokenAuthentication;
import io.harness.connector.entities.embedded.jenkins.JenkinsConnector;
import io.harness.connector.entities.embedded.jenkins.JenkinsConnector.JenkinsConnectorBuilder;
import io.harness.connector.entities.embedded.jenkins.JenkinsUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class JenkinsDTOToEntity implements ConnectorDTOToEntityMapper<JenkinsConnectorDTO, JenkinsConnector> {
  @Override
  public JenkinsConnector toConnectorEntity(JenkinsConnectorDTO configDTO) {
    JenkinsAuthType jenkinsAuthType = configDTO.getAuth().getAuthType();
    JenkinsConnectorBuilder jenkinsConnectorBuilder =
        JenkinsConnector.builder().url(StringUtils.trim(configDTO.getJenkinsUrl())).authType(jenkinsAuthType);
    if (jenkinsAuthType == JenkinsAuthType.USER_PASSWORD) {
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          (JenkinsUserNamePasswordDTO) configDTO.getAuth().getCredentials();
      jenkinsConnectorBuilder.jenkinsAuthentication(createDockerAuthentication(jenkinsUserNamePasswordDTO));
    }

    if (jenkinsAuthType == JenkinsAuthType.BEARER_TOKEN) {
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO = (JenkinsBearerTokenDTO) configDTO.getAuth().getCredentials();
      jenkinsConnectorBuilder.jenkinsAuthentication(createDockerAuthentication(jenkinsBearerTokenDTO));
    }

    JenkinsConnector jenkinsConnector = jenkinsConnectorBuilder.build();
    jenkinsConnector.setType(ConnectorType.JENKINS);
    return jenkinsConnector;
  }

  private JenkinsUserNamePasswordAuthentication createDockerAuthentication(
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO) {
    return JenkinsUserNamePasswordAuthentication.builder()
        .username(jenkinsUserNamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(jenkinsUserNamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(jenkinsUserNamePasswordDTO.getPasswordRef()))
        .build();
  }

  private JenkinsBearerTokenAuthentication createDockerAuthentication(JenkinsBearerTokenDTO jenkinsBearerTokenDTO) {
    return JenkinsBearerTokenAuthentication.builder()
        .tokenRef(SecretRefHelper.getSecretConfigString(jenkinsBearerTokenDTO.getTokenRef()))
        .build();
  }
}
