/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.jira.JiraInternalConfig;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class JiraRequestResponseMapper {
  public JiraInternalConfig toJiraInternalConfig(JiraTaskNGParameters parameters) {
    JiraConnectorDTO dto = parameters.getJiraConnectorDTO();
    return JiraInternalConfig.builder()
        .jiraUrl(dto.getJiraUrl())
        .username(FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
            dto.getUsername(), dto.getUsernameRef()))
        .password(new String(dto.getPasswordRef().getDecryptedValue()))
        .build();
  }
}
