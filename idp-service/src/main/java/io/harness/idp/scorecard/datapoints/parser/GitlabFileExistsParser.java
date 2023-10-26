/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GitlabFileExistsParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPointIdentifier, Set<String> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();

    for (String inputValue : inputValues) {
      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      if (isEmpty(inputValueData) || !isEmpty((String) inputValueData.get(ERROR_MESSAGE_KEY))) {
        String errorMessage = (String) inputValueData.get(ERROR_MESSAGE_KEY);
        dataPointData.putAll(
            constructDataPointInfo(inputValue, false, !isEmpty(errorMessage) ? errorMessage : INVALID_FILE_NAME_ERROR));
        continue;
      }
      List<Map<String, Object>> nodes =
          (List<Map<String, Object>>) CommonUtils.findObjectByName(inputValueData, "nodes");
      boolean isPresent = false;
      inputValue = inputValue.replace("\"", "");
      int lastSlash = inputValue.lastIndexOf("/");
      String inputFile = (lastSlash != -1) ? inputValue.substring(lastSlash + 1) : inputValue;
      for (Map<String, Object> node : nodes) {
        String fileName = (String) node.get("name");
        if (fileName.equals(inputFile)) {
          isPresent = true;
          break;
        }
      }
      dataPointData.putAll(constructDataPointInfo(inputValue, isPresent, null));
    }
    return dataPointData;
  }
}
