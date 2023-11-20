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

  // Plugin identifiers
  public static final String CIRCLE_CI_PLUGIN = "circleci";
  public static final String CONFLUENCE_PLUGIN = "confluence";
  public static final String DATADOG_PLUGIN = "datadog";
  public static final String FIRE_HYDRANT_PLUGIN = "firehydrant";
  public static final String GITHUB_ACTIONS_PLUGIN = "github-actions";
  public static final String GITHUB_CATALOG_DISCOVERY_PLUGIN = "github-catalog-discovery";
  public static final String GITHUB_INSIGHTS_PLUGIN = "github-insights";
  public static final String GITHUB_PULL_REQUESTS_PLUGIN = "github-pull-requests";
  public static final String GRAFANA_PLUGIN = "grafana";
  public static final String HARNESS_CI_CD_PLUGIN = "harness-ci-cd";
  public static final String HARNESS_FEATURE_FLAGS_PLUGIN = "harness-feature-flags";
  public static final String JENKINS_PLUGIN = "jenkins";
  public static final String JIRA_PLUGIN = "jira";
  public static final String KAFKA_PLUGIN = "kafka";
  public static final String KUBERNETES_PLUGIN = "kubernetes";
  public static final String LIGHTHOUSE_PLUGIN = "lighthouse";
  public static final String PAGER_DUTY_PLUGIN = "pager-duty";
  public static final String SYNK_SECURITY_PLUGIN = "snyk-security";
  public static final String SONARQUBE_PLUGIN = "sonarqube";
  public static final String TODO_PLUGIN = "todo";

  public static final String OPSGENIE_PLUGIN = "opsgenie";

  public static final List<String> pluginIds = List.of(CIRCLE_CI_PLUGIN, CONFLUENCE_PLUGIN, DATADOG_PLUGIN,
      FIRE_HYDRANT_PLUGIN, GITHUB_ACTIONS_PLUGIN, GITHUB_CATALOG_DISCOVERY_PLUGIN, GITHUB_INSIGHTS_PLUGIN,
      GITHUB_PULL_REQUESTS_PLUGIN, GRAFANA_PLUGIN, HARNESS_CI_CD_PLUGIN, HARNESS_FEATURE_FLAGS_PLUGIN, JENKINS_PLUGIN,
      JIRA_PLUGIN, KUBERNETES_PLUGIN, OPSGENIE_PLUGIN, PAGER_DUTY_PLUGIN, SONARQUBE_PLUGIN, TODO_PLUGIN);
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
  public static final String GITHUB_AUTH_NAME = "GitHub Auth";
  public static final String GOOGLE_AUTH = "google-auth";
  public static final String GOOGLE_AUTH_NAME = "Google Auth";
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
  public static final String GITLAB_IDENTIFIER = "gitlab";
  public static final String BITBUCKET_IDENTIFIER = "bitbucket";
  public static final String CATALOG_IDENTIFIER = "catalog";
  public static final String KUBERNETES_IDENTIFIER = "kubernetes";
  public static final String CUSTOM_IDENTIFIER = "custom";
  public static final String PAGERDUTY_IDENTIFIER = "pagerduty";
  public static final String JIRA_IDENTIFIER = "jira";
  public static final String DSL_RESPONSE = "dsl_response";
  public static final String DATA_POINT_VALUE_KEY = "value";
  public static final String ERROR_MESSAGE_KEY = "error_messages";
  public static final String ERROR_MESSAGES_KEY = "errorMessages";
  public static final String ERRORS = "errors";
  public static final String MESSAGE_KEY = "message";

  public static final String QA_HOST = "qa.harness.io";
  public static final String PRE_QA_HOST = "stress.harness.io";
  public static final String PROD_HOST = "app.harness.io";

  public static final String LOCAL_HOST = "localhost:8181";

  public static final String QA_ENV = "qa";
  public static final String PRE_QA_ENV = "stress";

  public static final String LOCAL_ENV = "local";

  public static final String COMPLIANCE_ENV = "compliance";

  public static final String DEFAULT_BRANCH_KEY = "refs/";
  public static final String DEFAULT_BRANCH_KEY_ESCAPED = "\"refs/\"";

  public static final String KUBERNETES = "kubernetes";
  public static final String HARNESS_ACCOUNT = "Harness-Account";
}
