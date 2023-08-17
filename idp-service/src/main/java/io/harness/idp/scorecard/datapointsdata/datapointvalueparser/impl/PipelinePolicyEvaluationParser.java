/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineExecutionInfo;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class PipelinePolicyEvaluationParser implements PipelineExecutionInfo {
  private static final String CONTENT_KEY = "content";

  @Override
  public Map<String, Object> getParsedValue(Object response, String dataPointIdentifier) {
    String jsonInString = new Gson().toJson(response);
    JSONObject listOfPipelineExecutions = new JSONObject(jsonInString);
    JSONArray jsonArray = listOfPipelineExecutions.getJSONArray(CONTENT_KEY);
    Map<String, Object> returnData = new HashMap<>();

    if (jsonArray.length() > 0) {
      JSONObject latestPipelineExecution = jsonArray.getJSONObject(0);
      JSONObject governanceMetadata = (JSONObject) latestPipelineExecution.get("governanceMetadata");
      returnData.put(dataPointIdentifier, governanceMetadata.get("status").equals("pass"));
    } else {
      // no executions hence marking check as false
      returnData.put(dataPointIdentifier, false);
    }
    return returnData;
  }
}
