/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.execution.NodeExecution;
import io.harness.logging.AutoLogContext;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ResourceRestraintObserver
    implements OrchestrationEndObserver, NodeStatusUpdateObserver, AsyncInformObserver {
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  @Inject private ResourceRestraintInstanceService restraintService;
  @Inject private TransactionHelper transactionHelper;

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    unblockConstraints(ambiance, ambiance.getPlanExecutionId());
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
    if (nodeExecution.getStepType().getStepCategory() != StepCategory.STAGE
        || !StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
      return;
    }
    unblockConstraints(nodeExecution.getAmbiance(), nodeExecution.getUuid());
  }

  private void unblockConstraints(Ambiance ambiance, String releaseEntityId) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      log.info("Update Active Resource constraints");
      final List<ResourceRestraintInstance> restraintInstances =
          restraintService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId);

      log.info("Found {} active resource restraint instances", restraintInstances.size());
      if (EmptyPredicate.isNotEmpty(restraintInstances)) {
        for (ResourceRestraintInstance ri : restraintInstances) {
          transactionHelper.performTransaction(() -> {
            restraintService.finishInstance(ri.getUuid(), ri.getResourceUnit());
            restraintService.updateBlockedConstraints(ImmutableSet.of(ri.getResourceRestraintId()));
            return null;
          });
        }
        log.info("Updated Blocked Resource constraints");
      }
    } catch (RuntimeException exception) {
      // Do not block the execution for possible exception in the RC update
      log.error("Something wrong with resource constraints update", exception);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
