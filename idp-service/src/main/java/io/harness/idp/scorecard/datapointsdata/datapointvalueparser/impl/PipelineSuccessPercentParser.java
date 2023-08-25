/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineSuccessPercent;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class PipelineSuccessPercentParser implements PipelineSuccessPercent {
  @Override
  public Map<String, Object> getParsedValue(Object dashboardPipelineHealthInfoObject, String dataPointIdentifier) {
    String jsonInString = new Gson().toJson(dashboardPipelineHealthInfoObject);
    JSONObject pipelineExecutionHealthResponse = new JSONObject(jsonInString);
    JSONObject executions = (JSONObject) pipelineExecutionHealthResponse.get("executions");
    JSONObject success = (JSONObject) executions.get("success");

    Map<String, Object> map = new HashMap<>();
    map.put(dataPointIdentifier, 0);
    if (dashboardPipelineHealthInfoObject != null) {
      map.put(dataPointIdentifier, success.get("percent"));
    }
    return map;
  }
}
