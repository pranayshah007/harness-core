/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.handlers.AbortAllInterruptHandler;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.handlers.CustomFailureInterruptHandler;
import io.harness.engine.interrupts.handlers.ExpireAllInterruptHandler;
import io.harness.engine.interrupts.handlers.IgnoreFailedInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkExpiredInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkFailedInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkSuccessInterruptHandler;
import io.harness.engine.interrupts.handlers.PauseAllInterruptHandler;
import io.harness.engine.interrupts.handlers.ProceedWithDefaultInterruptHandler;
import io.harness.engine.interrupts.handlers.ResumeAllInterruptHandler;
import io.harness.engine.interrupts.handlers.RetryInterruptHandler;
import io.harness.engine.interrupts.handlers.UserMarkedFailAllInterruptHandler;
import io.harness.pms.contracts.interrupts.InterruptType;

import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class InterruptHandlerFactory {
  @Inject private AbortAllInterruptHandler abortAllInterruptHandler;
  @Inject private PauseAllInterruptHandler pauseAllInterruptHandler;
  @Inject private ResumeAllInterruptHandler resumeAllInterruptHandler;
  @Inject private RetryInterruptHandler retryInterruptHandler;
  @Inject private MarkExpiredInterruptHandler markExpiredInterruptHandler;
  @Inject private MarkSuccessInterruptHandler markSuccessInterruptHandler;
  @Inject private MarkFailedInterruptHandler markFailedInterruptHandler;
  @Inject private IgnoreFailedInterruptHandler ignoreFailedInterruptHandler;
  @Inject private CustomFailureInterruptHandler customFailureInterruptHandler;
  @Inject private AbortInterruptHandler abortInterruptHandler;
  @Inject private ExpireAllInterruptHandler expireAllInterruptHandler;
  @Inject private ProceedWithDefaultInterruptHandler proceedWithDefaultInterruptHandler;
  @Inject private UserMarkedFailAllInterruptHandler userMarkedFailAllInterruptHandler;

  public InterruptHandler obtainHandler(InterruptType interruptType) {
    switch (interruptType) {
      case ABORT_ALL:
        return abortAllInterruptHandler;
      case PAUSE_ALL:
        return pauseAllInterruptHandler;
      case RESUME_ALL:
        return resumeAllInterruptHandler;
      case RETRY:
        return retryInterruptHandler;
      case MARK_EXPIRED:
        return markExpiredInterruptHandler;
      case MARK_SUCCESS:
        return markSuccessInterruptHandler;
      case IGNORE:
        return ignoreFailedInterruptHandler;
      case MARK_FAILED:
        return markFailedInterruptHandler;
      case CUSTOM_FAILURE:
        return customFailureInterruptHandler;
      case ABORT:
        return abortInterruptHandler;
      case EXPIRE_ALL:
        return expireAllInterruptHandler;
      case PROCEED_WITH_DEFAULT:
        return proceedWithDefaultInterruptHandler;
      case USER_MARKED_FAIL_ALL:
        return userMarkedFailAllInterruptHandler;
      default:
        throw new IllegalStateException("No Handler Available for Interrupt Type: " + interruptType);
    }
  }
}
