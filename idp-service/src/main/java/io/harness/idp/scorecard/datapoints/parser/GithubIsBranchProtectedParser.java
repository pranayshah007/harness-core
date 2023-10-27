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
      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      if (isEmpty(inputValueData) || !isEmpty((String) inputValueData.get(ERROR_MESSAGE_KEY))) {
        String errorMessage = (String) inputValueData.get(ERROR_MESSAGE_KEY);
        dataPointData.putAll(constructDataPointInfo(
            inputValue, false, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
        continue;
      }
      Map<String, Object> ref;
      if (CommonUtils.findObjectByName(inputValueData, "defaultBranchRef") == null
          && CommonUtils.findObjectByName(inputValueData, "ref") == null) {
        dataPointData.putAll(constructDataPointInfo(inputValue, false, INVALID_BRANCH_NAME_ERROR));
      } else {
        if (inputValue.equals(DEFAULT_BRANCH_KEY_ESCAPED)) {
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
}
