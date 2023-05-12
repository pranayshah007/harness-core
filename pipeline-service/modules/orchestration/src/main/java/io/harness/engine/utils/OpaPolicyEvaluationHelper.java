/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaShouldEvaluateResponse;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OpaPolicyEvaluationHelper {
  @Inject OpaServiceClient opaServiceClient;

  public boolean shouldEvaluatePolicy(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String type, String action) {
    try {
      OpaShouldEvaluateResponse opaShouldEvaluateResponse =
          SafeHttpCall.executeWithExceptions(opaServiceClient.shouldEvaluateByTypeAndAction(
              accountIdentifier, orgIdentifier, projectIdentifier, type, action));
      return opaShouldEvaluateResponse.isShould_evaluate();
    } catch (Exception ex) {
      log.error("Exception while evaluating OPA rules", ex);
      throw new OPAPolicyEvaluationException("Exception while evaluating OPA rules: " + ex.getMessage(), ex);
    }
  }
}
