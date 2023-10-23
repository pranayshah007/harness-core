/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.jira;

import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "JiraAuthenticationKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.jira.JiraUserNamePasswordAuthentication")
public class JiraUserNamePasswordAuthentication implements JiraAuthentication {
  String username;
  String usernameRef;
  String passwordRef;

  @Override
  public JiraAuthCredentialsDTO toJiraAuthCredentialsDTO() {
    return JiraUserNamePasswordDTO.builder()
        .username(this.getUsername())
        .usernameRef(SecretRefHelper.createSecretRef(this.getUsernameRef()))
        .passwordRef(SecretRefHelper.createSecretRef(this.getPasswordRef()))
        .build();
  }

  public static JiraAuthentication fromJiraAuthCredentialsDTO(JiraAuthCredentialsDTO jiraAuthCredentialsDTO) {
    JiraUserNamePasswordDTO jiraUserNamePasswordDTO = (JiraUserNamePasswordDTO) jiraAuthCredentialsDTO;
    return JiraUserNamePasswordAuthentication.builder()
        .username(jiraUserNamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(jiraUserNamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(jiraUserNamePasswordDTO.getPasswordRef()))
        .build();
  }
}
