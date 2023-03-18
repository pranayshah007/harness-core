/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class JiraTaskNGHelper {
  private final JiraTaskNGHandler jiraTaskNGHandler;
  private final SecretDecryptionService secretDecryptionService;

  @Inject
  public JiraTaskNGHelper(JiraTaskNGHandler jiraTaskNGHandler, SecretDecryptionService secretDecryptionService) {
    this.jiraTaskNGHandler = jiraTaskNGHandler;
    this.secretDecryptionService = secretDecryptionService;
  }

  public JiraTaskNGResponse getJiraTaskResponse(JiraTaskNGParameters params) {
    decryptRequestDTOs(params);
    switch (params.getAction()) {
      case VALIDATE_CREDENTIALS:
        return jiraTaskNGHandler.validateCredentials(params);
      case GET_PROJECTS:
        return jiraTaskNGHandler.getProjects(params);
      case GET_STATUSES:
        return jiraTaskNGHandler.getStatuses(params);
      case GET_ISSUE:
        return jiraTaskNGHandler.getIssue(params);
      case GET_ISSUE_CREATE_METADATA:
        return jiraTaskNGHandler.getIssueCreateMetadata(params);
      case GET_ISSUE_UPDATE_METADATA:
        return jiraTaskNGHandler.getIssueUpdateMetadata(params);
      case CREATE_ISSUE:
        return jiraTaskNGHandler.createIssue(params);
      case UPDATE_ISSUE:
        return jiraTaskNGHandler.updateIssue(params);
      case SEARCH_USER:
        return jiraTaskNGHandler.searchUser(params);
      default:
        throw new InvalidRequestException(String.format("Invalid jira action: %s", params.getAction()));
    }
  }

  private void decryptRequestDTOs(JiraTaskNGParameters jiraTaskNGParameters) {
    JiraConnectorDTO jiraConnectorDTO = jiraTaskNGParameters.getJiraConnectorDTO();
    if (!isNull(jiraConnectorDTO.getAuth()) && !isNull(jiraConnectorDTO.getAuth().getCredentials())) {
      secretDecryptionService.decrypt(
          jiraConnectorDTO.getAuth().getCredentials(), jiraTaskNGParameters.getEncryptionDetails());
    } else {
      secretDecryptionService.decrypt(jiraConnectorDTO, jiraTaskNGParameters.getEncryptionDetails());
    }
  }
}
