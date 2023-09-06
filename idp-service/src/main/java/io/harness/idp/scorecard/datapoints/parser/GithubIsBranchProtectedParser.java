/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
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
    if (CommonUtils.findObjectByName(data, "defaultBranchRef") == null
        && CommonUtils.findObjectByName(data, "ref") == null) {
      return constructDataPointInfo(inputValues, false, INVALID_BRANCH_NAME_ERROR);
    }
    Map<String, Object> branchProtectionRule =
        (Map<String, Object>) CommonUtils.findObjectByName(data, "branchProtectionRule");

    boolean value = false;
    String errorMessage = null;
    if (branchProtectionRule != null) {
      value = !(boolean) branchProtectionRule.get("allowsDeletions")
          && !(boolean) branchProtectionRule.get("allowsForcePushes");
    } else {
      errorMessage = GITHUB_ADMIN_PERMISSION_ERROR;
    }
    return constructDataPointInfo(inputValues, value, errorMessage);
  }

  private Map<String, Object> constructDataPointInfo(Set<String> inputValues, boolean value, String errorMessage) {
    Map<String, Object> data = new HashMap<>();
    data.put(DATA_POINT_VALUE_KEY, value);
    data.put(ERROR_MESSAGE_KEY, errorMessage);
    if (inputValues.isEmpty()) {
      return data;
    } else {
      String inputValue = inputValues.iterator().next();
      return Map.of(inputValue, data);
    }
  }
}
