/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.steps;

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
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
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

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CustomStageEnvironmentStep implements ChildrenExecutable<ServiceStepV3Parameters> {
  @Inject private EnvironmentService environmentService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CUSTOM_STAGE_ENVIRONMENT.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final String ENVIRONMENT_COMMAND_UNIT = "Environment Step";

  @Override
  public Class<ServiceStepV3Parameters> getStepParametersClass() {
    return ServiceStepV3Parameters.class;
  }

  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, ServiceStepV3Parameters parameters, StepInputPackage inputPackage) {
    try {
      final ParameterField<String> envRef = parameters.getEnvRef();
      if (ParameterField.isNull(envRef)) {
        throw new InvalidRequestException("Environment ref not found in stage yaml");
      }

      EnvironmentStepsUtils.checkForEnvAccessOrThrow(accessControlClient, ambiance, envRef);

      String finalEnvRef = (String) envRef.fetchFinalValue();

      log.info("Starting execution for Environment Step [{}]", finalEnvRef);

      final String accountId = AmbianceUtils.getAccountId(ambiance);
      final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

      Optional<Environment> environment =
          environmentService.get(accountId, orgIdentifier, projectIdentifier, finalEnvRef, false);
      if (environment.isEmpty()) {
        throw new InvalidRequestException(String.format("Environment with ref: [%s] not found", finalEnvRef));
      }

      // handle old environments
      if (isEmpty(environment.get().getYaml())) {
        serviceStepsHelper.setYamlInEnvironment(environment.get());
      }

      final NGLogCallback logCallback =
          new NGLogCallback(logStreamingStepClientFactory, ambiance, ENVIRONMENT_COMMAND_UNIT, true);

      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
          new EnumMap<>(ServiceOverridesType.class);

      boolean isOverridesV2enabled =
          overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);

      if (isOverridesV2enabled) {
        if (!ParameterField.isNull(parameters.getInfraId()) && parameters.getInfraId().isExpression()) {
          serviceStepsHelper.resolve(ambiance, parameters.getInfraId());
        }
        try {
          mergedOverrideV2Configs = serviceOverrideUtilityFacade.getMergedServiceOverrideConfigsForCustomStage(
              accountId, orgIdentifier, projectIdentifier, parameters, environment.get(), logCallback);
        } catch (IOException e) {
          throw new InvalidRequestException("An error occurred while resolving overrides", e);
        }
      }

      NGEnvironmentConfig ngEnvironmentConfig =
          serviceStepsHelper.getNgEnvironmentConfig(ambiance, parameters, accountId, environment);

      NGServiceOverrideConfig ngServiceOverrides = NGServiceOverrideConfig.builder().build();

      serviceStepsHelper.handleSecretVariables(
          ngEnvironmentConfig, ngServiceOverrides, mergedOverrideV2Configs, ambiance, isOverridesV2enabled);

      final EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(environment.get(),
          ngEnvironmentConfig, ngServiceOverrides, null, mergedOverrideV2Configs, isOverridesV2enabled);

      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

      serviceStepsHelper.processServiceAndEnvironmentVariables(
          ambiance, null, logCallback, environmentOutcome, isOverridesV2enabled, mergedOverrideV2Configs);

      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(emptyIfNull(StepUtils.generateLogKeys(
              StepUtils.generateLogAbstractions(ambiance), List.of(ENVIRONMENT_COMMAND_UNIT))))
          .addAllUnits(List.of(ENVIRONMENT_COMMAND_UNIT))
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

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, Map<String, ResponseData> responseDataMap) {
    long environmentStepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    StepResponse stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);

    final NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, ENVIRONMENT_COMMAND_UNIT, false);
    UnitProgress environmentStepUnitProgress = null;

    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      serviceStepsHelper.saveExecutionLog(logCallback,
          LogHelper.color("Failed to complete environment step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
      environmentStepUnitProgress = UnitProgress.newBuilder()
                                        .setStatus(UnitStatus.FAILURE)
                                        .setUnitName(ENVIRONMENT_COMMAND_UNIT)
                                        .setStartTime(environmentStepStartTs)
                                        .setEndTime(System.currentTimeMillis())
                                        .build();
    } else {
      serviceStepsHelper.saveExecutionLog(
          logCallback, "Completed environment step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      environmentStepUnitProgress = UnitProgress.newBuilder()
                                        .setStatus(UnitStatus.SUCCESS)
                                        .setUnitName(ENVIRONMENT_COMMAND_UNIT)
                                        .setStartTime(environmentStepStartTs)
                                        .setEndTime(System.currentTimeMillis())
                                        .build();
    }

    final EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutputExpressionConstants.ENVIRONMENT));

    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.ENVIRONMENT)
                         .outcome(environmentOutcome)
                         .group(StepCategory.STAGE.name())
                         .build());

    stepResponse = stepResponse.withStepOutcomes(stepOutcomes);
    serviceStepsHelper.saveServiceExecutionDataToStageInfo(ambiance, stepResponse);
    return stepResponse.toBuilder().unitProgressList(List.of(environmentStepUnitProgress)).build();
  }
}