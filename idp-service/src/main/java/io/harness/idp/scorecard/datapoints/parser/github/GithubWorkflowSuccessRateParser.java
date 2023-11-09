/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.github;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GithubWorkflowSuccessRateParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_FILE_NAME_ERROR));
    }
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : INVALID_CONDITIONAL_INPUT));
      return dataPointData;
    }

    List<Map<String, Object>> runs = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "workflow_runs");
    if (isEmpty(runs)) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, "No workflow runs found"));
      return dataPointData;
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
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, value, null));
    return dataPointData;
  }
}
