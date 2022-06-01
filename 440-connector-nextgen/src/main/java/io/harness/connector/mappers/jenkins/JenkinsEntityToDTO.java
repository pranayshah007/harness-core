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
import io.harness.connector.entities.embedded.jenkins.JenkinsUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class JenkinsEntityToDTO implements ConnectorEntityToDTOMapper<JenkinsConnectorDTO, JenkinsConnector> {
  @Override
  public JenkinsConnectorDTO createConnectorDTO(JenkinsConnector jenkinsConnector) {
    JenkinsAuthenticationDTO jenkinsAuthenticationDTO = null;
    if (jenkinsConnector.getAuthType() == JenkinsAuthType.USER_PASSWORD) {
      JenkinsUserNamePasswordAuthentication jenkinsCredentials =
          (JenkinsUserNamePasswordAuthentication) jenkinsConnector.getJenkinsAuthentication();
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          JenkinsUserNamePasswordDTO.builder()
              .username(jenkinsCredentials.getUsername())
              .usernameRef(SecretRefHelper.createSecretRef(jenkinsCredentials.getUsernameRef()))
              .passwordRef(SecretRefHelper.createSecretRef(jenkinsCredentials.getPasswordRef()))
              .build();
      jenkinsAuthenticationDTO = jenkinsAuthenticationDTO.builder()
                                     .authType(jenkinsConnector.getAuthType())
                                     .credentials(jenkinsUserNamePasswordDTO)
                                     .build();
    } else if (jenkinsConnector.getAuthType() == JenkinsAuthType.BEARER_TOKEN) {
      JenkinsBearerTokenAuthentication jenkinsCredentials =
          (JenkinsBearerTokenAuthentication) jenkinsConnector.getJenkinsAuthentication();
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
          JenkinsBearerTokenDTO.builder()
              .tokenRef(SecretRefHelper.createSecretRef(jenkinsCredentials.getTokenRef()))
              .build();
      jenkinsAuthenticationDTO = jenkinsAuthenticationDTO.builder()
                                     .authType(jenkinsConnector.getAuthType())
                                     .credentials(jenkinsBearerTokenDTO)
                                     .build();
    } else {
      jenkinsAuthenticationDTO = JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.ANONYMOUS).build();
    }

    return JenkinsConnectorDTO.builder().jenkinsUrl(jenkinsConnector.getUrl()).auth(jenkinsAuthenticationDTO).build();
  }
}
