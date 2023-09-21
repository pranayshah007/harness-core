/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class Constants {
  private Constants() {}

  public static final String IDP_PREFIX = "idp_";
  public static final String IDP_RESOURCE_TYPE = "IDP_SETTINGS";
  public static final String IDP_PERMISSION = "idp_idpsettings_manage";
  public static final List<String> pluginIds = List.of("circleci", "confluence", "firehydrant", "github-actions",
      "github-catalog-discovery", "github-insights", "github-pull-requests", "harness-ci-cd", "harness-feature-flags",
      "jenkins", "jira", "kubernetes", "pager-duty", "todo");
  public static final String GITHUB_TOKEN = "HARNESS_GITHUB_TOKEN";
  public static final String GITHUB_APP_ID = "HARNESS_GITHUB_APP_APPLICATION_ID";
  public static final String GITHUB_APP_PRIVATE_KEY_REF = "HARNESS_GITHUB_APP_PRIVATE_KEY_REF";
  public static final String PRIVATE_KEY_START = "-----BEGIN PRIVATE KEY-----";
  public static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
  public static final String GITLAB_TOKEN = "HARNESS_GITLAB_TOKEN";
  public static final String BITBUCKET_USERNAME = "HARNESS_BITBUCKET_USERNAME";
  public static final String BITBUCKET_USERNAME_API_ACCESS = "HARNESS_BITBUCKET_API_ACCESS_USERNAME";
  public static final String BITBUCKET_TOKEN = "HARNESS_BITBUCKET_TOKEN";
  public static final String BITBUCKET_API_ACCESS_TOKEN = "HARNESS_BITBUCKET_API_ACCESS_TOKEN";
  public static final String AZURE_REPO_TOKEN = "HARNESS_AZURE_REPO_TOKEN";
  public static final String BACKEND_SECRET = "BACKEND_SECRET";
  public static final String IDP_BACKEND_SECRET = "IDP_BACKEND_SECRET";
  public static final String PROXY_ENV_NAME = "HOST_PROXY_MAP";
  public static final String GITHUB_AUTH = "github-auth";
  public static final String GOOGLE_AUTH = "google-auth";
  public static final String AUTH_GITHUB_CLIENT_ID = "AUTH_GITHUB_CLIENT_ID";
  public static final String AUTH_GITHUB_CLIENT_SECRET = "AUTH_GITHUB_CLIENT_SECRET";
  public static final String AUTH_GITHUB_ENTERPRISE_INSTANCE_URL = "AUTH_GITHUB_ENTERPRISE_INSTANCE_URL";
  public static final List<String> GITHUB_AUTH_ENV_VARIABLES =
      new ArrayList<>(List.of(AUTH_GITHUB_CLIENT_ID, AUTH_GITHUB_CLIENT_SECRET, AUTH_GITHUB_ENTERPRISE_INSTANCE_URL));
  public static final String AUTH_GOOGLE_CLIENT_ID = "AUTH_GOOGLE_CLIENT_ID";
  public static final String AUTH_GOOGLE_CLIENT_SECRET = "AUTH_GOOGLE_CLIENT_SECRET";
  public static final List<String> GOOGLE_AUTH_ENV_VARIABLES =
      new ArrayList<>(List.of(AUTH_GOOGLE_CLIENT_ID, AUTH_GOOGLE_CLIENT_SECRET));
  public static final String LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG =
      "LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG";
  public static final String SLASH_DELIMITER = "/";
  public static final String SOURCE_FORMAT = "blob";
  public static final String LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES = "LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES";
  public static final String PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK = "pluginRequestsNotificationSlack";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  public static final String DOT_SEPARATOR = ".";
  public static final String SPACE_SEPARATOR = " ";
  public static final String SUCCESS_RESPONSE = "SUCCESS";

  public static final String HARNESS_IDENTIFIER = "harness";
  public static final String GITHUB_IDENTIFIER = "github";
  public static final String CATALOG_IDENTIFIER = "catalog";
  public static final String CUSTOM_IDENTIFIER = "custom";
  public static final String PAGERDUTY_IDENTIFIER = "pagerduty";
  public static final String DSL_RESPONSE = "dsl_response";
  public static final String DATA_POINT_VALUE_KEY = "value";
  public static final String ERROR_MESSAGE_KEY = "error_messages";
  public static final String MESSAGE_KEY = "message";

  public static final String QA_HOST = "qa.harness.io";
  public static final String PRE_QA_HOST = "stress.harness.io";
  public static final String PROD_HOST = "app.harness.io";

  public static final String LOCAL_HOST = "localhost:8181";

  public static final String QA_ENV = "qa";
  public static final String PRE_QA_ENV = "stress";

  public static final String LOCAL_ENV = "local";

  public static final String COMPLIANCE_ENV = "compliance";

  public static final String HARNESS_STO_SCAN_SETUP_DSL = "harness_sto_scan_dsl";
  public static final String HARNESS_POLICY_EVALUATION_DSL = "harness_policy_evaluation_dsl";
  public static final String HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS = "harness_ci_success_percent_in_seven_days";

  public static final String HARNESS_TEST_PASSING_ON_CI_IS_ZERO = "harness_test_passing_on_ci_is_zero";
  public static final String GITHUB_DEFAULT_BRANCH_KEY = "refs/";
  public static final String GITHUB_DEFAULT_BRANCH_KEY_ESCAPED = "\"refs/\"";

  public static final String PAGERDUTY_SERVICE_DIRECTORY = "pagerduty_service_directory";
  public static final String PAGERDUTY_INCIDENTS = "pagerduty_incidents";
}
