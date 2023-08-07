/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_BASE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_PR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceLocationFactory {
  private GithubPullRequestDsl githubPullRequestDsl;
  private GithubBaseDsl githubBaseDsl;

  public DataSourceLocation getDataSourceLocation(String identifier, List<DataPointEntity> dataPointsToFetch) {
    switch (identifier) {
      case GITHUB_PR:
        return githubPullRequestDsl;
      // Add more cases for other DSLs
      case GITHUB_BASE:
        return githubBaseDsl;
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataSource Location for %s", identifier));
    }
  }
}
