/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GithubProvider implements DataSourceProvider {
  private DataSourceLocationFactory dataSourceLocationFactory;
  private DataPointParserFactory dataPointParserFactory;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, List<DataPointEntity> dataPoints) {
    Map<DataSourceLocationEntity, List<DataPointEntity>> dataToFetch = new HashMap<>();
    /*
    Construct this map :
    {
        GITHUB_PR: [DP1(main), DP1(develop), DP2(.gitleaks), DP3]
        GITHUB_BASE: [DP4, DP5],
    }
    THIRD not needed
    * */
    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (DataSourceLocationEntity dataSourceLocationEntity : dataToFetch.keySet()) {
      Map<String, Object> dataPointValues = new HashMap<>();
      List<DataPointEntity> dataPointsToFetch = dataToFetch.get(dataSourceLocationEntity);

      DataSourceLocation dataSourceLocation =
          dataSourceLocationFactory.getDataSourceLocation(dataSourceLocationEntity.getIdentifier(), dataPointsToFetch);
      Map<String, Object> response =
          dataSourceLocation.fetchData(accountIdentifier, entity, dataSourceLocationEntity, dataPointsToFetch);

      for (DataPointEntity dataPoint : dataPointsToFetch) {
        DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPoint.getIdentifier());
        Object value = dataPointParser.parseDataPoint(response, dataPoint);
        dataPointValues.put(dataPoint.getIdentifier(), value);
      }
      aggregatedData.getOrDefault("github", new HashMap<>()).putAll(dataPointValues);
    }

    return aggregatedData;
  }
}
