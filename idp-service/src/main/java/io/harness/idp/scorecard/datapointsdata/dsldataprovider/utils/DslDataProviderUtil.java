/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;

import com.google.gson.Gson;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@UtilityClass
public class DslDataProviderUtil {
  public int getRunSequenceForPipelineExecution(Object executionResponse) {
    int runSequence = -1;
    if (executionResponse != null) {
      String jsonInStringExecutions = new Gson().toJson(executionResponse);
      JSONObject listOfPipelineExecutions = new JSONObject(jsonInStringExecutions);
      JSONArray pipelineExecutions = listOfPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
      if (pipelineExecutions.length() > 0) {
        JSONObject latestPipelineExecution = pipelineExecutions.getJSONObject(0);
        runSequence = latestPipelineExecution.getInt("runSequence");
      }
    }
    return runSequence;
  }

  public String getCdPipelineFromIdentifiers(Map<String, String> identifiersMap, String pipelineIdentifier) {
    return String.format(DslConstants.PIPELINE_URL, identifiersMap.get(DslConstants.CD_SERVICE_HOST),
        identifiersMap.get(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY),
        identifiersMap.get(DslConstants.CD_ORG_IDENTIFIER_KEY),
        identifiersMap.get(DslConstants.CD_PROJECT_IDENTIFIER_KEY), pipelineIdentifier);
  }
}
