/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@UtilityClass
public class ValueParserUtils {
  public Map<String, Object> getDataPointsInfoMap(Object dataPointValue, List<String> pipelinesForErrorMessage) {
    Map<String, Object> dataPointsInfo = new HashMap<>();
    dataPointsInfo.put(Constants.DATA_POINT_VALUE_KEY, dataPointValue);
    dataPointsInfo.put(Constants.ERROR_MESSAGE_KEY, getErrorMessageForChecksFromPipelineUrls(pipelinesForErrorMessage));
    return dataPointsInfo;
  }

  public String getErrorMessageForChecksFromPipelineUrls(List<String> pipelineUrls) {
    if (pipelineUrls.isEmpty()) {
      return null;
    }
    String failureSummary = "Failure Summary - check is failing because of below pipelines\n";
    for (String pipelineUrl : pipelineUrls) {
      failureSummary = failureSummary + pipelineUrl + "\n";
    }
    return failureSummary;
  }
}
