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
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_PATTERN;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.PATTERN;

import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GithubFileParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    if (inputValues.size() >= 2) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_CONDITIONAL_INPUT));
    }

    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(
          constructDataPointInfo(dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : INVALID_FILE_NAME_ERROR));
      return dataPointData;
    }

    if (CommonUtils.findObjectByName(data, "object") == null) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_FILE_NAME_ERROR));
      return dataPointData;
    }

    String text = (String) CommonUtils.findObjectByName(data, "text");
    Optional<InputValue> patternOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(PATTERN)).findFirst();
    if (patternOpt.isEmpty()) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_PATTERN));
      return dataPointData;
    }

    String regex = patternOpt.get().getValue();
    if (regex.startsWith("\"") && regex.endsWith("\"")) {
      regex = regex.substring(1, regex.length() - 1);
    }
    Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(text);

    Object value = this.parseRegex(matcher);
    String errorMessage = null;
    if (value == null || value == Boolean.FALSE) {
      errorMessage = INVALID_PATTERN;
    }
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, value, errorMessage));

    return dataPointData;
  }

  abstract Object parseRegex(Matcher matcher);
}
