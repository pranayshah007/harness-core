/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineExecutionInfo;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

@Slf4j
public class PipelinePolicyEvaluationParser implements PipelineExecutionInfo {
  @Override
  public Map<String, Object> getParsedValue(
      Object responseCI, Object responseCD, String dataPointIdentifier, String ciPipelineUrl, String cdPipelineUrl) {
    boolean policyEvaluationCI = false;
    boolean policyEvaluationCD = false;
    Map<String, Object> returnData = new HashMap<>();

    ArrayList<String> errorMessagePipelines = new ArrayList<>();
    if (responseCI != null) {
      policyEvaluationCI = isPolicyEvaluationSuccessfulForLatestPipelineExecution(responseCI);
      log.info("Policy evaluation for CI Pipeline - {}, pipeline link - {}", policyEvaluationCI, ciPipelineUrl);
      if (!policyEvaluationCI) {
        errorMessagePipelines.add(ciPipelineUrl);
      }
    }

    if (responseCD != null) {
      policyEvaluationCD = isPolicyEvaluationSuccessfulForLatestPipelineExecution(responseCD);
      log.info("Policy evaluation for CD Pipeline - {}, pipeline link - {}", policyEvaluationCD, cdPipelineUrl);
      if (!policyEvaluationCD) {
        errorMessagePipelines.add(cdPipelineUrl);
      }
    }

    Map<String, Object> dataPointInfo =
        ValueParserUtils.getDataPointsInfoMap(policyEvaluationCI && policyEvaluationCD, errorMessagePipelines);
    returnData.put(dataPointIdentifier, dataPointInfo);
    log.info("Returned data from PipelinePolicyEvaluationParser - {}", returnData);
    return returnData;
  }

  private boolean isPolicyEvaluationSuccessfulForLatestPipelineExecution(Object response) {
    String jsonInString = new Gson().toJson(response);
    JSONObject listOfPipelineExecutions = new JSONObject(jsonInString);
    JSONArray pipelineExecutions = listOfPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
    if (pipelineExecutions.length() > 0) {
      JSONObject latestPipelineExecution = pipelineExecutions.getJSONObject(0);
      JSONObject governanceMetadata = (JSONObject) latestPipelineExecution.get("governanceMetadata");
      log.info("Status for policy evaluation  - {} for execution - {}", governanceMetadata.get("status").equals("pass"),
          latestPipelineExecution);
      return governanceMetadata.get("status").equals("pass");
    }
    return false;
  }
}
