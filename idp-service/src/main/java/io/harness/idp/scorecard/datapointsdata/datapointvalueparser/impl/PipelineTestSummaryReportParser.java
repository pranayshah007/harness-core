/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineTestSummaryReport;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class PipelineTestSummaryReportParser implements PipelineTestSummaryReport {
  @Override
  public Map<String, Object> getParsedValue(JsonObject reportSummary, String dataPointIdentifier) {
    Map<String, Object> map = new HashMap<>();
    map.put(dataPointIdentifier, false);
    if (reportSummary != null) {
      int totalTestCases = reportSummary.get("total_tests").getAsInt();
      int failedTestCases = reportSummary.get("failed_tests").getAsInt();
      map.put(dataPointIdentifier, totalTestCases > 0 && failedTestCases == 0);
    }
    return map;
  }
}
