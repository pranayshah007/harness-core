/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.github;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.SEVERITY_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmBaseDslNoLoop;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public class GithubOpenCodeScanningAlertsDsl extends ScmBaseDslNoLoop {
  private static final String SEVERITY_REPLACER = "{SEVERITY_REPLACER}";

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(SEVERITY_TYPE)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!inputValue.isEmpty()) {
        url = url.replace(SEVERITY_REPLACER, inputValue.toLowerCase());
      }
    }

    return url;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(String requestBody, DataPointEntity dataPoint,
      List<InputValue> inputValues, BackstageCatalogEntity backstageCatalogEntity) {
    return requestBody;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), List.class));
    } else if (response.getStatus() == 500) {
      ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
    } else {
      ruleData.put(ERROR_MESSAGE_KEY,
          GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
    }
    return ruleData;
  }
}
