/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.cdng.gitops.MergePRStep;
import io.harness.cdng.gitops.UpdateReleaseRepoStep;
import io.harness.cdng.gitops.resume.GitopsStepFinishCallback;
import io.harness.cdng.gitops.revertpr.RevertPRStep;
import io.harness.delay.DelayEventHelper;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitopsStepFinishHandler implements OrchestrationEventHandler {
  @Inject private DelayEventHelper delayEventHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  private static final long DELAY_IN_SECONDS = 5;
  private static final Set<StepType> STEP_TYPES_GIT_LOCK =
      Set.of(UpdateReleaseRepoStep.STEP_TYPE, MergePRStep.STEP_TYPE, RevertPRStep.STEP_TYPE);

  private void unblockConstraints(Ambiance ambiance) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String resumeId = delayEventHelper.delay(DELAY_IN_SECONDS, Collections.emptyMap());
      waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION,
          new GitopsStepFinishCallback(Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance))),
          resumeId);

      log.info("Updated Blocked GithubResource constraints");
    } catch (RuntimeException exception) {
      // Do not block the execution for possible exception in the RC update
      log.error("Something wrong with Github resource constraints update", exception);
    }
  }

  @Override
  public void handleEvent(OrchestrationEvent event) {
    if (STEP_TYPES_GIT_LOCK.contains(AmbianceUtils.getCurrentStepType(event.getAmbiance()))
        && StatusUtils.isFinalStatus(event.getStatus())) {
      unblockConstraints(event.getAmbiance());
    }
  }
}
