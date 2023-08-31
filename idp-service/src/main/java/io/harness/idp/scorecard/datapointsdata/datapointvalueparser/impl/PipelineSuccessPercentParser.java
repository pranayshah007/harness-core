/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineSuccessPercent;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class PipelineSuccessPercentParser implements PipelineSuccessPercent {
  @Override
  public Map<String, Object> getParsedValue(
      Object dashboardPipelineHealthInfoObject, String dataPointIdentifier, String ciPipelineUrl) {
    Object percentage = 0;
    if (dashboardPipelineHealthInfoObject != null) {
      String jsonInString = new Gson().toJson(dashboardPipelineHealthInfoObject);
      JSONObject pipelineExecutionHealthResponse = new JSONObject(jsonInString);
      JSONObject executions = (JSONObject) pipelineExecutionHealthResponse.get("executions");
      JSONObject success = (JSONObject) executions.get("success");
      percentage = success.get("percent");
    }

    Map<String, Object> dataPointInfo =
        ValueParserUtils.getDataPointsInfoMap(0, Collections.singletonList(ciPipelineUrl));
    if (dashboardPipelineHealthInfoObject != null) {
      dataPointInfo.put(Constants.DATA_POINT_VALUE_KEY, percentage);
    }
    Map<String, Object> returnMap = new HashMap<>();
    returnMap.put(dataPointIdentifier, dataPointInfo);
    return returnMap;
  }
}
