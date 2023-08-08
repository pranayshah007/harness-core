/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GithubPullRequestDsl implements DataSourceLocation {
  DataPointParserFactory dataPointParserFactory;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataPointEntity> dataPointsToFetch) {
    //  delegateSelectorsCache.get(accountIdentifier, entity.getGithubHost())
    //  based on that we need to either hit the API or create a delegate task

    for (DataPointEntity dataPoint : dataPointsToFetch) { // isBranchProtected(main), isBranchProtected(develop)
      DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPoint.getIdentifier());
      if (dataPoint.isConditional()) {
        String key = dataPointParser.getReplaceKey(dataPoint); // {branch}
        String value = dataPointParser.extractInputValue(dataPoint.getExpression()); // main/develop
        // replace in the API
      }
    }

    // API
    // headers
    // catalog entity -> https://github.com/harness/harness-idp -> repo details
    // backstage env variables -> GITHUB_TOKEN, APP -> Authorization Header

    // common okhttpclient

    /*
custom logic

dataPointsToFetch[0] -> github_mttm

{
    github_mttm: 48
}
* */
    return null;
  }
}
