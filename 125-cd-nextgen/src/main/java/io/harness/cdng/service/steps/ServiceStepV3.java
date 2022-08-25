package io.harness.cdng.service.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
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
  @Inject @com.google.inject.name.Named(value = "defaultConnectorService") private ConnectorService connectorService;

  @Override
  public Class<ServiceStepV3Parameters> getStepParametersClass() {
    return ServiceStepV3Parameters.class;
  }

  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, StepInputPackage inputPackage) {
    try {
      final ServicePartResponse servicePartResponse = executeServicePart(ambiance, stepParameters);
      executeEnvironmentPart(ambiance, stepParameters, servicePartResponse);
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(stepParameters.getChildrenNodeIds()
                              .stream()
                              .map(id -> ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(id).build())
                              .collect(Collectors.toList()))
          .build();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void executeEnvironmentPart(Ambiance ambiance, ServiceStepV3Parameters parameters,
      ServicePartResponse servicePartResponse) throws IOException {
    final ParameterField<String> envRef = parameters.getEnvRef();
    final ParameterField<Map<String, Object>> envInputs = parameters.getEnvInputs();
    if (ParameterField.isNull(envRef)) {
      // Todo:(yogesh)
      return;
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
        // todo: (yogesh) handle this
        throw new RuntimeException("env not found");
      }

      NGEnvironmentConfig ngEnvironmentConfig =
          mergeEnvironmentInputs(environment.get().getYaml(), envInputs.getValue());

      EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(
          environment.get(), ngEnvironmentConfig, servicePartResponse.getNgServiceConfig());
      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());
    }
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
    serviceStepsHelper.validateResources(
        ambiance, ngServiceV2InfoConfig.getServiceDefinition(), ngServiceV2InfoConfig.getIdentifier());

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(OutcomeExpressionConstants.SERVICE)
                .outcome(ServiceStepOutcome.fromServiceStepV2(ngServiceV2InfoConfig.getIdentifier(),
                    ngServiceV2InfoConfig.getName(), ngServiceV2InfoConfig.getServiceDefinition().getType().name(),
                    ngServiceV2InfoConfig.getDescription(), ngServiceV2InfoConfig.getTags(),
                    ngServiceV2InfoConfig.getGitOpsEnabled()))
                .group(StepCategory.STAGE.name())
                .build())
        .build();
  }

  @Data
  @Builder
  static class ServicePartResponse {
    NGServiceConfig ngServiceConfig;
  }
  public ServicePartResponse executeServicePart(Ambiance ambiance, ServiceStepV3Parameters stepParameters) {
    // Todo: check if service ref is resolved
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

    // Todo:(yogesh) check the step category
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

  public NGEnvironmentConfig mergeEnvironmentInputs(String originalEnvYaml, Map<String, Object> environmentInputs)
      throws IOException {
    if (isEmpty(environmentInputs)) {
      return YamlUtils.read(originalEnvYaml, NGEnvironmentConfig.class);
    }
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    String resolvedYaml = MergeHelper.mergeInputSetFormatYamlToOriginYaml(
        originalEnvYaml, YamlPipelineUtils.writeYamlString(environmentInputYaml));
    return YamlUtils.read(resolvedYaml, NGEnvironmentConfig.class);
  }
}
