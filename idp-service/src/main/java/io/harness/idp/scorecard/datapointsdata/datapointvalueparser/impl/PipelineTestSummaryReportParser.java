/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineTestSummaryReport;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineTestSummaryReportParser implements PipelineTestSummaryReport {
  @Override
  public Map<String, Object> getParsedValue(
      JsonObject reportSummary, String dataPointIdentifier, String ciPipelineUrl) {
    Map<String, Object> dataPointInfo =
        ValueParserUtils.getDataPointsInfoMap(false, Collections.singletonList(ciPipelineUrl));

    if (reportSummary != null) {
      int totalTestCases = reportSummary.get("total_tests").getAsInt();
      log.info("Total number of test cases for ciPipeline - {}, pipeline link - {}", totalTestCases, ciPipelineUrl);
      int failedTestCases = reportSummary.get("failed_tests").getAsInt();
      log.info(
          "Total number of failed test cases for ciPipeline - {}, pipeline link - {}", failedTestCases, ciPipelineUrl);
      dataPointInfo.put(Constants.DATA_POINT_VALUE_KEY, totalTestCases > 0 && failedTestCases == 0);
    }
    Map<String, Object> returnMap = new HashMap<>();
    returnMap.put(dataPointIdentifier, dataPointInfo);
    log.info("Harness Data Source -> PipelineTestSummaryReportParser returned value {}", returnMap);
    return returnMap;
  }
}
