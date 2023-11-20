/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.github;

import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmBaseDslNoLoop;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class GithubFileExistsDsl extends ScmBaseDslNoLoop {
  private static final String GITHUB_FILE_EXISTS_REPLACER = "{GITHUB_FILE_EXISTS_REPLACER}";

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(String requestBody, DataPointEntity dataPoint,
      List<InputValue> inputValues, BackstageCatalogEntity backstageCatalogEntity) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(FILE_PATH)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      if (!inputValue.isEmpty()) {
        inputValue = inputValue.replace("\"", "");
        int lastSlash = inputValue.lastIndexOf("/");
        if (lastSlash != -1) {
          String path = inputValue.substring(0, lastSlash);
          requestBody = requestBody.replace(GITHUB_FILE_EXISTS_REPLACER, "HEAD:" + path);
        } else {
          requestBody = requestBody.replace(GITHUB_FILE_EXISTS_REPLACER, "HEAD:");
        }
      }
    }
    return requestBody;
  }
}
