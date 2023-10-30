/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class JiraIssuesCountParser implements DataPointParser {
  @Override
  public Object parseDataPoint(
      Map<String, Object> data, DataPointEntity dataPointIdentifier, List<InputValue> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfoWithoutInputValue(null, INVALID_FILE_NAME_ERROR));
    }
    String inputValue = inputValues.get(0).getValue();
    Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);

    if (isEmpty(inputValueData) || !isEmpty((String) inputValueData.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) inputValueData.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(
          constructDataPointInfo(inputValue, null, isEmpty(errorMessage) ? errorMessage : INVALID_CONDITIONAL_INPUT));
      return dataPointData;
    }

    double value = (double) CommonUtils.findObjectByName(inputValueData, "total");
    dataPointData.putAll(constructDataPointInfo(inputValue, value, null));
    return dataPointData;
  }
}
