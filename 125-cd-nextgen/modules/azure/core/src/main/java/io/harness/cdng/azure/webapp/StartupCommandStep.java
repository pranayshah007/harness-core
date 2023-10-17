/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.StartupCommandOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class StartupCommandStep implements SyncExecutable<StartupCommandParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.STARTUP_COMMAND.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @VisibleForTesting static final String ENTITY_TYPE = "Startup command";

  @Inject private AzureHelperService azureHelperService;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<StartupCommandParameters> getStepParametersClass() {
    return StartupCommandParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StartupCommandParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    logCallback.saveExecutionLog("Processing startup command...");
    StoreConfigWrapper storeConfig = stepParameters.getStartupCommand().getStore();
    azureHelperService.validateSettingsStoreReferences(storeConfig, ambiance, ENTITY_TYPE);
    azureHelperService.publishSecretRuntimeUsage(ambiance, storeConfig);
    logCallback.saveExecutionLog("Processed startup command");
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(STARTUP_COMMAND)
                         .outcome(StartupCommandOutcome.builder().store(storeConfig.getSpec()).build())
                         .group(StepOutcomeGroup.STAGE.name())
                         .build())
        .build();
  }
}
