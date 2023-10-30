/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
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
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.steps.EntityReferenceExtractorUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class ApplicationSettingsStep implements SyncExecutable<ApplicationSettingsParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.APPLICATION_SETTINGS.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @VisibleForTesting static final String ENTITY_TYPE = "Application settings";

  @Inject private AzureHelperService azureHelperService;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<ApplicationSettingsParameters> getStepParametersClass() {
    return ApplicationSettingsParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ApplicationSettingsParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    logCallback.saveExecutionLog("Processing application settings...");
    StoreConfigWrapper storeConfig = stepParameters.getApplicationSettings().getStore();
    azureHelperService.validateSettingsStoreReferences(storeConfig, ambiance, ENTITY_TYPE);
    azureHelperService.publishSecretRuntimeUsage(ambiance, storeConfig);
    logCallback.saveExecutionLog("Processed application settings");
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(APPLICATION_SETTINGS)
                         .outcome(ApplicationSettingsOutcome.builder().store(storeConfig.getSpec()).build())
                         .group(StepOutcomeGroup.STAGE.name())
                         .build())
        .build();
  }
}
