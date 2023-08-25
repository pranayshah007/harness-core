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

    if (responseCI != null) {
      policyEvaluationCI = isPolicyEvaluationSuccessfulForLatestPipelineExecution(responseCI);
    }

    if (responseCD != null) {
      policyEvaluationCD = isPolicyEvaluationSuccessfulForLatestPipelineExecution(responseCD);
    }

    returnData.put(dataPointIdentifier, policyEvaluationCI && policyEvaluationCD);
    return returnData;
  }

  private boolean isPolicyEvaluationSuccessfulForLatestPipelineExecution(Object response) {
    String jsonInString = new Gson().toJson(response);
    JSONObject listOfPipelineExecutions = new JSONObject(jsonInString);
    JSONArray pipelineExecutions = listOfPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
    if (pipelineExecutions.length() > 0) {
      JSONObject latestPipelineExecution = pipelineExecutions.getJSONObject(0);
      JSONObject governanceMetadata = (JSONObject) latestPipelineExecution.get("governanceMetadata");
      return governanceMetadata.get("status").equals("pass");
    }
    return false;
  }
}
