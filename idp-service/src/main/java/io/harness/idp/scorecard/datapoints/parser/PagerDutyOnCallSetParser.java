/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PagerDutyOnCallSetParser implements DataPointParser {
  private static final String ON_CALL_RESPONSE_KEY = "on_call_now";

  private static final String ERROR_MESSAGE_IF_NO_ON_CALL_IS_SET = "On call is not set on PagerDuty for the entity";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    log.info("Parser for is on call set is invoked data - {}, data point - {}, input values - {}", data, dataPoint,
        inputValues);

    List onCalls = new ArrayList<>();
    if (CommonUtils.findObjectByName(data, ON_CALL_RESPONSE_KEY) != null) {
      onCalls = (ArrayList) CommonUtils.findObjectByName(data, ON_CALL_RESPONSE_KEY);
    }

    if (!onCalls.isEmpty()) {
      return constructDataPointInfoWithoutInputValue(true, null);
    }
    return constructDataPointInfoWithoutInputValue(false, ERROR_MESSAGE_IF_NO_ON_CALL_IS_SET);
  }
}