/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class DataPoints {
  // Github
  public static final String GITHUB_PULL_REQUEST_MEAN_TIME_TO_MERGE = "meanTimeToMerge";
  public static final String GITHUB_IS_BRANCH_PROTECTED = "isBranchProtected";
  public static final String GITHUB_IS_FILE_EXISTS = "isFileExists";

  // Catalog
  public static final String CATALOG_TECH_DOCS = "techDocsAnnotation";
  public static final String CATALOG_PAGERDUTY = "pagerdutyAnnotation";
  public static final String CATALOG_SPEC_OWNER = "specOwner";

  // Harness
  public static final String STO_ADDED_IN_PIPELINE = "stoStageAdded";
  public static final String IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE = "isPolicyEvaluationSuccessful";
  public static final String PERCENTAGE_OF_CI_PIPELINE_FAILING_IN_SEVEN_DAYS =
      "PercentageOfCIPipelinePassingInPastSevenDays";
  public static final String PIPELINE_TEST_FAILING_IN_CI_IS_ZERO = "noTestsFailingInCiPipeline";
  public static final String INVALID_BRANCH_NAME_ERROR = "Invalid branch name provided";
  public static final String NO_PULL_REQUESTS_FOUND = "No pull requests found for branch: %s";
  public static final String INVALID_FILE_NAME_ERROR = "Invalid file name provided";
  public static final String GITHUB_ADMIN_PERMISSION_ERROR = "Github Connector does not have Admin permission";
}
