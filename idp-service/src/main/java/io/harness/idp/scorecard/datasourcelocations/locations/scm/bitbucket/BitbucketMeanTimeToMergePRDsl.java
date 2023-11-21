/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket;

import static io.harness.idp.common.Constants.DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class BitbucketMeanTimeToMergePRDsl extends BitbucketBaseDsl {
  private static final String DESTINATION_BRANCH_NAME_REPLACER = "{DESTINATION_BRANCH_NAME_REPLACER}";

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(BRANCH_NAME)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!inputValue.isEmpty()) {
        if (!inputValue.equals(DEFAULT_BRANCH_KEY_ESCAPED)) {
          url = url.replace(DESTINATION_BRANCH_NAME_REPLACER,
              " AND destination.branch.name="
                  + "\"" + inputValue + "\"");
        } else {
          url = url.replace(DESTINATION_BRANCH_NAME_REPLACER, "");
        }
      }
    }
    return url;
  }
}
