/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubAlertsCountParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPointIdentifier, Set<String> inputValues) {
    if (isEmpty(inputValues)) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      if (!isEmpty(errorMessage)) {
        return constructDataPointInfoWithoutInputValue(null, errorMessage);
      }
      return constructDataPointInfoWithoutInputValue(((List<?>) data.get(DSL_RESPONSE)).size(), null);
    }
    Map<String, Object> dataPointData = new HashMap<>();
    for (String inputValue : inputValues) {
      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      if (isEmpty(inputValueData) || !isEmpty((String) inputValueData.get(ERROR_MESSAGE_KEY))) {
        String errorMessage = (String) inputValueData.get(ERROR_MESSAGE_KEY);
        dataPointData.putAll(constructDataPointInfo(
            inputValue, null, !isEmpty(errorMessage) ? errorMessage : INVALID_CONDITIONAL_INPUT));
        continue;
      }
      dataPointData.putAll(
          constructDataPointInfo(inputValue, ((List<?>) inputValueData.get(DSL_RESPONSE)).size(), null));
    }
    return dataPointData;
  }
}
