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
  // Github, Bitbucket
  public static final String PULL_REQUEST_MEAN_TIME_TO_MERGE = "meanTimeToMerge";
  public static final String IS_BRANCH_PROTECTED = "isBranchProtected";
  public static final String IS_FILE_EXISTS = "isFileExists";

  // Catalog
  public static final String CATALOG_TECH_DOCS = "techDocsAnnotationExists";
  public static final String CATALOG_PAGERDUTY = "pagerdutyAnnotationExists";
  public static final String CATALOG_SPEC_OWNER = "specOwnerExists";

  // Harness
  public static final String STO_ADDED_IN_PIPELINE = "stoStageAdded";
  public static final String IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE = "isPolicyEvaluationSuccessful";
  public static final String PERCENTAGE_OF_CI_PIPELINE_FAILING_IN_SEVEN_DAYS =
      "PercentageOfCIPipelinePassingInPastSevenDays";
  public static final String PIPELINE_TEST_FAILING_IN_CI_IS_ZERO = "noTestsFailingInCiPipeline";

  // PagerDuty
  public static final String IS_ON_CALL_SET = "isOnCallSet";
  public static final String IS_ESCALATION_POLICY_SET = "isEscalationPolicySet";
  public static final String NO_OF_INCIDENTS_IN_LAST_THIRTY_DAYS = "noOfIncidentsInLastThirtyDays";
  public static final String AVG_RESOLVED_TIME_FOR_LAST_TEN_RESOLVED_INCIDENTS_IN_MINUTES =
      "avgResolvedTimeForLastTenResolvedIncidentsInMinutes";
  public static final String INVALID_BRANCH_NAME_ERROR = "Invalid branch name provided";
  public static final String SOURCE_LOCATION_ANNOTATION_ERROR =
      "Invalid or missing source-location annotation in the catalog info YAML";
  public static final String NO_PULL_REQUESTS_FOUND = "No pull requests found for branch: %s";
  public static final String INVALID_FILE_NAME_ERROR = "Invalid file name provided";
  public static final String GITHUB_ADMIN_PERMISSION_ERROR = "Github Connector does not have Admin permission";

  // Kubernetes
  public static final String REPLICAS = "replicas";
  public static final String DAYS_SINCE_LAST_DEPLOYED = "daysSinceLastDeployed";
}
