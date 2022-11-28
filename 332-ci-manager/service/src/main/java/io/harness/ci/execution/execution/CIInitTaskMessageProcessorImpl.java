/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.execution;

import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIInitTaskMessageProcessorImpl implements CIInitTaskMessageProcessor {
  @Inject InitializeTaskStepV2 initializeTaskStepV2;
  @Inject CIBuildEnforcer buildEnforcer;
  @Inject @Named("ciInitTaskExecutor") ExecutorService initTaskExecutor;

  @Override
  public Boolean processMessage(DequeueResponse dequeueResponse) {
    try {
      byte[] payload = dequeueResponse.getPayload();
      CIExecutionArgs ciExecutionArgs = RecastOrchestrationUtils.fromBytes(payload, CIExecutionArgs.class);
      Ambiance ambiance = ciExecutionArgs.getAmbiance();
      if (!buildEnforcer.checkBuildEnforcement(AmbianceUtils.getAccountId(ambiance))) {
        log.info(String.format("skipping execution for account id: %s because of concurrency enforcement failure",
            AmbianceUtils.getAccountId(ambiance)));
        return false;
      }
      initTaskExecutor.submit(()
                                  -> initializeTaskStepV2.executeBuild(ambiance,
                                      ciExecutionArgs.getStepElementParameters(), ciExecutionArgs.getCallbackId()));
      return true;
    } catch (Exception ex) {
      log.info("ci init task processing failed", ex);
      return false;
    }
  }
}
