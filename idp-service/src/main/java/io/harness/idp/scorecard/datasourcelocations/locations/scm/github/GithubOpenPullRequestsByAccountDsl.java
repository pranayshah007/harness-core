/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.github;

import static io.harness.idp.scorecard.datapoints.constants.Inputs.ACCOUNT_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmBaseDslNoLoop;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class GithubOpenPullRequestsByAccountDsl extends ScmBaseDslNoLoop {
  private static final String AUTHOR_NAME = "{AUTHOR_NAME}";

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(String requestBody, DataPointEntity dataPoint,
      List<InputValue> inputValues, BackstageCatalogEntity backstageCatalogEntity) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(ACCOUNT_NAME)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      if (!inputValue.isEmpty()) {
        inputValue = inputValue.replace("\"", "");
        requestBody = requestBody.replace(AUTHOR_NAME, inputValue);
      }
    }
    return requestBody;
  }
}
