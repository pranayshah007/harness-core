/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubWorkflowSuccessRateParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPointIdentifier, Set<String> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();
    for (String inputValue : inputValues) {
      if (!data.containsKey(inputValue)) {
        dataPointData.putAll(constructDataPointInfo(inputValue, 0, "Invalid workflow"));
        continue;
      }

      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      List<Map<String, Object>> runs =
          (List<Map<String, Object>>) CommonUtils.findObjectByName(inputValueData, "workflow_runs");
      if (isEmpty(runs)) {
        dataPointData.putAll(constructDataPointInfo(inputValue, 0, "No workflow runs found"));
        continue;
      }
      int numberOfRuns = runs.size();
      int successRuns = 0;
      for (Map<String, Object> run : runs) {
        String conclusion = (String) run.get("conclusion");
        if ("success".equals(conclusion)) {
          successRuns++;
        }
      }
      double value = (double) (successRuns * 100) / numberOfRuns;
      dataPointData.putAll(constructDataPointInfo(inputValue, value, null));
    }
    return dataPointData;
  }
}
