/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.jira;

import io.harness.jira.JiraIssueNG;

import java.util.Map;

public interface CCMJiraHelper {
  JiraIssueNG createIssue(
      String accountId, String jiraConnectorRef, String projectKey, String issueType, Map<String, String> fields);
  JiraIssueNG getIssue(String accountId, String jiraConnectorRef, String issueKey);
}
