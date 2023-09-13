/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public interface DataSourceLocation {
  Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity backstageCatalogEntity,
      DataSourceLocationEntity dataSourceLocationEntity, Map<DataPointEntity, Set<String>> dataPointsAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs);

  default void matchAndReplaceHeaders(Map<String, String> headers, Map<String, String> replaceableHeaders) {
    headers.forEach((k, v) -> {
      if (replaceableHeaders.containsKey(k)) {
        headers.put(k, replaceableHeaders.get(k));
      }
    });
  }

  default String replaceRequestBodyPlaceholdersIfAny(
      Map<String, String> possibleReplaceableRequestBodyPairs, String requestBody) {
    for (Map.Entry<String, String> entry : possibleReplaceableRequestBodyPairs.entrySet()) {
      requestBody = requestBody.replace(entry.getKey(), entry.getValue());
    }
    return requestBody;
  }

  default Map<String, Set<String>> convertDataPointEntityMapToDataPointIdMap(
      Map<DataPointEntity, Set<String>> dataPointsAndInputValues) {
    Map<String, Set<String>> dataPointIdsAndInputValues = new HashMap<>();
    dataPointsAndInputValues.forEach((k, v) -> dataPointIdsAndInputValues.put(k.getIdentifier(), v));
    return dataPointIdsAndInputValues;
  }
}
