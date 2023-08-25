/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.scorecard.datasources.constants.Constants.CATALOG_PROVIDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CatalogProvider implements DataSourceProvider {
  private DataPointParserFactory dataPointParserFactory;
  private DataPointService dataPointService;
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public String getProviderIdentifier() {
    return CATALOG_PROVIDER;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, Object> response = mapper.convertValue(entity, new TypeReference<>() {});

    Set<String> dataPointIdentifiers = dataPointsAndInputValues.keySet();
    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(
        accountIdentifier, new ArrayList<>(dataPointIdentifiers), this.getProviderIdentifier());

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();

    for (String dslIdentifier : dataToFetch.keySet()) {
      Map<DataPointEntity, Set<String>> dataToFetchWithInputValues = new HashMap<>();
      dataToFetch.get(dslIdentifier)
          .forEach(dataPointEntity
              -> dataToFetchWithInputValues.put(
                  dataPointEntity, dataPointsAndInputValues.get(dataPointEntity.getIdentifier())));

      Map<String, Object> dataPointValues = new HashMap<>();
      for (Map.Entry<DataPointEntity, Set<String>> entry : dataToFetchWithInputValues.entrySet()) {
        DataPointEntity dataPoint = entry.getKey();
        Set<String> inputValues = entry.getValue();
        DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPoint.getIdentifier());
        Object values = dataPointParser.parseDataPoint(response, dataPoint, inputValues);
        if (values != null) {
          dataPointValues.put(dataPoint.getIdentifier(), values);
        }
      }

      if (aggregatedData.containsKey(getProviderIdentifier())) {
        aggregatedData.get(getProviderIdentifier()).putAll(dataPointValues);
      } else {
        aggregatedData.put(getProviderIdentifier(), dataPointValues);
      }
    }

    return aggregatedData;
  }
}
