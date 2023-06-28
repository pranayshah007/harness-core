/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.TMP_PATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.PluginStepMetadata;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.UnitStep;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PluginUtils {
  @Inject CIDelegateTaskExecutor taskExecutor;
  @Inject OutcomeService outcomeService;

  /**
   * This function is used to create the task data for the container steps.
   * It does the following:
   *    * Fetches the liteEnginePodDetails from outcome
   *    * Fetches the ip address of the pod
   *    * Fetches the delegate task data for execute step.
   * @param ambiance
   * @param unitStep
   * @param pluginStepMetadata
   * @return
   */
  public TaskData getDelegateTaskForPluginStep(
      Ambiance ambiance, UnitStep unitStep, PluginStepMetadata pluginStepMetadata) {
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome = (LiteEnginePodDetailsOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));
    String ip = liteEnginePodDetailsOutcome.getIpAddress();

    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);

    ExecuteStepRequest executeStepRequest = ExecuteStepRequest.newBuilder()
                                                .setExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                                .setExecutionId(runtimeId)
                                                .setStep(unitStep)
                                                .setTmpFilePath(TMP_PATH)
                                                .build();
    CIK8ExecuteStepTaskParams params = CIK8ExecuteStepTaskParams.builder()
                                           .ip(ip)
                                           .port(LITE_ENGINE_PORT)
                                           .serializedStep(executeStepRequest.toByteArray())
                                           .isLocal(pluginStepMetadata.isLocal())
                                           .delegateSvcEndpoint(pluginStepMetadata.getDelegateServiceEndpoint())
                                           .build();
    return taskExecutor.getDelegateTaskDataForExecuteStep(ambiance, pluginStepMetadata.getTimeout(), params);
  }
}
