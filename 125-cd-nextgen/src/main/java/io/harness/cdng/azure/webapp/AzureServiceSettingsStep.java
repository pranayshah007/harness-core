/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.CONNECTION_STRINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
import io.harness.cdng.azure.config.ConnectionStringsOutcome;
import io.harness.cdng.azure.config.StartupCommandOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/*
Single step for Application settings, Connection Strings, Startup command
 */
@Slf4j
public class AzureServiceSettingsStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_SERVICE_SETTINGS_STEP.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private AzureHelperService azureHelperService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver expressionResolver;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final Optional<NGServiceV2InfoConfig> serviceOptional = cdStepHelper.fetchServiceConfigFromSweepingOutput(ambiance);
    if (serviceOptional.isEmpty()) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    NGServiceV2InfoConfig service = serviceOptional.get();
    if (!ServiceDefinitionType.AZURE_WEBAPP.getYamlName().equals(
            service.getServiceDefinition().getServiceSpec().getType())) {
      log.info("skipping AzureServiceSettingsStep because service is not of type azure web app");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final AzureWebAppServiceSpec serviceSpec = (AzureWebAppServiceSpec) service.getServiceDefinition().getServiceSpec();

    expressionResolver.updateExpressions(ambiance, serviceSpec);

    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);

    final List<StepResponse.StepOutcome> outcomes = new ArrayList<>();

    // Process azure settings
    if (serviceSpec.getStartupCommand() != null) {
      outcomes.add(processStartupCommand(ambiance, serviceSpec, logCallback));
    }
    if (serviceSpec.getApplicationSettings() != null) {
      outcomes.add(processApplicationSettings(ambiance, serviceSpec, logCallback));
    }
    if (serviceSpec.getConnectionStrings() != null) {
      outcomes.add(processConnectionStrings(ambiance, serviceSpec, logCallback));
    }

    return StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(outcomes).build();
  }

  private StepResponse.StepOutcome processConnectionStrings(
      Ambiance ambiance, AzureWebAppServiceSpec serviceSpec, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing connection strings...");
    StoreConfigWrapper storeConfig = serviceSpec.getConnectionStrings().getStore();
    azureHelperService.validateSettingsStoreReferences(storeConfig, ambiance, "Connection strings");
    saveExecutionLog(logCallback, "Processed connection strings");
    return StepResponse.StepOutcome.builder()
        .name(CONNECTION_STRINGS)
        .outcome(ConnectionStringsOutcome.builder().store(storeConfig.getSpec()).build())
        .group(StepCategory.STAGE.name())
        .build();
  }

  private StepResponse.StepOutcome processApplicationSettings(
      Ambiance ambiance, AzureWebAppServiceSpec serviceSpec, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing application settings...");
    StoreConfigWrapper storeConfig = serviceSpec.getApplicationSettings().getStore();
    azureHelperService.validateSettingsStoreReferences(storeConfig, ambiance, "Application settings");
    saveExecutionLog(logCallback, "Processed application settings");
    return StepResponse.StepOutcome.builder()
        .name(APPLICATION_SETTINGS)
        .outcome(ApplicationSettingsOutcome.builder().store(storeConfig.getSpec()).build())
        .group(StepCategory.STAGE.name())
        .build();
  }

  private StepResponse.StepOutcome processStartupCommand(
      Ambiance ambiance, AzureWebAppServiceSpec serviceSpec, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing startup command...");
    StoreConfigWrapper storeConfig = serviceSpec.getStartupCommand().getStore();
    azureHelperService.validateSettingsStoreReferences(storeConfig, ambiance, "Startup command");
    saveExecutionLog(logCallback, "Processed startup command");
    return StepResponse.StepOutcome.builder()
        .name(STARTUP_COMMAND)
        .outcome(StartupCommandOutcome.builder().store(storeConfig.getSpec()).build())
        .group(StepCategory.STAGE.name())
        .build();
  }

  private void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }
}
