package io.harness.cdng.service.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
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
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStepV3 implements ChildrenExecutable<ServiceStepV3Parameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE_V3.getName()).setStepCategory(StepCategory.STEP).build();
  public static final String SERVICE_SWEEPING_OUTPUT = "serviceSweepingOutput";
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EnvironmentService environmentService;
  @Inject private CDExpressionResolver expressionResolver;
  @Inject private ServiceOverrideService serviceOverrideService;

  @Override
  public Class<ServiceStepV3Parameters> getStepParametersClass() {
    return ServiceStepV3Parameters.class;
  }

  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, StepInputPackage inputPackage) {
    validate(stepParameters);
    try {
      final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance, true);
      saveExecutionLog(logCallback, "Starting service step...");

      final ServicePartResponse servicePartResponse = executeServicePart(ambiance, stepParameters);

      executeEnvironmentPart(ambiance, stepParameters, servicePartResponse, logCallback);

      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(CollectionUtils.emptyIfNull(
              StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
          .addAllChildren(stepParameters.getChildrenNodeIds()
                              .stream()
                              .map(id -> ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(id).build())
                              .collect(Collectors.toList()))
          .build();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void validate(ServiceStepV3Parameters stepParameters) {
    if (ParameterField.isNull(stepParameters.getServiceRef())) {
      throw new InvalidRequestException("service ref not provided");
    }

    if (stepParameters.getServiceRef().isExpression()) {
      throw new UnresolvedExpressionsException(Arrays.asList(stepParameters.getServiceRef().getExpressionValue()));
    }

    if (ParameterField.isNull(stepParameters.getEnvRef())) {
      throw new InvalidRequestException("environment ref not provided");
    }
  }

  private void executeEnvironmentPart(Ambiance ambiance, ServiceStepV3Parameters parameters,
      ServicePartResponse servicePartResponse, NGLogCallback logCallback) throws IOException {
    final ParameterField<String> envRef = parameters.getEnvRef();
    final ParameterField<Map<String, Object>> envInputs = parameters.getEnvInputs();
    if (ParameterField.isNull(envRef)) {
      throw new InvalidRequestException("Environment ref not found in pipeline yaml");
    }

    List<Object> toResolve = new ArrayList<>();
    toResolve.add(envRef);
    toResolve.add(envInputs);
    expressionResolver.updateExpressions(ambiance, toResolve);

    log.info("Starting execution for Environment Step [{}]", envRef.getValue());
    if (envRef.fetchFinalValue() != null) {
      Optional<Environment> environment =
          environmentService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), envRef.getValue(), false);
      if (environment.isEmpty()) {
        throw new InvalidRequestException("Environment " + envRef.getValue() + " not found");
      }

      final NGEnvironmentConfig ngEnvironmentConfig = mergeEnvironmentInputs(environment.get().getYaml(), envInputs);

      final Optional<NGServiceOverridesEntity> ngServiceOverridesEntity =
          serviceOverrideService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), envRef.getValue(), parameters.getServiceRef().getValue());

      final EnvironmentOutcome environmentOutcome =
          EnvironmentMapper.toEnvironmentOutcome(environment.get(), ngEnvironmentConfig,
              NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(
                  ngServiceOverridesEntity.orElse(NGServiceOverridesEntity.builder().build())));

      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

      processServiceVariables(ambiance, servicePartResponse, logCallback, environmentOutcome);
    }
  }

  private void processServiceVariables(Ambiance ambiance, ServicePartResponse servicePartResponse,
      NGLogCallback logCallback, EnvironmentOutcome environmentOutcome) {
    VariablesSweepingOutput variablesSweepingOutput = getVariablesSweepingOutput(
        servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(), logCallback, environmentOutcome);

    sweepingOutputService.consume(ambiance, YAMLFieldNameConstants.VARIABLES, variablesSweepingOutput, null);

    Object outputObj = variablesSweepingOutput.get("output");
    if (!(outputObj instanceof VariablesSweepingOutput)) {
      outputObj = new VariablesSweepingOutput();
    }

    sweepingOutputService.consume(ambiance, YAMLFieldNameConstants.SERVICE_VARIABLES,
        (VariablesSweepingOutput) outputObj, StepCategory.STAGE.name());

    saveExecutionLog(logCallback, "Processed service variables");
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT));

    NGServiceConfig ngServiceConfig = null;
    if (serviceSweepingOutput != null) {
      try {
        ngServiceConfig = YamlUtils.read(serviceSweepingOutput.getFinalServiceYaml(), NGServiceConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Unable to read service yaml", e);
      }
    }

    if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
      log.info("No service configuration found");
      throw new InvalidRequestException("Unable to read service yaml");
    }
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();

    StepResponse stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);

    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      saveExecutionLog(logCallback, LogHelper.color("Failed to complete service step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
    } else {
      saveExecutionLog(logCallback, "Completed service step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    stepOutcomes.add(
        StepResponse.StepOutcome.builder()
            .name(OutcomeExpressionConstants.SERVICE)
            .outcome(ServiceStepOutcome.fromServiceStepV2(ngServiceV2InfoConfig.getIdentifier(),
                ngServiceV2InfoConfig.getName(), ngServiceV2InfoConfig.getServiceDefinition().getType().name(),
                ngServiceV2InfoConfig.getDescription(), ngServiceV2InfoConfig.getTags(),
                ngServiceV2InfoConfig.getGitOpsEnabled()))
            .group(StepCategory.STAGE.name())
            .build());

    final OptionalSweepingOutput manifestsOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.MANIFESTS));
    if (manifestsOutput.isFound()) {
      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.MANIFESTS)
                           .outcome((ManifestsOutcome) manifestsOutput.getOutput())
                           .group(StepCategory.STAGE.name())
                           .build());
    }

    final OptionalSweepingOutput artifactsOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutput.isFound()) {
      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.ARTIFACTS)
                           .outcome((ArtifactsOutcome) artifactsOutput.getOutput())
                           .group(StepCategory.STAGE.name())
                           .build());
    }

    final OptionalSweepingOutput configFilesOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.CONFIG_FILES));
    if (configFilesOutput.isFound()) {
      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.CONFIG_FILES)
                           .outcome((ConfigFilesOutcome) configFilesOutput.getOutput())
                           .group(StepCategory.STAGE.name())
                           .build());
    }
    // Todo: Add azure outcomes here
    return stepResponse.withStepOutcomes(stepOutcomes);
  }

  private ServicePartResponse executeServicePart(Ambiance ambiance, ServiceStepV3Parameters stepParameters) {
    final Optional<ServiceEntity> serviceOpt =
        serviceEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getServiceRef().getValue(), false);
    if (serviceOpt.isEmpty()) {
      throw new InvalidRequestException(
          format("serviceOpt with identifier %s not found", stepParameters.getServiceRef()));
    }

    final ServiceEntity serviceEntity = serviceOpt.get();
    final String mergedServiceYaml;
    if (stepParameters.getInputs() != null && isNotEmpty(stepParameters.getInputs().getValue())) {
      mergedServiceYaml = mergeServiceInputsIntoService(serviceEntity.getYaml(), stepParameters.getInputs().getValue());
    } else {
      mergedServiceYaml = serviceEntity.getYaml();
    }

    final NGServiceConfig ngServiceConfig;
    try {
      ngServiceConfig = YamlUtils.read(mergedServiceYaml, NGServiceConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("corrupt service yaml for service " + serviceEntity.getIdentifier(), e);
    }

    sweepingOutputService.consume(ambiance, SERVICE_SWEEPING_OUTPUT,
        ServiceSweepingOutput.builder().finalServiceYaml(mergedServiceYaml).build(), "");

    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();

    serviceStepsHelper.validateResources(ambiance, ngServiceConfig);

    ServiceStepOutcome outcome = ServiceStepOutcome.fromServiceStepV2(serviceEntity.getIdentifier(),
        serviceEntity.getName(), ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(),
        serviceEntity.getDescription(), ngServiceV2InfoConfig.getTags(), serviceEntity.getGitOpsEnabled());

    sweepingOutputService.consume(ambiance, OutcomeExpressionConstants.SERVICE, outcome, StepCategory.STAGE.name());

    return ServicePartResponse.builder().ngServiceConfig(ngServiceConfig).build();
  }

  private String mergeServiceInputsIntoService(String originalServiceYaml, Map<String, Object> serviceInputs) {
    Map<String, Object> serviceInputsYaml = new HashMap<>();
    serviceInputsYaml.put(YamlTypes.SERVICE_ENTITY, serviceInputs);
    return MergeHelper.mergeInputSetFormatYamlToOriginYaml(
        originalServiceYaml, YamlPipelineUtils.writeYamlString(serviceInputsYaml));
  }

  private NGEnvironmentConfig mergeEnvironmentInputs(
      String originalEnvYaml, ParameterField<Map<String, Object>> environmentInputs) throws IOException {
    if (ParameterField.isNull(environmentInputs) || isEmpty(environmentInputs.getValue())) {
      return YamlUtils.read(originalEnvYaml, NGEnvironmentConfig.class);
    }
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    String resolvedYaml = MergeHelper.mergeInputSetFormatYamlToOriginYaml(
        originalEnvYaml, YamlPipelineUtils.writeYamlString(environmentInputYaml));
    return YamlUtils.read(resolvedYaml, NGEnvironmentConfig.class);
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

  private Map<String, Object> getFinalVariablesMap(
      NGServiceV2InfoConfig serviceV2InfoConfig, Map<String, Object> envVariables, NGLogCallback logCallback) {
    List<NGVariable> variableList = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getVariables();
    Map<String, Object> variables = new HashMap<>();
    Map<String, Object> outputVariables = new VariablesSweepingOutput();
    if (isNotEmpty(variableList)) {
      Map<String, Object> originalVariables = NGVariablesUtils.getMapOfVariables(variableList);
      variables.putAll(originalVariables);
      outputVariables.putAll(originalVariables);
    }
    addEnvVariables(outputVariables, envVariables, logCallback);
    variables.put("output", outputVariables);
    return variables;
  }

  private void addEnvVariables(
      Map<String, Object> variables, Map<String, Object> envVariables, NGLogCallback logCallback) {
    if (EmptyPredicate.isEmpty(envVariables)) {
      return;
    }

    saveExecutionLog(logCallback, "Applying environment variables and service overrides");
    variables.putAll(envVariables);
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

  @Data
  @Builder
  static class ServicePartResponse {
    NGServiceConfig ngServiceConfig;
  }
}
