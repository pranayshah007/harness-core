/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira.mappers;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.jira.JiraInternalConfig;

import lombok.experimental.UtilityClass;
import okhttp3.Credentials;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@UtilityClass
public class JiraRequestResponseMapper {
  private static final String BEARER = "Bearer";

  public JiraInternalConfig toJiraInternalConfig(JiraConnectorDTO dto) {
    if (isNull(dto)) {
      return null;
    }
    if (isNull(dto.getAuth()) || isNull(dto.getAuth().getCredentials())) {
      // means somehow auth type is not present (not easily possible as migration done)
      return JiraInternalConfig.builder()
          .jiraUrl(dto.getJiraUrl())
          .authToken(getAuthTokenUsingUserNamePassword(dto.getUsername(), dto.getUsernameRef(), dto.getPasswordRef()))
          .build();
    }
    JiraAuthType jiraAuthType = dto.getAuth().getAuthType();
    JiraAuthCredentialsDTO jiraAuthCredentialsDTO = dto.getAuth().getCredentials();

    if (JiraAuthType.USER_PASSWORD.equals(jiraAuthType)) {
      JiraUserNamePasswordDTO jiraUserNamePasswordDTO = (JiraUserNamePasswordDTO) jiraAuthCredentialsDTO;
      return JiraInternalConfig.builder()
          .jiraUrl(dto.getJiraUrl())
          .authToken(getAuthTokenUsingUserNamePassword(jiraUserNamePasswordDTO.getUsername(),
              jiraUserNamePasswordDTO.getUsernameRef(), jiraUserNamePasswordDTO.getPasswordRef()))
          .build();
    } else if (JiraAuthType.PAT.equals(jiraAuthType)) {
      JiraPATDTO jiraPATDTO = (JiraPATDTO) jiraAuthCredentialsDTO;
      return JiraInternalConfig.builder()
          .jiraUrl(dto.getJiraUrl())
          .authToken(String.format("%s %s", BEARER, new String(jiraPATDTO.getPatRef().getDecryptedValue())))
          .build();
    } else {
      throw new InvalidRequestException(String.format("Unsupported auth type in jira connector: %s", jiraAuthType));
    }
  }

  private static String getAuthTokenUsingUserNamePassword(
      String userName, SecretRefData userNameRef, SecretRefData passwordRef) {
    String finalUserName =
        FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(userName, userNameRef);
    String password = new String(passwordRef.getDecryptedValue());
    return Credentials.basic(finalUserName, password);
  }
}
