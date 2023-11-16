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

import com.google.gson.internal.LinkedTreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class PagerDutyNoOfIncidentsInLastThirtyDaysParser implements DataPointParser {
  private static final String INCIDENTS_RESPONSE_KEY = "incidents";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());
    String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
    if (!isEmpty(errorMessage)) {
      return constructDataPointInfo(dataFetchDTO, null, errorMessage);
    }

    List<LinkedTreeMap> incidents = new ArrayList<>();

    if (CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY) != null) {
      incidents = (ArrayList) CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY);
    }

    return constructDataPointInfo(dataFetchDTO, incidents.size(), null);
  }
}