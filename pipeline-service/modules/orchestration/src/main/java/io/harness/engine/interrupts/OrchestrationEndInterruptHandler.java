/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.logging.AutoLogContext;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OrchestrationEndInterruptHandler implements AsyncInformObserver, OrchestrationEndObserver {
  @Inject private InterruptService interruptService;
  @Inject @Named("EngineExecutorService") ExecutorService executorService;

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      long closedInterrupts = interruptService.closeActiveInterrupts(ambiance.getPlanExecutionId());
      if (closedInterrupts < 0) {
        log.error("Error Closing out the interrupts");
        return;
      }
      log.info("Closed {} active interrupts", closedInterrupts);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
