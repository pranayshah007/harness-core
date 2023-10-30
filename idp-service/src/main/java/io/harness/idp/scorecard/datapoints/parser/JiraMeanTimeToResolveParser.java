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
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.NO_ISSUES_FOUND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.DateUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class JiraMeanTimeToResolveParser implements DataPointParser {
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
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

    List<Map<String, Object>> issues =
        (List<Map<String, Object>>) CommonUtils.findObjectByName(inputValueData, "issues");
    if (isEmpty(issues)) {
      dataPointData.putAll(constructDataPointInfo(inputValue, null, NO_ISSUES_FOUND));
      return dataPointData;
    }

    int numberOfTickets = 0;
    long totalTimeToResolve = 0;
    for (Map<String, Object> issue : issues) {
      Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
      long createdAtMillis = DateUtils.parseTimestamp((String) fields.get("created"), DATE_FORMAT);
      String resolutionDate = (String) fields.get("resolutiondate");
      if (resolutionDate == null) {
        continue;
      }
      long resolvedAtMillis = DateUtils.parseTimestamp(resolutionDate, DATE_FORMAT);
      long timeToMergeMillis = resolvedAtMillis - createdAtMillis;
      totalTimeToResolve += timeToMergeMillis;
      numberOfTickets++;
    }

    if (numberOfTickets == 0) {
      dataPointData.putAll(constructDataPointInfo(inputValue, null, NO_ISSUES_FOUND));
    } else {
      double meanTimeToResolveMillis = (double) totalTimeToResolve / numberOfTickets;
      long value = (long) (meanTimeToResolveMillis / (60 * 60 * 1000));
      dataPointData.putAll(constructDataPointInfo(inputValue, value, null));
    }
    return dataPointData;
  }
}
