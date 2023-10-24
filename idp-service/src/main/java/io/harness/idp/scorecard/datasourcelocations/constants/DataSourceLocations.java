/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class DataSourceLocations {
  public static final String BODY = "{BODY}";

  // Github
  public static final String GITHUB_MEAN_TIME_TO_MERGE_PR = "github_mean_time_to_merge_pr";
  public static final String GITHUB_IS_BRANCH_PROTECTION_SET = "github_is_branch_protection_set";
  public static final String GITHUB_FILE_EXISTS = "github_is_file_exists";
  public static final String GITHUB_FILE_CONTENTS = "github_file_contents";
  public static final String GITHUB_WORKFLOWS_COUNT = "github_workflows_count";
  public static final String GITHUB_WORKFLOW_SUCCESS_RATE = "github_workflow_success_rate";
  public static final String GITHUB_MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS = "github_mean_time_to_complete_workflow_runs";
  public static final String GITHUB_MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS =
      "github_mean_time_to_complete_success_workflow_runs";
  public static final String GITHUB_OPEN_DEPENDABOT_ALERTS = "github_open_dependabot_alerts";
  public static final String GITHUB_OPEN_CODE_SCANNING_ALERTS = "github_open_code_scanning_alerts";
  public static final String GITHUB_OPEN_SECRET_SCANNING_ALERTS = "github_open_secret_scanning_alerts";
  public static final String GITHUB_OPEN_PULL_REQUESTS_BY_ACCOUNT = "github_open_pull_requests_by_account";
  public static final String HARNESS_STO_SCAN_SETUP_DSL = "harness_sto_scan_dsl";
  public static final String HARNESS_POLICY_EVALUATION_DSL = "harness_policy_evaluation_dsl";
  public static final String HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS = "harness_ci_success_percent_in_seven_days";
  public static final String HARNESS_TEST_PASSING_ON_CI_IS_ZERO = "harness_test_passing_on_ci_is_zero";
  public static final String PAGERDUTY_INCIDENTS = "pagerduty_incidents";
  public static final String PAGERDUTY_RESOLVED_INCIDENTS = "pagerduty_resolved_incidents";
  public static final String PAGERDUTY_SERVICE_DIRECTORY = "pagerduty_service_directory";

  // Bitbucket
  public static final String BITBUCKET_MEAN_TIME_TO_MERGE_PR = "bitbucket_mean_time_to_merge_pr";
  public static final String BITBUCKET_IS_BRANCH_PROTECTION_SET = "bitbucket_is_branch_protection_set";

  // Gitlab
  public static final String GITLAB_MEAN_TIME_TO_MERGE_PR = "gitlab_mean_time_to_merge_pr";
  public static final String GITLAB_IS_BRANCH_PROTECTION_SET = "gitlab_is_branch_protection_set";
  public static final String GITLAB_FILE_EXISTS = "gitlab_is_file_exists";

  // SCM Commons
  public static final String API_BASE_URL = "{API_BASE_URL}";
  public static final String REPO_SCM = "{REPO_SCM}";
  public static final String REPOSITORY_OWNER = "{REPOSITORY_OWNER}";
  public static final String REPOSITORY_NAME = "{REPOSITORY_NAME}";
  public static final String WORKSPACE = "{WORKSPACE}";
  public static final String PROJECT_PATH = "{PROJECT_PATH}";
  public static final String REPOSITORY_BRANCH = "{REPOSITORY_BRANCH}";

  // Catalog
  public static final String CATALOG = "catalog";

  // PagerDuty
  public static final String PAGERDUTY_SERVICE_ID = "{SERVICE_ID}";
  public static final String PAGERDUTY_TARGET_URL = "{TARGET_URL}";

  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String PAGERDUTY_ANNOTATION_MISSING_ERROR =
      "PagerDuty annotation is missing in the catalog info YAML";
  public static final String PAGERDUTY_PLUGIN_NOT_ENABLED_ERROR_MESSAGE = "PagerDuty Plugin is not enabled";
  public static final String PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE =
      "PagerDuty token added in plugin is invalid";
  public static final String PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE =
      "Unable to get the PagerDuty data, probably target url provided in plugin is invalid";
  public static final String PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE = "Unable to fetch the data from PagerDuty";

  // Jira
  public static final String PROJECT_COMPONENT_REPLACER = "{PROJECT_COMPONENT_REPLACER}";
  public static final String JIRA_MEAN_TIME_TO_RESOLVE = "jira_mean_time_to_resolve";
  public static final String JIRA_ISSUES_COUNT = "jira_issues_count";
  public static final String JIRA_ISSUES_OPEN_CLOSE_RATIO = "jira_issues_open_close_ratio";
  // Kubernetes
  public static final String KUBERNETES = "kubernetes";
}
