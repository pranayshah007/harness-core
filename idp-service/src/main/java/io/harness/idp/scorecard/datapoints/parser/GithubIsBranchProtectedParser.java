/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_DEFAULT_BRANCH_KEY;
import static io.harness.idp.common.Constants.GITHUB_DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.GITHUB_ADMIN_PERMISSION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubIsBranchProtectedParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();

    for (String inputValue : inputValues) {
      if (!data.containsKey(inputValue)) {
        if (inputValue.equals(GITHUB_DEFAULT_BRANCH_KEY_ESCAPED)) {
          dataPointData.putAll(constructDataPointInfo(GITHUB_DEFAULT_BRANCH_KEY, false, INVALID_BRANCH_NAME_ERROR));
        } else {
          dataPointData.putAll(constructDataPointInfo(inputValue, false, INVALID_BRANCH_NAME_ERROR));
        }
        continue;
      }

      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      Map<String, Object> ref;
      if (CommonUtils.findObjectByName(inputValueData, "defaultBranchRef") == null
          && CommonUtils.findObjectByName(inputValueData, "ref") == null) {
        dataPointData.putAll(constructDataPointInfo(inputValue, false, INVALID_BRANCH_NAME_ERROR));
      } else {
        if (inputValue.equals(GITHUB_DEFAULT_BRANCH_KEY_ESCAPED)) {
          ref = (Map<String, Object>) CommonUtils.findObjectByName(inputValueData, "defaultBranchRef");
        } else {
          ref = (Map<String, Object>) CommonUtils.findObjectByName(inputValueData, "ref");
        }
        Map<String, Object> branchProtectionRule = (Map<String, Object>) ref.get("branchProtectionRule");

        boolean value = false;
        String errorMessage = null;
        if (branchProtectionRule != null) {
          value = !(boolean) branchProtectionRule.get("allowsDeletions")
              && !(boolean) branchProtectionRule.get("allowsForcePushes");
        } else {
          errorMessage = GITHUB_ADMIN_PERMISSION_ERROR;
        }
        dataPointData.putAll(constructDataPointInfo(inputValue, value, errorMessage));
      }
    }
    return dataPointData;
  }

  private Map<String, Object> constructDataPointInfo(String inputValue, boolean value, String errorMessage) {
    Map<String, Object> data = new HashMap<>();
    data.put(DATA_POINT_VALUE_KEY, value);
    data.put(ERROR_MESSAGE_KEY, errorMessage);
    if (inputValue.equals(GITHUB_DEFAULT_BRANCH_KEY_ESCAPED)) {
      return Map.of(GITHUB_DEFAULT_BRANCH_KEY, data);
    } else {
      return Map.of(inputValue, data);
    }
  }
}
