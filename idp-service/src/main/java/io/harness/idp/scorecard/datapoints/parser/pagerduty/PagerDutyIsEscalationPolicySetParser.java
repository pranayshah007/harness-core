/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.parser.pagerduty;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PagerDutyIsEscalationPolicySetParser implements DataPointParser {
  private static final String ESCALATION_POLICY_RESPONSE_KEY = "escalation_policy";

  private static final String ERROR_MESSAGE_IF_NO_ESCALATION_POLICY_IS_SET =
      "Escalation policy is not set on PagerDuty for the entity";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    log.info("Parser for is escalation policy set is invoked data - {}, data point - {}, input values - {}", data,
        dataFetchDTO.getDataPoint(), dataFetchDTO.getInputValues());
    String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
    if (!isEmpty(errorMessage)) {
      return constructDataPointInfo(dataFetchDTO, null, errorMessage);
    }

    if (CommonUtils.findObjectByName(data, ESCALATION_POLICY_RESPONSE_KEY) != null) {
      return constructDataPointInfo(dataFetchDTO, true, null);
    }
    return constructDataPointInfo(dataFetchDTO, false, ERROR_MESSAGE_IF_NO_ESCALATION_POLICY_IS_SET);
  }
}