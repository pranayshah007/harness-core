/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.GITHUB_ADMIN_PERMISSION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;
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
public class GithubIsBranchProtectedParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, List<InputValue> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();

    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfoWithoutInputValue(null, INVALID_BRANCH_NAME_ERROR));
    }
    String inputValue = inputValues.get(0).getValue();
    data = (Map<String, Object>) data.get(inputValue);

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(
          constructDataPointInfo(inputValue, false, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    Map<String, Object> ref;
    if (CommonUtils.findObjectByName(data, "defaultBranchRef") == null
        && CommonUtils.findObjectByName(data, "ref") == null) {
      dataPointData.putAll(constructDataPointInfo(inputValue, false, INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    if (inputValue.equals(DEFAULT_BRANCH_KEY_ESCAPED)) {
      ref = (Map<String, Object>) CommonUtils.findObjectByName(data, "defaultBranchRef");
    } else {
      ref = (Map<String, Object>) CommonUtils.findObjectByName(data, "ref");
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
    return dataPointData;
  }
}
