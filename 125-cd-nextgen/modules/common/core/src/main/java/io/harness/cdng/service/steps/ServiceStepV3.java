/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_ENV_OUTCOME;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;

import static java.lang.String.format;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorHelper;
import io.harness.cdng.creator.plan.environment.EnvironmentStepsUtils;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.steps.GitOpsEnvOutCome;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.exception.WingsException;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.expression.EngineExpressionService;
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
import io.harness.rbac.CDNGRbacUtility;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStepV3 implements ChildrenExecutable<ServiceStepV3Parameters> {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EnvironmentService environmentService;
  @Inject private CDExpressionResolver expressionResolver;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private ServiceStepOverrideHelper serviceStepOverrideHelper;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject private FrozenExecutionService frozenExecutionService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private NotificationHelper notificationHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject NgExpressionHelper ngExpressionHelper;
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private ServiceCustomSweepingOutputHelper serviceCustomSweepingOutputHelper;

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

      Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

      final ServicePartResponse servicePartResponse = executeServicePart(ambiance, stepParameters, entityMap);

      saveExecutionLog(logCallback,
          "Service Name: " + servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig().getName()
              + " , Identifier: "
              + servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig().getIdentifier());

      performFiltering(stepParameters, ambiance,
          EnvironmentInfraFilterHelper.getNGTags(
              servicePartResponse.ngServiceConfig.getNgServiceV2InfoConfig().getTags()));
      // Support GitOps Flow
      // If environment group is only set for GitOps or if GitOps flow and deploying to multi-environments
      if (ParameterField.isNotNull(stepParameters.getGitOpsMultiSvcEnvEnabled())
          && stepParameters.getGitOpsMultiSvcEnvEnabled().getValue()) {
        handleMultipleEnvironmentsPart(ambiance, stepParameters, servicePartResponse, logCallback);
      } else {
        executeEnvironmentPart(ambiance, stepParameters, servicePartResponse, logCallback, entityMap);
      }

      ChildrenExecutableResponse childrenExecutableResponse = executeFreezePart(ambiance, entityMap);
      if (childrenExecutableResponse != null) {
        return childrenExecutableResponse;
      }

      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(emptyIfNull(
              StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
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

  // Only for Gitops flow. Non GitOps is handled in Multi-Spawner Step
  private void performFiltering(ServiceStepV3Parameters stepParameters, Ambiance ambiance, List<NGTag> serviceTags) {
    if (stepParameters.getEnvironmentGroupYaml() == null && stepParameters.getEnvironmentsYaml() == null) {
      return;
    }
    List<EnvClusterRefs> envClusterRefs;
    if (stepParameters.getEnvironmentGroupYaml() != null) {
      envClusterRefs = environmentInfraFilterHelper.filterEnvGroupAndClusters(stepParameters.getEnvironmentGroupYaml(),
          serviceTags, AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance));
    } else {
      envClusterRefs = environmentInfraFilterHelper.filterEnvsAndClusters(stepParameters.getEnvironmentsYaml(),
          serviceTags, AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance));
    }
    Set<String> envRefs =
        envClusterRefs.stream().map(envClusterRefs1 -> envClusterRefs1.getEnvRef()).collect(Collectors.toSet());
    List<ParameterField<String>> envRefsList =
        envRefs.stream().map(envRef -> ParameterField.createValueField(envRef)).collect(Collectors.toList());
    stepParameters.setEnvRefs(envRefsList);
  }

  private void validate(ServiceStepV3Parameters stepParameters) {
    if (ParameterField.isNull(stepParameters.getServiceRef())) {
      throw new InvalidRequestException("service ref not provided");
    }

    if (stepParameters.getServiceRef().isExpression()) {
      throw new UnresolvedExpressionsException(Arrays.asList(stepParameters.getServiceRef().getExpressionValue()));
    }
    if (ParameterField.isNull(stepParameters.getEnvRef()) && isEmpty(stepParameters.getEnvRefs())
        && stepParameters.getEnvironmentGroupYaml() == null && stepParameters.getEnvironmentsYaml() == null) {
      throw new InvalidRequestException("environment ref or environment refs not provided");
    }
  }

  /**
   * Function handles processing envInputs and serviceInputs for multiple environments. Currently,
   * the flow is being used for GitOps Flows deploying to multiple environments.
   */
  private void handleMultipleEnvironmentsPart(Ambiance ambiance, ServiceStepV3Parameters parameters,
      ServicePartResponse servicePartResponse, NGLogCallback logCallback) {
    Map<String, Map<String, Object>> envToEnvVariables = new HashMap<>();
    Map<String, Map<String, Object>> envToSvcVariables = new HashMap<>();
    List<NGVariable> svcOverrideVariables;
    List<NGVariable> secretNGVariables = new ArrayList<>();
    if (isEmpty(parameters.getEnvRefs())) {
      throw new InvalidRequestException("No environments are found while handling deployment to multiple environments");
    }

    List<ParameterField<String>> envRefs =
        inferEnvRefScopeFromEnvGroup(parameters.getEnvRefs(), parameters.getEnvGroupRef());

    List<Environment> environments = getEnvironmentsFromEnvRef(ambiance, envRefs);

    EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(accessControlClient, ambiance, environments);

    log.info("Starting execution for Environments: [{}]", Arrays.toString(environments.toArray()));
    for (Environment environment : environments) {
      NGEnvironmentConfig ngEnvironmentConfig;
      // handle old environments
      if (isEmpty(environment.getYaml())) {
        getNGEnvironmentConfig(environment);
      }
      try {
        if (isNotEmpty(parameters.getEnvToEnvInputs())) {
          ngEnvironmentConfig = mergeEnvironmentInputs(environment.getYaml(),
              parameters.getEnvToEnvInputs().get(
                  getEnvRefOrId(environment.fetchRef(), parameters.getEnvGroupRef(), environment.getIdentifier())));
        } else {
          ngEnvironmentConfig = mergeEnvironmentInputs(environment.getYaml(), null);
        }
      } catch (IOException ex) {
        throw new InvalidRequestException(format("Unable to read yaml for environment [Name: %s, Identifier: %s]",
                                              environment.getName(), environment.getIdentifier()),
            ex);
      }
      List<NGVariable> variables = ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables();
      envToEnvVariables.put(inheritEnvGroupScope(environment.fetchRef(), parameters.getEnvGroupRef()),
          NGVariablesUtils.getMapOfVariables(variables));
      if (variables != null) {
        secretNGVariables.addAll(
            variables.stream().filter(SecretNGVariable.class ::isInstance).collect(Collectors.toList()));
      }
      final Optional<NGServiceOverridesEntity> ngServiceOverridesEntity = serviceOverrideService.get(
          AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), environment.fetchRef(), parameters.getServiceRef().getValue());
      NGServiceOverrideConfig ngServiceOverrides;
      if (ngServiceOverridesEntity.isPresent()) {
        ngServiceOverrides = mergeSvcOverrideInputs(ngServiceOverridesEntity.get().getYaml(),
            parameters.getEnvToSvcOverrideInputs().get(
                getEnvRefOrId(environment.fetchRef(), parameters.getEnvGroupRef(), environment.getIdentifier())));

        svcOverrideVariables = ngServiceOverrides.getServiceOverrideInfoConfig().getVariables();
        if (svcOverrideVariables != null) {
          secretNGVariables.addAll(
              svcOverrideVariables.stream().filter(SecretNGVariable.class ::isInstance).collect(Collectors.toList()));
        }
        envToSvcVariables.put(inheritEnvGroupScope(environment.fetchRef(), parameters.getEnvGroupRef()),
            NGVariablesUtils.getMapOfVariables(svcOverrideVariables));
      }
    }

    serviceStepsHelper.checkForAccessOrThrow(ambiance, secretNGVariables);

    resolve(ambiance, envToEnvVariables, envToSvcVariables);

    GitOpsEnvOutCome gitOpsEnvOutCome = new GitOpsEnvOutCome(envToEnvVariables, envToSvcVariables);

    sweepingOutputService.consume(ambiance, GITOPS_ENV_OUTCOME, gitOpsEnvOutCome, StepCategory.STAGE.name());

    processServiceVariables(ambiance, servicePartResponse, logCallback, null);

    serviceStepOverrideHelper.prepareAndSaveFinalManifestMetadataToSweepingOutput(
        servicePartResponse.getNgServiceConfig(), null, null, ambiance,
        ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT);

    serviceStepOverrideHelper.prepareAndSaveFinalConfigFilesMetadataToSweepingOutput(
        servicePartResponse.getNgServiceConfig(), null, null, ambiance,
        ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT);

    serviceStepOverrideHelper.prepareAndSaveFinalAppServiceMetadataToSweepingOutput(
        servicePartResponse.getNgServiceConfig(), null, null, ambiance,
        ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT);

    serviceStepOverrideHelper.prepareAndSaveFinalConnectionStringsMetadataToSweepingOutput(
        servicePartResponse.getNgServiceConfig(), null, null, ambiance,
        ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT);
  }

  private String getEnvRefOrId(String envRef, ParameterField<String> envGroupRef, String envId) {
    if (ParameterField.isNull(envGroupRef) || StringUtils.isEmpty(envGroupRef.getValue())) {
      return envRef;
    }
    return envId;
  }

  private String inheritEnvGroupScope(String envRef, ParameterField<String> envGroupRef) {
    if (ParameterField.isNull(envGroupRef) || StringUtils.isEmpty(envGroupRef.getValue())) {
      return envRef;
    }
    Scope envGroupScope = EnvironmentStepsUtils.getScopeForRef(envGroupRef.getValue());
    return EnvironmentStepsUtils.getEnvironmentRef(envRef, envGroupScope);
  }

  private List<ParameterField<String>> inferEnvRefScopeFromEnvGroup(
      List<ParameterField<String>> envRefs, ParameterField<String> envGroupRef) {
    if (isEmpty(envRefs) || ParameterField.isNull(envGroupRef) || StringUtils.isEmpty(envGroupRef.getValue())) {
      return envRefs;
    }
    Scope envGroupScope = EnvironmentStepsUtils.getScopeForRef(envGroupRef.getValue());
    return envRefs.stream()
        .map(e -> ParameterField.createValueField(EnvironmentStepsUtils.getEnvironmentRef(e.getValue(), envGroupScope)))
        .collect(Collectors.toList());
  }

  private List<Environment> getEnvironmentsFromEnvRef(Ambiance ambiance, List<ParameterField<String>> envRefs) {
    List<String> envRefsIds = envRefs.stream().map(e -> e.getValue()).collect(Collectors.toList());

    List<Environment> environments =
        environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), envRefsIds);

    if (environments.isEmpty()) {
      throw new InvalidRequestException(
          "Unable to fetch environments from environment identifiers. Please verify if referred environments still exist");
    }
    return environments;
  }

  private NGEnvironmentConfig getNGEnvironmentConfig(Environment environment) {
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
    environment.setYaml(io.harness.ng.core.environment.mappers.EnvironmentMapper.toYaml(ngEnvironmentConfig));
    return ngEnvironmentConfig;
  }

  private void executeEnvironmentPart(Ambiance ambiance, ServiceStepV3Parameters parameters,
      ServicePartResponse servicePartResponse, NGLogCallback logCallback,
      Map<FreezeEntityType, List<String>> entityMap) {
    final ParameterField<String> envRef = parameters.getEnvRef();
    final ParameterField<Map<String, Object>> envInputs = parameters.getEnvInputs();
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
          environmentService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), envRef.getValue(), false);
      if (environment.isEmpty()) {
        throw new InvalidRequestException(String.format("Environment with ref: [%s] not found", envRef.getValue()));
      }

      NGEnvironmentConfig ngEnvironmentConfig;
      // handle old environments
      if (isEmpty(environment.get().getYaml())) {
        getNGEnvironmentConfig(environment.get());
      }

      try {
        ngEnvironmentConfig = mergeEnvironmentInputs(environment.get().getYaml(), envInputs);
      } catch (IOException ex) {
        throw new InvalidRequestException(
            "Unable to read yaml for environment: " + environment.get().getIdentifier(), ex);
      }

      final Optional<NGServiceOverridesEntity> ngServiceOverridesEntity =
          serviceOverrideService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), envRef.getValue(), parameters.getServiceRef().getValue());
      NGServiceOverrideConfig ngServiceOverrides = NGServiceOverrideConfig.builder().build();
      if (ngServiceOverridesEntity.isPresent()) {
        ngServiceOverrides =
            mergeSvcOverrideInputs(ngServiceOverridesEntity.get().getYaml(), parameters.getServiceOverrideInputs());
      }

      resolve(ambiance, ngEnvironmentConfig, ngServiceOverrides);
      List<NGVariable> secretNGVariables = new ArrayList<>();
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
      serviceStepsHelper.checkForAccessOrThrow(ambiance, secretNGVariables);
      entityMap.put(FreezeEntityType.ENVIRONMENT, Lists.newArrayList(environment.get().getIdentifier()));
      entityMap.put(FreezeEntityType.ENV_TYPE, Lists.newArrayList(environment.get().getType().name()));

      Optional<EnvironmentGroupEntity> envGroupOpt = Optional.empty();
      if (ParameterField.isNotNull(parameters.getEnvGroupRef())
          && EmptyPredicate.isNotEmpty(parameters.getEnvGroupRef().getValue())) {
        envGroupOpt =
            environmentGroupService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                AmbianceUtils.getProjectIdentifier(ambiance), parameters.getEnvGroupRef().getValue(), false);
      }
      final EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(
          environment.get(), ngEnvironmentConfig, ngServiceOverrides, envGroupOpt.orElse(null));

      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

      processServiceVariables(ambiance, servicePartResponse, logCallback, environmentOutcome);

      serviceStepOverrideHelper.prepareAndSaveFinalManifestMetadataToSweepingOutput(
          servicePartResponse.getNgServiceConfig(), ngServiceOverrides, ngEnvironmentConfig, ambiance,
          ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalConfigFilesMetadataToSweepingOutput(
          servicePartResponse.getNgServiceConfig(), ngServiceOverrides, ngEnvironmentConfig, ambiance,
          ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalAppServiceMetadataToSweepingOutput(
          servicePartResponse.getNgServiceConfig(), ngServiceOverrides, ngEnvironmentConfig, ambiance,
          ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT);

      serviceStepOverrideHelper.prepareAndSaveFinalConnectionStringsMetadataToSweepingOutput(
          servicePartResponse.getNgServiceConfig(), ngServiceOverrides, ngEnvironmentConfig, ambiance,
          ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT);
    }
  }

  private void resolve(Ambiance ambiance, Object... objects) {
    final List<Object> toResolve = new ArrayList<>(Arrays.asList(objects));
    expressionResolver.updateExpressions(ambiance, toResolve);
  }

  private NGServiceOverrideConfig mergeSvcOverrideInputs(
      String originalOverridesYaml, ParameterField<Map<String, Object>> serviceOverrideInputs) {
    NGServiceOverrideConfig serviceOverrideConfig = NGServiceOverrideConfig.builder().build();

    if (ParameterField.isNull(serviceOverrideInputs) || isEmpty(serviceOverrideInputs.getValue())) {
      return ServiceOverridesMapper.toNGServiceOverrideConfig(originalOverridesYaml);
    }
    final String mergedYaml = EnvironmentPlanCreatorHelper.resolveServiceOverrideInputs(
        originalOverridesYaml, serviceOverrideInputs.getValue());
    if (isNotEmpty(mergedYaml)) {
      serviceOverrideConfig = ServiceOverridesMapper.toNGServiceOverrideConfig(mergedYaml);
    }
    return serviceOverrideConfig;
  }

  private void processServiceVariables(Ambiance ambiance, ServicePartResponse servicePartResponse,
      NGLogCallback logCallback, EnvironmentOutcome environmentOutcome) {
    VariablesSweepingOutput variablesSweepingOutput;
    if (environmentOutcome != null) {
      variablesSweepingOutput = getVariablesSweepingOutput(
          servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(), logCallback, environmentOutcome);
    } else {
      variablesSweepingOutput = getVariablesSweepingOutputForGitOps(
          servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig(), logCallback);
    }

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
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    final ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT));
    String serviceRef = stepParameters.getServiceRef().getValue();
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

    StepResponse stepResponse = handleFreezeResponse(ambiance, ngServiceV2InfoConfig, stepParameters);
    if (stepResponse != null) {
      return stepResponse;
    }

    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);

    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      saveExecutionLog(logCallback, LogHelper.color("Failed to complete service step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
    } else {
      saveExecutionLog(logCallback, "Completed service step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }
    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.SERVICE)
                         .outcome(ServiceStepOutcome.fromServiceStepV2(serviceRef, ngServiceV2InfoConfig.getName(),
                             ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(),
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

  private ServicePartResponse executeServicePart(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, Map<FreezeEntityType, List<String>> entityMap) {
    final Optional<ServiceEntity> serviceOpt =
        serviceEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getServiceRef().getValue(), false);

    if (serviceOpt.isEmpty()) {
      throw new InvalidRequestException(
          format("service with ref: [%s] not found", stepParameters.getServiceRef().fetchFinalValue()));
    }

    final ServiceEntity serviceEntity = serviceOpt.get();

    if (serviceEntity.getType() != null && stepParameters.getDeploymentType() != null
        && serviceEntity.getType() != stepParameters.getDeploymentType()) {
      throw new InvalidRequestException(format("Deployment type of the stage [%s] and the service [%s] do not match",
          stepParameters.getDeploymentType().getYamlName(), serviceEntity.getType().getYamlName()));
    }

    String mergedServiceYaml;
    if (stepParameters.getInputs() != null && isNotEmpty(stepParameters.getInputs().getValue())) {
      mergedServiceYaml = mergeServiceInputsIntoService(serviceEntity.getYaml(), stepParameters.getInputs().getValue());
    } else {
      mergedServiceYaml = serviceEntity.getYaml();
    }

    final NGServiceConfig ngServiceConfig;
    try {
      ngServiceConfig = YamlUtils.read(mergedServiceYaml, NGServiceConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException(format("Unable to read yaml for service [Name: %s, Identifier: %s]",
                                            serviceEntity.getName(), serviceEntity.getIdentifier()),
          e);
    }

    sweepingOutputService.consume(ambiance, ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT,
        ServiceSweepingOutput.builder().finalServiceYaml(mergedServiceYaml).build(), "");

    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();

    if (ngServiceV2InfoConfig.getServiceDefinition() == null) {
      throw new InvalidRequestException(
          format("Service Definition is not defined for service [Name: %s, Identifier: %s]", serviceEntity.getName(),
              serviceEntity.getIdentifier()));
    }
    serviceCustomSweepingOutputHelper.saveAdditionalServiceFieldsToSweepingOutput(ngServiceConfig, ambiance);

    serviceStepsHelper.checkForVariablesAccessOrThrow(
        ambiance, ngServiceConfig, stepParameters.getServiceRef().getValue());

    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(serviceEntity.getOrgIdentifier()));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(serviceEntity.getProjectIdentifier()));
    // serviceRef instead of identifier to be passed here
    entityMap.put(FreezeEntityType.SERVICE,
        Lists.newArrayList(FullyQualifiedIdentifierHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
            serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier())));

    // Add the reason in serviceOutcome;
    ServiceStepOutcome outcome = ServiceStepOutcome.fromServiceStepV2(serviceEntity, ngServiceV2InfoConfig);

    sweepingOutputService.consume(ambiance, OutcomeExpressionConstants.SERVICE, outcome, StepCategory.STAGE.name());

    return ServicePartResponse.builder().ngServiceConfig(ngServiceConfig).build();
  }

  private String mergeServiceInputsIntoService(String originalServiceYaml, Map<String, Object> serviceInputs) {
    Map<String, Object> serviceInputsYaml = new HashMap<>();
    serviceInputsYaml.put(YamlTypes.SERVICE_ENTITY, serviceInputs);
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalServiceYaml, YamlPipelineUtils.writeYamlString(serviceInputsYaml), true, true);
  }

  private NGEnvironmentConfig mergeEnvironmentInputs(
      String originalEnvYaml, ParameterField<Map<String, Object>> environmentInputs) throws IOException {
    if (ParameterField.isNull(environmentInputs) || isEmpty(environmentInputs.getValue())) {
      return YamlUtils.read(originalEnvYaml, NGEnvironmentConfig.class);
    }
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    String resolvedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalEnvYaml, YamlPipelineUtils.writeYamlString(environmentInputYaml), true, true);
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

  private VariablesSweepingOutput getVariablesSweepingOutputForGitOps(
      NGServiceV2InfoConfig serviceV2InfoConfig, NGLogCallback logCallback) {
    Map<String, Object> variables = getFinalVariablesMap(serviceV2InfoConfig, Map.of(), logCallback);
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
    if (isEmpty(envVariables)) {
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

  @VisibleForTesting
  protected ChildrenExecutableResponse executeFreezePart(
      Ambiance ambiance, Map<FreezeEntityType, List<String>> entityMap) {
    if (ngFeatureFlagHelperService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_DEPLOYMENT_FREEZE)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(ngFeatureFlagHelperService, accountId, orgId, projectId,
              accessControlClient, CDNGRbacUtility.constructPrincipalFromAmbiance(ambiance))) {
        return null;
      }
      List<FreezeSummaryResponseDTO> globalFreezeConfigs;
      List<FreezeSummaryResponseDTO> manualFreezeConfigs;
      globalFreezeConfigs = freezeEvaluateService.anyGlobalFreezeActive(accountId, orgId, projectId);
      manualFreezeConfigs = freezeEvaluateService.getActiveManualFreezeEntities(accountId, orgId, projectId, entityMap);
      if (globalFreezeConfigs.size() + manualFreezeConfigs.size() > 0) {
        log.info("Deployment Freeze is Active for the given service.");
        sweepingOutputService.consume(ambiance, ServiceStepV3Constants.FREEZE_SWEEPING_OUTPUT,
            FreezeOutcome.builder()
                .frozen(true)
                .manualFreezeConfigs(manualFreezeConfigs)
                .globalFreezeConfigs(globalFreezeConfigs)
                .build(),
            "");
        log.info("Adding Children as empty.");
        String executionUrl =
            engineExpressionService.renderExpression(ambiance, ServiceStepV3Constants.PIPELINE_EXECUTION_EXPRESSION,
                ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        String baseUrl = ngExpressionHelper.getBaseUrl(AmbianceUtils.getAccountId(ambiance));
        notificationHelper.sendNotificationForFreezeConfigs(
            manualFreezeConfigs, globalFreezeConfigs, ambiance, executionUrl, baseUrl);
        return ChildrenExecutableResponse.newBuilder()
            .addAllLogKeys(emptyIfNull(
                StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
            .addAllChildren(Collections.emptyList())
            .build();
      }
    }
    return null;
  }

  @VisibleForTesting
  protected StepResponse handleFreezeResponse(
      Ambiance ambiance, NGServiceV2InfoConfig ngServiceV2InfoConfig, ServiceStepV3Parameters serviceStepParameters) {
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    if (ngFeatureFlagHelperService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_DEPLOYMENT_FREEZE)) {
      final OptionalSweepingOutput freezeOutcomeOptional = sweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.FREEZE_SWEEPING_OUTPUT));

      if (freezeOutcomeOptional.isFound()) {
        FreezeOutcome freezeOutcome = (FreezeOutcome) freezeOutcomeOptional.getOutput();
        if (freezeOutcome.isFrozen()) {
          frozenExecutionService.createFrozenExecution(
              ambiance, freezeOutcome.getManualFreezeConfigs(), freezeOutcome.getGlobalFreezeConfigs());

          stepOutcomes.add(StepResponse.StepOutcome.builder()
                               .name(OutcomeExpressionConstants.FREEZE_OUTCOME)
                               .outcome(freezeOutcome)
                               .group(StepCategory.STAGE.name())
                               .build());
          stepOutcomes.add(
              StepResponse.StepOutcome.builder()
                  .name(OutcomeExpressionConstants.SERVICE)
                  .outcome(ServiceStepOutcome.fromServiceStepV2(serviceStepParameters.getServiceRef().getValue().trim(),
                      ngServiceV2InfoConfig.getName(),
                      ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(),
                      ngServiceV2InfoConfig.getDescription(), ngServiceV2InfoConfig.getTags(),
                      ngServiceV2InfoConfig.getGitOpsEnabled()))
                  .group(StepCategory.STAGE.name())
                  .build());
          return StepResponse.builder()
              .stepOutcomes(stepOutcomes)
              .failureInfo(FailureInfo.newBuilder()
                               .addFailureData(FailureData.newBuilder()
                                                   .addFailureTypes(FailureType.FREEZE_ACTIVE_FAILURE)
                                                   .setLevel(Level.ERROR.name())
                                                   .setCode(FREEZE_EXCEPTION.name())
                                                   .setMessage("Pipeline Aborted due to freeze")
                                                   .build())
                               .build())
              .status(Status.FREEZE_FAILED)
              .build();
        }
      }
    }
    return null;
  }

  @Data
  @Builder
  static class ServicePartResponse {
    NGServiceConfig ngServiceConfig;
  }
}
