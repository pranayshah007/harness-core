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
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_PATTERN;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.PATTERN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.IDP)
public class GithubFileContentsParser implements DataPointParser {
  @Override
  public Object parseDataPoint(
      Map<String, Object> data, DataPointEntity dataPointIdentifier, List<InputValue> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (inputValues.size() != 3) {
      dataPointData.putAll(constructDataPointInfoWithoutInputValue(null, INVALID_CONDITIONAL_INPUT));
    }

    for (InputValue inputValue : inputValues) {
      data = (Map<String, Object>) data.get(inputValue.getValue());
    }

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(
          constructDataPointInfo(inputValues, null, !isEmpty(errorMessage) ? errorMessage : INVALID_FILE_NAME_ERROR));
      return dataPointData;
    }

    if (CommonUtils.findObjectByName(data, "object") == null) {
      dataPointData.putAll(constructDataPointInfo(inputValues, null, INVALID_FILE_NAME_ERROR));
      return dataPointData;
    }

    String text = (String) CommonUtils.findObjectByName(data, "text");
    Optional<InputValue> patternOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(PATTERN)).findFirst();
    if (patternOpt.isEmpty()) {
      dataPointData.putAll(constructDataPointInfo(inputValues, null, INVALID_PATTERN));
      return dataPointData;
    }

    String regex = patternOpt.get().getValue().replace("\"", "");
    Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(text);

    if (matcher.find()) {
      String capturedValue = matcher.group(1);
      dataPointData.putAll(constructDataPointInfo(inputValues, capturedValue, null));
    } else {
      dataPointData.putAll(constructDataPointInfo(inputValues, null, INVALID_PATTERN));
    }

    return dataPointData;
  }
}
