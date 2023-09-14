/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;
import static io.harness.idp.common.Constants.HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS;
import static io.harness.idp.common.Constants.HARNESS_POLICY_EVALUATION_DSL;
import static io.harness.idp.common.Constants.HARNESS_STO_SCAN_SETUP_DSL;
import static io.harness.idp.common.Constants.HARNESS_TEST_PASSING_ON_CI_IS_ZERO;
import static io.harness.idp.common.Constants.PAGERDUTY_INCIDENTS;
import static io.harness.idp.common.Constants.PAGERDUTY_SERVICE_DIRECTORY;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.CATALOG;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_FILE_EXISTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_IS_BRANCH_PROTECTION_SET;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_MEAN_TIME_TO_MERGE_PR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataSourceLocationFactory {
  private GithubMeanTimeToMergePRDsl githubMeanTimeToMergePRDsl;
  private GithubIsBranchProtectionSetDsl githubIsBranchProtectionSetDsl;
  private GithubFileExistsDsl githubFileExistsDsl;
  private HarnessProxyThroughDsl harnessProxyThroughDsl;
  private NoOpDsl noOpDsl;
  private PagerDutyServiceDirectory pagerDutyServiceDirectory;
  private PagerDutyIncidents pagerDutyIncidents;

  public DataSourceLocation getDataSourceLocation(String identifier) {
    switch (identifier) {
      // Github
      case GITHUB_MEAN_TIME_TO_MERGE_PR:
        return githubMeanTimeToMergePRDsl;
      case GITHUB_IS_BRANCH_PROTECTION_SET:
        return githubIsBranchProtectionSetDsl;
      case GITHUB_FILE_EXISTS:
        return githubFileExistsDsl;

        // Harness
      case HARNESS_STO_SCAN_SETUP_DSL:
      case HARNESS_POLICY_EVALUATION_DSL:
      case HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS:
      case HARNESS_TEST_PASSING_ON_CI_IS_ZERO:
        return harnessProxyThroughDsl;

      // Catalog
      case CATALOG:
        return noOpDsl;

      // Pagerduty
      case PAGERDUTY_SERVICE_DIRECTORY:
        return pagerDutyServiceDirectory;
      case PAGERDUTY_INCIDENTS:
        return pagerDutyIncidents;

      default:
        throw new UnsupportedOperationException(String.format("Could not find DataSource Location for %s", identifier));
    }
  }
}
