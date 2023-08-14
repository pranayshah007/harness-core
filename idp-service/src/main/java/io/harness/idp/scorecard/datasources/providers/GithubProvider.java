/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.scorecard.datasources.constants.Constants.GITHUB_PROVIDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GithubProvider implements DataSourceProvider {
  private DataSourceLocationFactory dataSourceLocationFactory;
  private DataPointParserFactory dataPointParserFactory;
  private DataPointService dataPointService;

  @Override
  public String getProviderIdentifier() {
    return GITHUB_PROVIDER;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Set<String> dataPointIdentifiers = dataPointsAndInputValues.keySet();
    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(
        accountIdentifier, new ArrayList<>(dataPointIdentifiers), this.getProviderIdentifier());

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (String dslIdentifier : dataToFetch.keySet()) {
      Map<String, Set<String>> dataToFetchWithInputValues = new HashMap<>();
      dataToFetch.get(dslIdentifier)
          .forEach(dataPointEntity
              -> dataToFetchWithInputValues.put(
                  dataPointEntity.getIdentifier(), dataPointsAndInputValues.get(dataPointEntity.getIdentifier())));

      DataSourceLocation dataSourceLocation = dataSourceLocationFactory.getDataSourceLocation(dslIdentifier);
      Map<String, Object> response =
          dataSourceLocation.fetchData(accountIdentifier, entity, dslIdentifier, dataToFetchWithInputValues);

      Map<String, Object> dataPointValues = new HashMap<>();
      for (Map.Entry<String, Set<String>> entry : dataToFetchWithInputValues.entrySet()) {
        String dataPointIdentifier = entry.getKey();
        Set<String> inputValues = entry.getValue();
        DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointIdentifier);
        Object values = dataPointParser.parseDataPoint(response, dataPointIdentifier, inputValues);
        if (values != null) {
          dataPointValues.put(dataPointIdentifier, values);
        }
      }
      Map<String, Object> providerData = aggregatedData.getOrDefault(getProviderIdentifier(), new HashMap<>());
      providerData.putAll(dataPointValues);
      aggregatedData.put(getProviderIdentifier(), providerData);
    }

    return aggregatedData;
  }
}
