/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public interface DataPointParser {
  Object parseDataPoint(Map<String, Object> data, DataFetchDTO inputValues);

  default Map<String, Object> constructDataPointInfo(DataFetchDTO dataFetchDTO, Object value, String errorMessage) {
    Map<String, Object> data = new HashMap<>();
    data.put(DATA_POINT_VALUE_KEY, value);
    data.put(ERROR_MESSAGE_KEY, errorMessage);
    return Map.of(dataFetchDTO.getRuleIdentifier(), data);
  }
}
