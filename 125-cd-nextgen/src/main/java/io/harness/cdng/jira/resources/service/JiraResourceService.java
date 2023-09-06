/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jira.resources.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.jira.JiraSearchUserData;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueTransitionNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;

import java.util.List;

@OwnedBy(CDC)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public interface JiraResourceService {
  boolean validateCredentials(IdentifierRef jiraConnectorRef, String orgId, String projectId);
  List<JiraProjectBasicNG> getProjects(IdentifierRef jiraConnectorRef, String orgId, String projectId);
  List<JiraStatusNG> getStatuses(IdentifierRef jiraConnectorRef, String orgId, String projectId, String projectKey,
      String issueType, String issueKey);
  JiraIssueCreateMetadataNG getIssueCreateMetadata(IdentifierRef jiraConnectorRef, String orgId, String projectId,
      String projectKey, String issueType, String expand, boolean fetchStatus, boolean ignoreComment);
  JiraIssueUpdateMetadataNG getIssueUpdateMetadata(
      IdentifierRef jiraConnectorRef, String orgId, String projectId, String issueKey);
  JiraSearchUserData searchUser(String accountId, String orgIdentifier, String projectIdentifier, String connectorId,
      long defaultSyncCallTimeout, String userQuery, String offset);
  List<JiraIssueTransitionNG> getTransitions(
      IdentifierRef jiraConnectorRef, String orgId, String projectId, String issueKey);
}
