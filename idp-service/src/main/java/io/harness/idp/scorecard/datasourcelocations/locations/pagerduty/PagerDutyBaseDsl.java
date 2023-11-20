/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.pagerduty;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_ANNOTATION_MISSING_ERROR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_SERVICE_ID;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_TARGET_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationLoop;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public abstract class PagerDutyBaseDsl extends DataSourceLocationLoop {
  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs) {
    if (isEmpty(replaceableHeaders.get(AUTHORIZATION_HEADER))) {
      data.put(
          dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE));
      return false;
    }
    if (isEmpty(possibleReplaceableRequestBodyPairs.get(PAGERDUTY_SERVICE_ID))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, PAGERDUTY_ANNOTATION_MISSING_ERROR));
      return false;
    }
    if (isEmpty(possibleReplaceableRequestBodyPairs.get(PAGERDUTY_TARGET_URL))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE));
      return false;
    }
    return true;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, List<DataFetchDTO> dataPointsAndInputValues, BackstageCatalogEntity backstageCatalogEntity) {
    return requestBody;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else if (response.getStatus() == 401) {
      ruleData.put(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE);
    } else if (response.getStatus() == 500) {
      ruleData.put(ERROR_MESSAGE_KEY, PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE);
    } else {
      ruleData.put(ERROR_MESSAGE_KEY, PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE);
    }
    return ruleData;
  }
}
