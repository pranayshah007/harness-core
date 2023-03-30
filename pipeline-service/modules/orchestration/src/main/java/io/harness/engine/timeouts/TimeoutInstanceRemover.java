/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.timeouts;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.logging.AutoLogContext;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.springdata.HMongoTemplate;
import io.harness.timeout.TimeoutEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class TimeoutInstanceRemover implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    Status status = nodeUpdateInfo.getStatus();

    // do not delete EXPIRED instances, persistent monitor is used for that
    if (status == Status.EXPIRED || !StatusUtils.isFinalStatus(status)) {
      return;
    }

    List<String> timeoutInstanceIds = nodeUpdateInfo.getNodeExecution().getTimeoutInstanceIds();

    try (AutoLogContext autoLogContext = obtainAutoLogContext(nodeUpdateInfo)) {
      boolean isSuccess = deleteTimeoutInstancesWithRetry(timeoutInstanceIds);
      if (isSuccess) {
        log.info("Timeout instances {} are removed successfully", timeoutInstanceIds);
      } else {
        log.error("Failed to delete timeout instances {}", timeoutInstanceIds);
      }
    } catch (Exception e) {
      log.error("Failed to delete timeout instances {}", timeoutInstanceIds);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }

  private AutoLogContext obtainAutoLogContext(NodeUpdateInfo nodeUpdateInfo) {
    return new AutoLogContext(ImmutableMap.of("planExecutionId", nodeUpdateInfo.getPlanExecutionId(), "nodeExecutionId",
                                  nodeUpdateInfo.getNodeExecutionId()),
        AutoLogContext.OverrideBehavior.OVERRIDE_NESTS);
  }

  private boolean deleteTimeoutInstancesWithRetry(List<String> timeoutInstanceIds) {
    return HMongoTemplate.retry(() -> {
      timeoutEngine.deleteTimeouts(timeoutInstanceIds);
      return true;
    });
  }
}
