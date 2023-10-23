/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.steps;

import static io.harness.cdng.environment.constants.CustomStageEnvironmentStepConstants.ENVIRONMENT_STEP_COMMAND_UNIT;
import static io.harness.cdng.environment.constants.CustomStageEnvironmentStepConstants.OVERRIDES_COMMAND_UNIT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.helper.EnvironmentMapper;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.environment.helper.beans.CustomStageEnvironmentStepParameters;
import io.harness.cdng.service.steps.ServiceStepOverrideHelper;
import io.harness.cdng.service.steps.ServiceStepV3Helper;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CustomStageEnvironmentStep implements ChildrenExecutable<CustomStageEnvironmentStepParameters> {
  @Inject private EnvironmentService environmentService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private ServiceStepV3Helper serviceStepV3Helper;
  @Inject private ServiceStepOverrideHelper serviceStepOverrideHelper;

  @Override
  public Class<CustomStageEnvironmentStepParameters> getStepParametersClass() {
    return CustomStageEnvironmentStepParameters.class;
  }

  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, CustomStageEnvironmentStepParameters parameters, StepInputPackage inputPackage) {
    try {
      final String accountId = AmbianceUtils.getAccountId(ambiance);
      final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

      Optional<Environment> environment =
          getEnvironment(ambiance, parameters, accountId, orgIdentifier, projectIdentifier);

      final NGLogCallback environmentStepLogCallback = getLogCallback(ambiance, true, ENVIRONMENT_STEP_COMMAND_UNIT);
      final NGLogCallback overrideLogCallback = getLogCallback(ambiance, true, OVERRIDES_COMMAND_UNIT);

      boolean isOverridesV2enabled =
          overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);

      ServiceStepV3Parameters serviceStepV3Parameters = EnvironmentMapper.toServiceStepV3Parameters(parameters);

      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
          getMergedOverrideV2Configs(accountId, orgIdentifier, projectIdentifier, environment, overrideLogCallback,
              isOverridesV2enabled, serviceStepV3Parameters);

      NGEnvironmentConfig ngEnvironmentConfig =
          serviceStepV3Helper.getNgEnvironmentConfig(ambiance, serviceStepV3Parameters, accountId, environment);

      serviceStepV3Helper.handleSecretVariables(
          ngEnvironmentConfig, null, mergedOverrideV2Configs, ambiance, isOverridesV2enabled);

      final EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(
          environment.get(), ngEnvironmentConfig, null, null, mergedOverrideV2Configs, isOverridesV2enabled);

      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

      if (!ParameterField.isNull(parameters.getInfraId()) && parameters.getInfraId().isExpression()) {
        serviceStepV3Helper.resolve(ambiance, parameters.getInfraId());
      }

      serviceStepV3Helper.processServiceAndEnvironmentVariables(ambiance, null, environmentStepLogCallback,
          environmentOutcome, isOverridesV2enabled, mergedOverrideV2Configs);

      final String scopedEnvironmentRef =
          IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, environment.get().getOrgIdentifier(),
              environment.get().getProjectIdentifier(), environment.get().getIdentifier());

      /*this method saves other overrides i.e. manifests, config files, azure application settings, azure connection
      strings to sweeping output which are then later resolved and used by their respective steps
       */
      saveNonVariableOverridesToSweepingOutput(
          ambiance, scopedEnvironmentRef, isOverridesV2enabled, mergedOverrideV2Configs, ngEnvironmentConfig);

      Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
      entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(AmbianceUtils.getOrgIdentifier(ambiance)));
      entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(AmbianceUtils.getProjectIdentifier(ambiance)));
      entityMap.put(FreezeEntityType.PIPELINE, Lists.newArrayList(AmbianceUtils.getPipelineIdentifier(ambiance)));
      entityMap.put(FreezeEntityType.ENVIRONMENT,
          Lists.newArrayList(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.get().getAccountId(),
              environment.get().getOrgIdentifier(), environment.get().getProjectIdentifier(),
              environment.get().getIdentifier())));
      entityMap.put(FreezeEntityType.ENV_TYPE, Lists.newArrayList(environment.get().getType().name()));

      ChildrenExecutableResponse childrenExecutableResponse = serviceStepV3Helper.executeFreezePart(
          ambiance, entityMap, List.of(ENVIRONMENT_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT));
      if (childrenExecutableResponse != null) {
        return childrenExecutableResponse;
      }

      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(emptyIfNull(StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance),
              List.of(ENVIRONMENT_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT))))
          .addAllUnits(List.of(ENVIRONMENT_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT))
          .addAllChildren(parameters.getChildrenNodeIds()
                              .stream()
                              .map(id -> ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(id).build())
                              .collect(Collectors.toList()))
          .build();

    } catch (WingsException wingsException) {
      throw wingsException;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void saveNonVariableOverridesToSweepingOutput(Ambiance ambiance, String scopedEnvironmentRef,
      boolean isOverridesV2enabled, EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs,
      NGEnvironmentConfig ngEnvironmentConfig) {
    if (isOverridesV2enabled) {
      serviceStepOverrideHelper.saveFinalManifestsToSweepingOutputV2(ambiance,
          ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT, mergedOverrideV2Configs, scopedEnvironmentRef);
      serviceStepOverrideHelper.saveFinalConfigFilesToSweepingOutputV2(mergedOverrideV2Configs, scopedEnvironmentRef,
          ambiance, ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT);
      serviceStepOverrideHelper.saveFinalAppSettingsToSweepingOutputV2(
          mergedOverrideV2Configs, ambiance, ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT);
      serviceStepOverrideHelper.saveFinalConnectionStringsToSweepingOutputV2(
          mergedOverrideV2Configs, ambiance, ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT);
    } else {
      serviceStepOverrideHelper.prepareAndSaveFinalManifestMetadataToSweepingOutput(
          ngEnvironmentConfig, ambiance, ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalConfigFilesMetadataToSweepingOutput(
          ngEnvironmentConfig, ambiance, ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalAppSettingsMetadataToSweepingOutput(
          ngEnvironmentConfig, ambiance, ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalConnectionStringsMetadataToSweepingOutput(
          ngEnvironmentConfig, ambiance, ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT);
    }
  }

  @Override
  public StepResponse handleChildrenResponse(Ambiance ambiance, CustomStageEnvironmentStepParameters stepParameters,
      Map<String, ResponseData> responseDataMap) {
    long environmentStepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    final EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutputExpressionConstants.ENVIRONMENT));

    StepResponse stepResponse =
        serviceStepV3Helper.handleFreezeResponse(ambiance, environmentOutcome, OutcomeExpressionConstants.ENVIRONMENT);
    if (stepResponse != null) {
      return stepResponse;
    }

    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);

    final NGLogCallback logCallback = getLogCallback(ambiance, false, ENVIRONMENT_STEP_COMMAND_UNIT);

    UnitProgress environmentStepUnitProgress = null;

    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      serviceStepV3Helper.saveExecutionLog(logCallback,
          LogHelper.color("Failed to complete environment step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
      environmentStepUnitProgress = UnitProgress.newBuilder()
                                        .setStatus(UnitStatus.FAILURE)
                                        .setUnitName(ENVIRONMENT_STEP_COMMAND_UNIT)
                                        .setStartTime(environmentStepStartTs)
                                        .setEndTime(System.currentTimeMillis())
                                        .build();
    } else {
      serviceStepV3Helper.saveExecutionLog(
          logCallback, "Completed environment step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      environmentStepUnitProgress = UnitProgress.newBuilder()
                                        .setStatus(UnitStatus.SUCCESS)
                                        .setUnitName(ENVIRONMENT_STEP_COMMAND_UNIT)
                                        .setStartTime(environmentStepStartTs)
                                        .setEndTime(System.currentTimeMillis())
                                        .build();
    }

    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.ENVIRONMENT)
                         .outcome(environmentOutcome)
                         .group(StepCategory.STAGE.name())
                         .build());

    serviceStepV3Helper.addManifestsOutputToStepOutcome(ambiance, stepOutcomes);

    serviceStepV3Helper.addConfigFilesOutputToStepOutcome(ambiance, stepOutcomes);

    stepResponse = stepResponse.withStepOutcomes(stepOutcomes);

    serviceStepsHelper.saveEnvironmentExecutionDataToStageInfo(ambiance, stepResponse);

    UnitProgress overridesUnit = UnitProgress.newBuilder()
                                     .setStatus(UnitStatus.SUCCESS)
                                     .setUnitName(OVERRIDES_COMMAND_UNIT)
                                     .setStartTime(environmentStepStartTs)
                                     .setEndTime(System.currentTimeMillis())
                                     .build();

    return stepResponse.toBuilder().unitProgressList(List.of(environmentStepUnitProgress, overridesUnit)).build();
  }

  private EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> getMergedOverrideV2Configs(String accountId,
      String orgIdentifier, String projectIdentifier, Optional<Environment> environment,
      NGLogCallback overrideLogCallback, boolean isOverridesV2enabled,
      ServiceStepV3Parameters serviceStepV3Parameters) {
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
        new EnumMap<>(ServiceOverridesType.class);

    if (isOverridesV2enabled) {
      try {
        mergedOverrideV2Configs = serviceOverrideUtilityFacade.getMergedServiceOverrideConfigsForCustomStage(accountId,
            orgIdentifier, projectIdentifier, serviceStepV3Parameters, environment.get(), overrideLogCallback);
      } catch (IOException e) {
        throw new InvalidRequestException("An error occurred while resolving overrides", e);
      }
    }
    return mergedOverrideV2Configs;
  }

  @NotNull
  private Optional<Environment> getEnvironment(Ambiance ambiance, CustomStageEnvironmentStepParameters parameters,
      String accountId, String orgIdentifier, String projectIdentifier) {
    final ParameterField<String> envRef = parameters.getEnvRef();
    if (ParameterField.isNull(envRef)) {
      throw new InvalidRequestException("Environment ref not found in stage yaml");
    }

    String finalEnvRef = (String) envRef.fetchFinalValue();
    log.info("Starting execution for Environment Step [{}]", finalEnvRef);

    EnvironmentStepsUtils.checkForEnvAccessOrThrow(accessControlClient, ambiance, envRef);

    Optional<Environment> environment =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, finalEnvRef, false);
    if (environment.isEmpty()) {
      throw new InvalidRequestException(String.format("Environment with ref: [%s] not found", finalEnvRef));
    }

    // handle old environments
    if (isEmpty(environment.get().getYaml())) {
      serviceStepV3Helper.setYamlInEnvironment(environment.get());
    }
    return environment;
  }

  public NGLogCallback getLogCallback(Ambiance ambiance, boolean shouldOpenStream, String commandUnit) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, commandUnit, shouldOpenStream);
  }
}
