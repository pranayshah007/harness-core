/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.jira;

import io.harness.jira.JiraConstantsNG;
import io.harness.jira.JiraIssueNG;

import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class CCMJiraUtils {
  public static String getStatus(JiraIssueNG jiraIssueNG) {
    return (String) getStatusMap(jiraIssueNG).get("name");
  }

  public static String getStatusColor(JiraIssueNG jiraIssueNG) {
    return getStatusCategoryMap(jiraIssueNG).get("colorName");
  }

  public static String getStatusCategory(JiraIssueNG jiraIssueNG) {
    return getStatusCategoryMap(jiraIssueNG).get("key");
  }

  private static Map<String, Object> getStatusMap(JiraIssueNG jiraIssueNG) {
    return (Map<String, Object>) jiraIssueNG.getFields().get(JiraConstantsNG.STATUS_INTERNAL_NAME);
  }

  private static Map<String, String> getStatusCategoryMap(JiraIssueNG jiraIssueNG) {
    return (Map<String, String>) getStatusMap(jiraIssueNG).get("statusCategory");
  }
}
