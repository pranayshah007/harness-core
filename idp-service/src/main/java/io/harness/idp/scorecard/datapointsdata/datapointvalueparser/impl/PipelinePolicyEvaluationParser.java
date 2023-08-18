/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineExecutionInfo;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class PipelinePolicyEvaluationParser implements PipelineExecutionInfo {
  @Override
  public Map<String, Object> getParsedValue(Object responseCI, Object responseCD, String dataPointIdentifier) {
    boolean policyEvaluationCI = false;
    boolean policyEvaluationCD = false;
    Map<String, Object> returnData = new HashMap<>();

    String jsonInStringCI = new Gson().toJson(responseCI);
    JSONObject listOfCIPipelineExecutions = new JSONObject(jsonInStringCI);
    JSONArray ciPipelineExecutions = listOfCIPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
    if (ciPipelineExecutions.length() > 0) {
      JSONObject latestPipelineExecution = ciPipelineExecutions.getJSONObject(0);
      JSONObject governanceMetadata = (JSONObject) latestPipelineExecution.get("governanceMetadata");
      policyEvaluationCI = governanceMetadata.get("status").equals("pass");
    }

    String jsonInStringCD = new Gson().toJson(responseCD);
    JSONObject listOfCDPipelineExecutions = new JSONObject(jsonInStringCD);
    JSONArray cdPipelineExecutions = listOfCDPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
    if (cdPipelineExecutions.length() > 0) {
      JSONObject latestPipelineExecution = cdPipelineExecutions.getJSONObject(0);
      JSONObject governanceMetadata = (JSONObject) latestPipelineExecution.get("governanceMetadata");
      policyEvaluationCD = governanceMetadata.get("status").equals("pass");
    }

    returnData.put(dataPointIdentifier, policyEvaluationCI && policyEvaluationCD);
    return returnData;
  }
}
