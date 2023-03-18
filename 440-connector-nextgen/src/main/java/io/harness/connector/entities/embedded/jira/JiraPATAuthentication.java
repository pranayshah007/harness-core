/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.jira;

import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
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
@TypeAlias("io.harness.connector.entities.embedded.jira.JiraPATAuthentication")
public class JiraPATAuthentication implements JiraAuthentication {
  String patRef;

  @Override
  public JiraAuthCredentialsDTO toJiraAuthCredentialsDTO() {
    return JiraPATDTO.builder().patRef(SecretRefHelper.createSecretRef(this.getPatRef())).build();
  }

  public static JiraAuthentication fromJiraAuthCredentialsDTO(JiraAuthCredentialsDTO jiraAuthCredentialsDTO) {
    JiraPATDTO jiraPATDTO = (JiraPATDTO) jiraAuthCredentialsDTO;
    return JiraPATAuthentication.builder()
        .patRef(SecretRefHelper.getSecretConfigString(jiraPATDTO.getPatRef()))
        .build();
  }
}
