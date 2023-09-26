/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import io.harness.cdng.gitops.UpdateReleaseRepoStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GithubRestraintObserverImpl implements OrchestrationEventHandler {
  @Inject private GithubRestraintInstanceService githubRestraintInstanceService;
  @Inject private TransactionHelper transactionHelper;

  private void unblockConstraints(Ambiance ambiance) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      log.info("Update Active Github Resource constraints");
      final List<GithubRestraintInstance> githubRestraintInstances =
          githubRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(
              AmbianceUtils.obtainCurrentRuntimeId(ambiance));

      // TODO: Add a delay here
      log.info("Found {} active resource restraint instances", githubRestraintInstances.size());
      if (EmptyPredicate.isNotEmpty(githubRestraintInstances)) {
        for (GithubRestraintInstance ri : githubRestraintInstances) {
          transactionHelper.performTransaction(() -> {
            githubRestraintInstanceService.finishInstance(ri.getUuid());
            githubRestraintInstanceService.updateBlockedConstraints(ri.getResourceUnit());
            return null;
          });
        }
        log.info("Updated Blocked GithubResource constraints");
      }
    } catch (RuntimeException exception) {
      // Do not block the execution for possible exception in the RC update
      log.error("Something wrong with Github resource constraints update", exception);
    }
  }

  @Override
  public void handleEvent(OrchestrationEvent event) {
    if (UpdateReleaseRepoStep.STEP_TYPE.equals(AmbianceUtils.getCurrentStepType(event.getAmbiance()))
        && StatusUtils.isFinalStatus(event.getStatus())) {
      unblockConstraints(event.getAmbiance());
    }
  }
}
