/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers;

import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.common.beans.StepDetailsDelegateInfo;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceConfigStepConstants;
import io.harness.cdng.service.steps.constants.ServiceSectionStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceStepsHelper {
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private OutcomeService outcomeService;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  @Inject private CDExpressionResolver expressionResolver;

  public void checkForVariablesAccessOrThrow(Ambiance ambiance, NGServiceConfig serviceConfig, String serviceRef) {
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    io.harness.accesscontrol.principals.PrincipalType principalType =
        PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
            executionPrincipalInfo.getPrincipalType());

    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(serviceRef, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    accessControlClient.checkForAccessOrThrow(io.harness.accesscontrol.acl.api.Principal.of(principalType, principal),
        io.harness.accesscontrol.acl.api.ResourceScope.of(serviceIdentifierRef.getAccountIdentifier(),
            serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier()),
        io.harness.accesscontrol.acl.api.Resource.of(NGResourceType.SERVICE, serviceIdentifierRef.getIdentifier()),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION,
        String.format("Missing Access Permission for Service: [%s]", serviceRef));

    List<NGVariable> serviceVariables =
        serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();

    checkForAccessOrThrow(ambiance, serviceVariables);
  }

  public void checkForAccessOrThrow(Ambiance ambiance, List<NGVariable> serviceVariables) {
    if (EmptyPredicate.isEmpty(serviceVariables)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (NGVariable ngVariable : serviceVariables) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          ngVariable == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, ngVariable);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  public void checkForVariablesAccessOrThrow(
      Ambiance ambiance, ServiceDefinition serviceDefinition, String identifier) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    io.harness.accesscontrol.principals.PrincipalType principalType =
        PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
            executionPrincipalInfo.getPrincipalType());
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceDefinition);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    accessControlClient.checkForAccessOrThrow(io.harness.accesscontrol.acl.api.Principal.of(principalType, principal),
        io.harness.accesscontrol.acl.api.ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        io.harness.accesscontrol.acl.api.Resource.of(NGResourceType.SERVICE, identifier),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION, "Validation for Service Step failed");
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance) {
    return getServiceLogCallback(ambiance, false);
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return getServiceLogCallback(prepareServiceAmbiance(ambiance), shouldOpenStream, null);
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance, boolean shouldOpenStream, String commandUnit) {
    return new NGLogCallback(
        logStreamingStepClientFactory, prepareServiceAmbiance(ambiance), commandUnit, shouldOpenStream);
  }
  private Ambiance prepareServiceAmbiance(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    for (int i = levels.size() - 1; i >= 0; i--) {
      Level level = levels.get(i);
      if (ServiceConfigStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceSectionStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceStepV3Constants.STEP_TYPE.equals(level.getStepType())) {
        return AmbianceUtils.clone(ambiance, i + 1);
      }
    }
    throw new UnsupportedOperationException("Not inside service step or one of it's children");
  }

  public List<Outcome> getChildrenOutcomes(Map<String, ResponseData> responseDataMap) {
    List<StepOutcomeRef> outcomeRefs = new ArrayList<>();
    for (ResponseData responseData : responseDataMap.values()) {
      if (!(responseData instanceof StepResponseNotifyData)) {
        continue;
      }

      StepResponseNotifyData stepResponseNotifyData = (StepResponseNotifyData) responseData;
      if (EmptyPredicate.isNotEmpty(stepResponseNotifyData.getStepOutcomeRefs())) {
        outcomeRefs.addAll(stepResponseNotifyData.getStepOutcomeRefs());
      }
    }

    if (isEmpty(outcomeRefs)) {
      return Collections.emptyList();
    }

    Set<String> runtimeIds = new HashSet<>();
    outcomeRefs.forEach(or -> runtimeIds.add(or.getInstanceId()));
    return outcomeService.fetchOutcomes(new ArrayList<>(runtimeIds));
  }

  public void saveServiceExecutionDataToStageInfo(Ambiance ambiance, StepResponse stepResponse) {
    stageExecutionInfoService.updateStageExecutionInfo(ambiance,
        StageExecutionInfoUpdateDTO.builder().serviceInfo(createServiceInfoFromResponse(stepResponse)).build());
  }

  private ServiceExecutionSummaryDetails createServiceInfoFromResponse(StepResponse stepResponse) {
    if (stepResponse.getStepOutcomes() != null) {
      for (StepResponse.StepOutcome stepOutcome : stepResponse.getStepOutcomes()) {
        if (stepOutcome.getOutcome() instanceof ServiceStepOutcome) {
          ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) stepOutcome.getOutcome();
          return ServiceExecutionSummaryDetails.builder()
              .identifier(serviceStepOutcome.getIdentifier())
              .displayName(serviceStepOutcome.getName())
              .deploymentType(serviceStepOutcome.getServiceDefinitionType())
              .gitOpsEnabled(serviceStepOutcome.isGitOpsEnabled())
              .build();
        }
      }
    }
    return ServiceExecutionSummaryDetails.builder().build();
  }

  public void publishTaskIdsStepDetailsForServiceStep(
      Ambiance ambiance, List<StepDelegateInfo> stepDelegateInfos, String name) {
    if (isNotEmpty(stepDelegateInfos)) {
      sdkGraphVisualizationDataService.publishStepDetailInformation(prepareServiceAmbiance(ambiance),
          StepDetailsDelegateInfo.builder().stepDelegateInfos(stepDelegateInfos).build(), name);
    }
  }

  public void processServiceAndEnvironmentVariables(Ambiance ambiance, ServicePartResponse servicePartResponse,
      NGLogCallback serviceStepLogCallback, EnvironmentOutcome environmentOutcome, boolean isOverridesV2Enabled,
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs) {
    VariablesSweepingOutput variablesSweepingOutput = null;
    if (isOverridesV2Enabled) {
      if (servicePartResponse == null) {
        variablesSweepingOutput =
            getVariablesSweepingOutputFromOverridesV2(null, serviceStepLogCallback, overridesV2Configs);
      } else {
        variablesSweepingOutput = getVariablesSweepingOutputFromOverridesV2(
            servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(), serviceStepLogCallback,
            overridesV2Configs);
      }
    } else if (environmentOutcome != null) {
      if (servicePartResponse == null) {
        variablesSweepingOutput = getVariablesSweepingOutput(null, serviceStepLogCallback, environmentOutcome);
      } else {
        variablesSweepingOutput =
            getVariablesSweepingOutput(servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(),
                serviceStepLogCallback, environmentOutcome);
      }
    } else {
      if (servicePartResponse != null) {
        variablesSweepingOutput = getVariablesSweepingOutputForGitOps(
            servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(), serviceStepLogCallback);
      }
    }

    sweepingOutputService.consume(ambiance, YAMLFieldNameConstants.VARIABLES, variablesSweepingOutput, null);

    if (servicePartResponse != null) {
      Object outputObj = variablesSweepingOutput.get("output");
      if (!(outputObj instanceof VariablesSweepingOutput)) {
        outputObj = new VariablesSweepingOutput();
      }

      sweepingOutputService.consume(ambiance, YAMLFieldNameConstants.SERVICE_VARIABLES,
          (VariablesSweepingOutput) outputObj, StepCategory.STAGE.name());

      saveExecutionLog(serviceStepLogCallback, "Processed service & environment variables");
    } else {
      saveExecutionLog(serviceStepLogCallback, "Processed environment variables");
    }
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

  private VariablesSweepingOutput getVariablesSweepingOutputForGitOps(
      NGServiceV2InfoConfig serviceV2InfoConfig, NGLogCallback logCallback) {
    Map<String, Object> variables = getFinalVariablesMap(serviceV2InfoConfig, Map.of(), logCallback);
    VariablesSweepingOutput variablesOutcome = new VariablesSweepingOutput();
    variablesOutcome.putAll(variables);
    return variablesOutcome;
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

  public Map<String, Object> getAllOverridesVariables(
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

  public Map<String, Object> getFinalVariablesMap(NGServiceV2InfoConfig serviceV2InfoConfig,
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

  public void resolve(Ambiance ambiance, Object... objects) {
    final List<Object> toResolve = new ArrayList<>(Arrays.asList(objects));
    expressionResolver.updateExpressions(ambiance, toResolve);
  }

  public NGEnvironmentConfig mergeEnvironmentInputs(String accountId, String identifier, String yaml,
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

  public void setYamlInEnvironment(Environment environment) {
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
    environment.setYaml(io.harness.ng.core.environment.mappers.EnvironmentMapper.toYaml(ngEnvironmentConfig));
  }

  public NGEnvironmentConfig getNgEnvironmentConfig(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, String accountId, Optional<Environment> environment) {
    NGEnvironmentConfig ngEnvironmentConfig;
    final ParameterField<Map<String, Object>> envInputs = stepParameters.getEnvInputs();
    try {
      ngEnvironmentConfig =
          mergeEnvironmentInputs(accountId, environment.get().getIdentifier(), environment.get().getYaml(), envInputs);
    } catch (IOException ex) {
      throw new InvalidRequestException(
          "Unable to read yaml for environment: " + environment.get().getIdentifier(), ex);
    }
    resolve(ambiance, ngEnvironmentConfig);
    return ngEnvironmentConfig;
  }

  public NGEnvironmentConfig getNgEnvironmentConfig(String accountId, String identifier, String yaml)
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
  public List<NGVariable> getSecretVariablesFromOverridesV2(
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

  public NGServiceOverrideConfigV2 toOverrideConfigV2(NGEnvironmentConfig envConfig, String accountId) {
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

  private NGServiceOverrideConfigV2 toOverrideConfigV2(
      NGServiceOverrideConfig configV1, String accountId, NGEnvironmentConfig ngEnvironmentConfig) {
    NGServiceOverrideInfoConfig serviceOverrideInfoConfig = configV1.getServiceOverrideInfoConfig();
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig = ngEnvironmentConfig.getNgEnvironmentInfoConfig();
    final String envRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, ngEnvironmentInfoConfig.getOrgIdentifier(),
            ngEnvironmentInfoConfig.getProjectIdentifier(), ngEnvironmentInfoConfig.getIdentifier());
    return NGServiceOverrideConfigV2.builder()
        .identifier(generateEnvServiceOverrideV2Identifier(
            serviceOverrideInfoConfig.getEnvironmentRef(), serviceOverrideInfoConfig.getServiceRef()))
        .environmentRef(envRef)
        .type(ENV_SERVICE_OVERRIDE)
        .spec(ServiceOverridesSpec.builder()
                  .variables(serviceOverrideInfoConfig.getVariables())
                  .manifests(serviceOverrideInfoConfig.getManifests())
                  .configFiles(serviceOverrideInfoConfig.getConfigFiles())
                  .connectionStrings(serviceOverrideInfoConfig.getConnectionStrings())
                  .applicationSettings(serviceOverrideInfoConfig.getApplicationSettings())
                  .build())
        .build();
  }

  public String generateEnvServiceOverrideV2Identifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }

  public String generateEnvGlobalOverrideV2Identifier(String envRef) {
    return String.join("_", envRef).replace(".", "_");
  }

  public void saveExecutionLog(NGLogCallback logCallback, String line, LogLevel info, CommandExecutionStatus success) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, info, success);
    }
  }

  public void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }

  public void handleSecretVariables(NGEnvironmentConfig ngEnvironmentConfig, NGServiceOverrideConfig ngServiceOverrides,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs, Ambiance ambiance,
      boolean isOverridesV2enabled) {
    List<NGVariable> secretNGVariables = new ArrayList<>();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    if (isOverridesV2enabled) {
      if (ngEnvironmentConfig != null && ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
          && !mergedOverrideV2Configs.containsKey(ENV_GLOBAL_OVERRIDE)) {
        mergedOverrideV2Configs.put(ENV_GLOBAL_OVERRIDE, toOverrideConfigV2(ngEnvironmentConfig, accountId));
      }
      if (ngServiceOverrides != null && ngServiceOverrides.getServiceOverrideInfoConfig() != null
          && !mergedOverrideV2Configs.containsKey(ENV_SERVICE_OVERRIDE)) {
        mergedOverrideV2Configs.put(
            ENV_SERVICE_OVERRIDE, toOverrideConfigV2(ngServiceOverrides, accountId, ngEnvironmentConfig));
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

      if (ngServiceOverrides != null && ngServiceOverrides.getServiceOverrideInfoConfig() != null
          && ngServiceOverrides.getServiceOverrideInfoConfig().getVariables() != null) {
        secretNGVariables.addAll(ngServiceOverrides.getServiceOverrideInfoConfig()
                                     .getVariables()
                                     .stream()
                                     .filter(SecretNGVariable.class ::isInstance)
                                     .collect(Collectors.toList()));
      }
    }
    checkForAccessOrThrow(ambiance, secretNGVariables);
  }

  @Data
  @Builder
  public static class ServicePartResponse {
    private NGServiceConfig ngServiceConfig;

    public NGServiceConfig getNgServiceConfig() {
      return ngServiceConfig;
    }
  }
}
