/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.advise.handlers.EndPlanAdviserResponseHandler;
import io.harness.engine.pms.advise.handlers.IgnoreFailureAdviseHandler;
import io.harness.engine.pms.advise.handlers.InterventionWaitAdviserResponseHandler;
import io.harness.engine.pms.advise.handlers.MarkSuccessAdviseHandler;
import io.harness.engine.pms.advise.handlers.NextStepHandler;
import io.harness.engine.pms.advise.handlers.RetryAdviserResponseHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.AdviseType;

import com.google.inject.Inject;

@OwnedBy(CDC)
public class AdviseHandlerFactory {
  @Inject private NextStepHandler nextStepHandler;
  @Inject private RetryAdviserResponseHandler retryAdviseHandler;
  @Inject private EndPlanAdviserResponseHandler endPlanAdviseHandler;
  @Inject private InterventionWaitAdviserResponseHandler interventionWaitAdviseHandler;
  @Inject private MarkSuccessAdviseHandler markSuccessAdviseHandler;
  @Inject private IgnoreFailureAdviseHandler ignoreFailureAdviseHandler;

  public AdviserResponseHandler obtainHandler(AdviseType adviseType) {
    switch (adviseType) {
      case NEXT_STEP:
        return nextStepHandler;
      case RETRY:
        return retryAdviseHandler;
      case INTERVENTION_WAIT:
        return interventionWaitAdviseHandler;
      case END_PLAN:
        return endPlanAdviseHandler;
      case MARK_SUCCESS:
        return markSuccessAdviseHandler;
      case IGNORE_FAILURE:
        return ignoreFailureAdviseHandler;
      default:
        throw new InvalidRequestException("No handler Present for advise type: " + adviseType);
    }
  }
}
