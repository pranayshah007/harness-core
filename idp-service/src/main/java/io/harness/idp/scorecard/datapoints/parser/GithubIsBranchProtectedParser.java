/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubIsBranchProtectedParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    Map<?, ?> result = (Map<?, ?>) data.get(dataPoint.getIdentifier());
    Map<String, Boolean> dataPointData = new HashMap<>();

    for (String inputValue : inputValues) {
      if (result.containsKey(inputValue)) {
        dataPointData.put(inputValue, (boolean) result.get(inputValue));
      }
    }
    return dataPointData;
  }

  @Override
  public String getReplaceKey(DataPointEntity entity) {
    return "{branch}";
  }
}
