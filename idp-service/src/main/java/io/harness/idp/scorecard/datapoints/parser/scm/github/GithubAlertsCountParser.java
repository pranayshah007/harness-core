/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.github;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.constants.DataPoints;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GithubAlertsCountParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());
    if (inputValues.size() != 1) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      if (!isEmpty(errorMessage)) {
        return constructDataPointInfo(dataFetchDTO, null, errorMessage);
      }
      return constructDataPointInfo(dataFetchDTO, ((List<?>) data.get(DSL_RESPONSE)).size(), null);
    }

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : DataPoints.INVALID_CONDITIONAL_INPUT));
      return dataPointData;
    }
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, ((List<?>) data.get(DSL_RESPONSE)).size(), null));
    return dataPointData;
  }
}
