/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubWorkflowsCountParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPointIdentifier, Set<String> inputValues) {
    String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
    if (!isEmpty(errorMessage)) {
      return constructDataPointInfoWithoutInputValue(null, errorMessage);
    }
    double count = (double) CommonUtils.findObjectByName(data, "total_count");
    return constructDataPointInfoWithoutInputValue(count, null);
  }
}
