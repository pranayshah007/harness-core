/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubFileExistsParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPointIdentifier, Set<String> inputValues) {
    String inputFile = inputValues.iterator().next();
    if (CommonUtils.findObjectByName(data, "object") == null) {
      return constructDataPointInfo(inputFile, false, INVALID_BRANCH_NAME_ERROR);
    }
    List<Map<String, Object>> entries = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "entries");
    boolean isPresent = false;
    inputFile = inputFile.replace("\"", "");
    for (Map<String, Object> entry : entries) {
      String fileName = (String) entry.get("name");
      if (fileName.equals(inputFile)) {
        isPresent = true;
        break;
      }
    }
    return constructDataPointInfo(inputFile, isPresent, null);
  }

  private Map<String, Object> constructDataPointInfo(String inputValue, boolean value, String errorMessage) {
    Map<String, Object> data = new HashMap<>() {
      {
        put(DATA_POINT_VALUE_KEY, value);
        put(ERROR_MESSAGE_KEY, errorMessage);
      }
    };
    return Map.of(inputValue, data);
  }
}
