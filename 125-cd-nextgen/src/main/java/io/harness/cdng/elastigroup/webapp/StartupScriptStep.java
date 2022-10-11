/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.webapp;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.StartupCommandOutcome;
import io.harness.cdng.azure.webapp.StartupCommandParameters;
import io.harness.cdng.elastigroup.config.ElastigroupHelperService;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.ServiceStepsHelper;
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
import lombok.extern.slf4j.Slf4j;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_SCRIPT;

@OwnedBy(CDP)
@Slf4j
public class StartupScriptStep implements SyncExecutable<StartupScriptParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.STARTUP_SCRIPT.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @VisibleForTesting static final String ENTITY_TYPE = "Startup script";

  @Inject private ElastigroupHelperService elastigroupHelperService;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<StartupScriptParameters> getStepParametersClass() {
    return StartupScriptParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StartupScriptParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    logCallback.saveExecutionLog("Processing startup script...");
    StoreConfigWrapper storeConfig = stepParameters.getStartupScript().getStore();
    elastigroupHelperService.validateSettingsStoreReferences(storeConfig, ambiance, ENTITY_TYPE);
    logCallback.saveExecutionLog("Processed startup script");
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(STARTUP_SCRIPT)
                         .outcome(StartupScriptOutcome.builder().store(storeConfig.getSpec()).build())
                         .group(StepOutcomeGroup.STAGE.name())
                         .build())
        .build();
  }
}
