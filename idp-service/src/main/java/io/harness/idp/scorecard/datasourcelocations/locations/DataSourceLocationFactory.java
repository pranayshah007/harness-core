/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.CATALOG;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_GIT_LEAKS_FILE_EXISTS;
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
  private NoOpDsl noOpDsl;

  public DataSourceLocation getDataSourceLocation(String identifier) {
    switch (identifier) {
      // Github
      case GITHUB_MEAN_TIME_TO_MERGE_PR:
        return githubMeanTimeToMergePRDsl;
      case GITHUB_IS_BRANCH_PROTECTION_SET:
        return githubIsBranchProtectionSetDsl;
      case GITHUB_GIT_LEAKS_FILE_EXISTS:
        return githubFileExistsDsl;

      // Catalog
      case CATALOG:
        return noOpDsl;
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataSource Location for %s", identifier));
    }
  }
}
