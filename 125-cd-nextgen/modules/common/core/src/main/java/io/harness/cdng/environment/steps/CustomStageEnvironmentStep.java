/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.steps;

import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.environment.helper.EnvironmentMapper;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
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
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
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
  @Inject private EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  @Inject private CDExpressionResolver expressionResolver;

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
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, StepInputPackage inputPackage) {
    try {
      final String accountId = AmbianceUtils.getAccountId(ambiance);
      final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

      final ParameterField<String> envRef = stepParameters.getEnvRef();
      final ParameterField<Map<String, Object>> envInputs = stepParameters.getEnvInputs();
      if (ParameterField.isNull(envRef)) {
        throw new InvalidRequestException("Environment ref not found in stage yaml");
      }

      if (envRef.isExpression()) {
        resolve(ambiance, envRef);
      }

      log.info("Starting execution for Environment Step [{}]", envRef.getValue());

      EnvironmentStepsUtils.checkForEnvAccessOrThrow(accessControlClient, ambiance, envRef);

      if (envRef.fetchFinalValue() != null) {
        Optional<Environment> environment =
            environmentService.get(accountId, orgIdentifier, projectIdentifier, envRef.getValue(), false);
        if (environment.isEmpty()) {
          throw new InvalidRequestException(String.format("Environment with ref: [%s] not found", envRef.getValue()));
        }

        final NGLogCallback logCallback =
            new NGLogCallback(logStreamingStepClientFactory, ambiance, ENVIRONMENT_COMMAND_UNIT, true);

        EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
            new EnumMap<>(ServiceOverridesType.class);

        boolean isOverridesV2enabled =
            overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);

        if (isOverridesV2enabled) {
          if (!ParameterField.isNull(stepParameters.getInfraId()) && stepParameters.getInfraId().isExpression()) {
            resolve(ambiance, stepParameters.getInfraId());
          }
          try {
            mergedOverrideV2Configs = serviceOverrideUtilityFacade.getMergedServiceOverrideConfigsForCustomStage(
                accountId, orgIdentifier, projectIdentifier, stepParameters, environment.get(), logCallback);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        // handle old environments
        if (isEmpty(environment.get().getYaml())) {
          setYamlInEnvironment(environment.get());
        }

        NGEnvironmentConfig ngEnvironmentConfig;
        try {
          ngEnvironmentConfig = mergeEnvironmentInputs(
              accountId, environment.get().getIdentifier(), environment.get().getYaml(), envInputs);
        } catch (IOException ex) {
          throw new InvalidRequestException(
              "Unable to read yaml for environment: " + environment.get().getIdentifier(), ex);
        }
        resolve(ambiance, ngEnvironmentConfig);

        List<NGVariable> secretNGVariables = new ArrayList<>();
        if (isOverridesV2enabled) {
          if (ngEnvironmentConfig != null && ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
              && !mergedOverrideV2Configs.containsKey(ENV_GLOBAL_OVERRIDE)) {
            mergedOverrideV2Configs.put(ENV_GLOBAL_OVERRIDE, toOverrideConfigV2(ngEnvironmentConfig, accountId));
          }
          secretNGVariables = getSecretVariablesFromOverridesV2(mergedOverrideV2Configs);

        } else {
          if (ngEnvironmentConfig != null && ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
              && ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables() != null) {
            secretNGVariables.addAll(ngEnvironmentConfig.getNgEnvironmentInfoConfig()
                                         .getVariables()
                                         .stream()
                                         .filter(SecretNGVariable.class ::isInstance)
                                         .collect(Collectors.toList()));
          }
        }
        serviceStepsHelper.checkForAccessOrThrow(ambiance, secretNGVariables);

        NGServiceOverrideConfig ngServiceOverrides =
            NGServiceOverrideConfig.builder().serviceOverrideInfoConfig(null).build();

        final EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(environment.get(),
            ngEnvironmentConfig, ngServiceOverrides, null, mergedOverrideV2Configs, isOverridesV2enabled);

        sweepingOutputService.consume(
            ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

        processEnvironmentVariables(
            ambiance, logCallback, environmentOutcome, isOverridesV2enabled, mergedOverrideV2Configs);
      }
      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(emptyIfNull(StepUtils.generateLogKeys(
              StepUtils.generateLogAbstractions(ambiance), List.of(ENVIRONMENT_COMMAND_UNIT))))
          .addAllUnits(List.of(ENVIRONMENT_COMMAND_UNIT))
          .addAllChildren(stepParameters.getChildrenNodeIds()
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
      saveExecutionLog(logCallback, LogHelper.color("Failed to complete environment step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
      environmentStepUnitProgress = UnitProgress.newBuilder()
                                        .setStatus(UnitStatus.FAILURE)
                                        .setUnitName(ENVIRONMENT_COMMAND_UNIT)
                                        .setStartTime(environmentStepStartTs)
                                        .setEndTime(System.currentTimeMillis())
                                        .build();
    } else {
      saveExecutionLog(logCallback, "Completed environment step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
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

  private void resolve(Ambiance ambiance, Object... objects) {
    final List<Object> toResolve = new ArrayList<>(Arrays.asList(objects));
    expressionResolver.updateExpressions(ambiance, toResolve);
  }

  private void setYamlInEnvironment(Environment environment) {
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
    environment.setYaml(io.harness.ng.core.environment.mappers.EnvironmentMapper.toYaml(ngEnvironmentConfig));
  }

  private NGEnvironmentConfig mergeEnvironmentInputs(String accountId, String identifier, String yaml,
      ParameterField<Map<String, Object>> environmentInputs) throws IOException {
    if (ParameterField.isNull(environmentInputs) || isEmpty(environmentInputs.getValue())) {
      return getNgEnvironmentConfig(accountId, identifier, yaml);
    }
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    String resolvedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        yaml, YamlPipelineUtils.writeYamlString(environmentInputYaml), true, true);

    return getNgEnvironmentConfig(accountId, identifier, resolvedYaml);
  }

  private NGEnvironmentConfig getNgEnvironmentConfig(String accountId, String identifier, String yaml)
      throws IOException {
    try {
      return YamlUtils.read(yaml, NGEnvironmentConfig.class);
    } catch (Exception ex) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, yaml);
      log.error(String.format(
          "Environment schema validation succeeded but failed to convert environment yaml to environment config [%s]",
          identifier));
      throw ex;
    }
  }

  @NonNull
  private List<NGVariable> getSecretVariablesFromOverridesV2(
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs) {
    return mergedOverrideV2Configs.values()
        .stream()
        .map(NGServiceOverrideConfigV2::getSpec)
        .map(spec -> spec.getVariables())
        .filter(variables -> isNotEmpty(variables))
        .flatMap(Collection::stream)
        .filter(SecretNGVariable.class ::isInstance)
        .collect(Collectors.toList());
  }

  private NGServiceOverrideConfigV2 toOverrideConfigV2(NGEnvironmentConfig envConfig, String accountId) {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig = envConfig.getNgEnvironmentInfoConfig();
    NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride = ngEnvironmentInfoConfig.getNgEnvironmentGlobalOverride();
    ServiceOverridesSpec.ServiceOverridesSpecBuilder specBuilder =
        ServiceOverridesSpec.builder().variables(ngEnvironmentInfoConfig.getVariables());
    if (ngEnvironmentGlobalOverride != null) {
      specBuilder.manifests(ngEnvironmentGlobalOverride.getManifests())
          .configFiles(ngEnvironmentGlobalOverride.getConfigFiles())
          .connectionStrings(ngEnvironmentGlobalOverride.getConnectionStrings())
          .applicationSettings(ngEnvironmentGlobalOverride.getApplicationSettings());
    }

    final String envRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, ngEnvironmentInfoConfig.getOrgIdentifier(),
            ngEnvironmentInfoConfig.getProjectIdentifier(), ngEnvironmentInfoConfig.getIdentifier());

    return NGServiceOverrideConfigV2.builder()
        .identifier(generateEnvGlobalOverrideV2Identifier(envRef))
        .environmentRef(envRef)
        .type(ENV_GLOBAL_OVERRIDE)
        .spec(specBuilder.build())
        .build();
  }

  private String generateEnvGlobalOverrideV2Identifier(String envRef) {
    return String.join("_", envRef).replace(".", "_");
  }

  private void processEnvironmentVariables(Ambiance ambiance, NGLogCallback serviceStepLogCallback,
      EnvironmentOutcome environmentOutcome, boolean isOverridesV2Enabled,
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs) {
    VariablesSweepingOutput variablesSweepingOutput = new VariablesSweepingOutput();
    if (isOverridesV2Enabled) {
      variablesSweepingOutput =
          getVariablesSweepingOutputFromOverridesV2(null, serviceStepLogCallback, overridesV2Configs);
    } else if (environmentOutcome != null) {
      variablesSweepingOutput = getVariablesSweepingOutput(null, serviceStepLogCallback, environmentOutcome);
    }

    sweepingOutputService.consume(ambiance, YAMLFieldNameConstants.VARIABLES, variablesSweepingOutput, null);

    saveExecutionLog(serviceStepLogCallback, "Processed environment variables");
  }

  private void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }

  private void saveExecutionLog(NGLogCallback logCallback, String line, LogLevel info, CommandExecutionStatus success) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, info, success);
    }
  }

  private VariablesSweepingOutput getVariablesSweepingOutput(
      NGServiceV2InfoConfig serviceV2InfoConfig, NGLogCallback logCallback, EnvironmentOutcome environmentOutcome) {
    // env v2 incorporating env variables into service variables
    final Map<String, Object> envVariables = new HashMap<>();
    if (isNotEmpty(environmentOutcome.getVariables())) {
      envVariables.putAll(environmentOutcome.getVariables());
    }
    Map<String, Object> variables = getFinalVariablesMap(serviceV2InfoConfig, envVariables, logCallback);
    VariablesSweepingOutput variablesOutcome = new VariablesSweepingOutput();
    variablesOutcome.putAll(variables);
    return variablesOutcome;
  }

  private VariablesSweepingOutput getVariablesSweepingOutputFromOverridesV2(NGServiceV2InfoConfig serviceV2InfoConfig,
      NGLogCallback logCallback, Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs) {
    Map<String, Object> finalOverridesVariables = new HashMap<>();
    final Map<String, Object> overridesVariables = getAllOverridesVariables(overridesV2Configs, logCallback);
    if (isNotEmpty(overridesVariables)) {
      finalOverridesVariables.putAll(overridesVariables);
    }
    Map<String, Object> variables = getFinalVariablesMap(serviceV2InfoConfig, finalOverridesVariables, logCallback);
    VariablesSweepingOutput variablesOutcome = new VariablesSweepingOutput();
    variablesOutcome.putAll(variables);
    return variablesOutcome;
  }

  private Map<String, Object> getAllOverridesVariables(
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs, NGLogCallback logCallback) {
    // Priority Order : INFRA_SERVICE > INFRA_GLOBAL > ENV_SERVICE > ENV_GLOBAL
    Map<String, Object> finalOverridesVars = new HashMap<>();
    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (overridesV2Configs.containsKey(overridesType)
          && isNotEmpty(overridesV2Configs.get(overridesType).getSpec().getVariables())) {
        finalOverridesVars.putAll(
            NGVariablesUtils.getMapOfVariables(overridesV2Configs.get(overridesType).getSpec().getVariables()));
        saveExecutionLog(logCallback, "Collecting variables from override of type " + overridesType.toString());
      }
    }
    return finalOverridesVars;
  }

  private Map<String, Object> getFinalVariablesMap(NGServiceV2InfoConfig serviceV2InfoConfig,
      Map<String, Object> envOrOverrideVariables, NGLogCallback logCallback) {
    List<NGVariable> variableList = new ArrayList<>();
    if (serviceV2InfoConfig != null) {
      variableList = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getVariables();
    }
    Map<String, Object> variables = new HashMap<>();
    Map<String, Object> outputVariables = new VariablesSweepingOutput();
    if (isNotEmpty(variableList)) {
      Map<String, Object> originalVariables = NGVariablesUtils.getMapOfVariables(variableList);
      variables.putAll(originalVariables);
      outputVariables.putAll(originalVariables);
    }
    addEnvVariables(outputVariables, envOrOverrideVariables, logCallback);
    variables.put("output", outputVariables);
    return variables;
  }

  private void addEnvVariables(
      Map<String, Object> variables, Map<String, Object> envVariables, NGLogCallback logCallback) {
    if (isEmpty(envVariables)) {
      return;
    }
    saveExecutionLog(logCallback, "Applying environment variables and overrides");
    variables.putAll(envVariables);
  }
}