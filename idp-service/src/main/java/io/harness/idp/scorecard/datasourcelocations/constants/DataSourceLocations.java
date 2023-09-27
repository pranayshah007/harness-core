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

  // Bitbucket
  public static final String BITBUCKET_MEAN_TIME_TO_MERGE_PR = "bitbucket_mean_time_to_merge_pr";
  public static final String BITBUCKET_IS_BRANCH_PROTECTION_SET = "bitbucket_is_branch_protection_set";

  // SCM Commons
  public static final String API_BASE_URL = "{API_BASE_URL}";
  public static final String REPO_SCM = "{REPO_SCM}";
  public static final String REPOSITORY_OWNER = "{REPOSITORY_OWNER}";
  public static final String REPOSITORY_NAME = "{REPOSITORY_NAME}";
  public static final String WORKSPACE = "{WORKSPACE}";
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

  // Kubernetes
  public static final String KUBERNETES = "kubernetes";
}
