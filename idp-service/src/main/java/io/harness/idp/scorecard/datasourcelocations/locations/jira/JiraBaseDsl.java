/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.jira;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGES_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.AUTHORIZATION_HEADER_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PROJECT_KEY_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.JQL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_COMPONENT_REPLACER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationNoLoop;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public abstract class JiraBaseDsl extends DataSourceLocationNoLoop {
  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs) {
    if (isEmpty(possibleReplaceableRequestBodyPairs.get(PROJECT_COMPONENT_REPLACER))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, PROJECT_KEY_ANNOTATION_ERROR));
      return false;
    }
    if (isEmpty(replaceableHeaders.get(AUTHORIZATION_HEADER))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, AUTHORIZATION_HEADER_ERROR));
      return false;
    }
    return true;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(String requestBody, DataPointEntity dataPoint,
      List<InputValue> inputValues, BackstageCatalogEntity backstageCatalogEntity) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(JQL)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      if (!inputValue.isEmpty()) {
        inputValue = inputValue.replaceFirst("\"", "");
        inputValue = inputValue.substring(0, inputValue.length() - 1);
        requestBody = requestBody.replace("{JQL_EXPRESSION}", inputValue);
      }
    }
    return requestBody;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return data.get(API_BASE_URL);
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else if (response.getStatus() == 500) {
      ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
    } else {
      ruleData.put(ERROR_MESSAGE_KEY,
          GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class)
              .get(ERROR_MESSAGES_KEY)
              .toString());
    }
    return ruleData;
  }
}
