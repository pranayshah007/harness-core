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
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GithubBaseDsl implements DataSourceLocation {
  DataPointParserFactory dataPointParserFactory;
  DslClientFactory dslClientFactory;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      String dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues) {
    for (DataPointEntity dataPoint :
        dataPointsAndInputValues.keySet()) { // isBranchProtected(main), isBranchProtected(develop)
      DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPoint.getIdentifier());
      if (dataPoint.isConditional()) {
        String key = dataPointParser.getReplaceKey(dataPoint); // {branch}
        String value = dataPointParser.extractInputValue(dataPoint.getExpression()); // main/develop
        log.info("replace key : {}, value: {}", key, value);
        // replace in the API
      }
    }

    // Changes added for testing. To be removed later once we have actual code.
    Map<String, Object> dataFromDataSource = new HashMap<>();
    Map<String, Object> dataFromDataPoint = new HashMap<>();
    dataFromDataPoint.put("main", true);
    dataFromDataPoint.put("master", true);
    dataFromDataPoint.put("develop", false);
    dataFromDataSource.put("isBranchProtected", dataFromDataPoint);
    dataFromDataSource.put("random1", "random");
    dataFromDataSource.put("random2", "random");
    return dataFromDataSource;
  }
}
