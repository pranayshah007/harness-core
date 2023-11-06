/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_ENV_OUTCOME;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENV_GROUP_REF;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENV_REF;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENV_VARIABLES_PATTERN_REGEX;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDES_COMMAND_UNIT;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_CONFIGURATION_NOT_FOUND;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_VARIABLES_PATTERN_REGEX;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_GIT_BRANCH_EXPRESSION;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.helper.EnvironmentMapper;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorHelper;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.steps.GitOpsEnvOutCome;
import io.harness.cdng.hooks.steps.ServiceHooksOutcome;
import io.harness.cdng.k8s.HarnessRelease;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.service.steps.ServiceStepV3Helper.ServicePartResponse;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.exception.WingsException;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStepV3 implements ChildrenExecutable<ServiceStepV3Parameters> {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private ServiceStepOverrideHelper serviceStepOverrideHelper;
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private ServiceCustomSweepingOutputHelper serviceCustomSweepingOutputHelper;
  @Inject private ServiceEntityYamlSchemaHelper serviceEntityYamlSchemaHelper;

  @Inject private ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceStepV3Helper serviceStepV3Helper;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private static final Pattern serviceVariablePattern = Pattern.compile(SERVICE_VARIABLES_PATTERN_REGEX);
  private static final Pattern envVariablePattern = Pattern.compile(ENV_VARIABLES_PATTERN_REGEX);
  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";

  @Override
  public Class<ServiceStepV3Parameters> getStepParametersClass() {
    return ServiceStepV3Parameters.class;
  }

  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, StepInputPackage inputPackage) {
    validate(stepParameters);

    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      final NGLogCallback serviceStepLogCallback =
          serviceStepsHelper.getServiceLogCallback(ambiance, true, SERVICE_STEP_COMMAND_UNIT);
      final NGLogCallback overrideLogCallback =
          serviceStepsHelper.getServiceLogCallback(ambiance, true, OVERRIDES_COMMAND_UNIT);

      serviceStepV3Helper.saveExecutionLog(serviceStepLogCallback, "Starting service step...");

      Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

      final ServicePartResponse servicePartResponse = executeServicePart(ambiance, stepParameters, entityMap);

      serviceStepV3Helper.saveExecutionLog(serviceStepLogCallback,
          "Service Name: " + servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig().getName()
              + " , Identifier: "
              + servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig().getIdentifier());

      performFiltering(stepParameters, ambiance,
          EnvironmentInfraFilterHelper.getNGTags(
              servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig().getTags()));
      // Support GitOps Flow
      // If environment group is only set for GitOps or if GitOps flow and deploying to multi-environments
      if (ParameterField.isNotNull(stepParameters.getGitOpsMultiSvcEnvEnabled())
          && stepParameters.getGitOpsMultiSvcEnvEnabled().getValue()) {
        handleMultipleEnvironmentsPart(ambiance, stepParameters, servicePartResponse, serviceStepLogCallback);
      } else {
        executeEnvironmentPart(
            ambiance, stepParameters, servicePartResponse, serviceStepLogCallback, entityMap, overrideLogCallback);
      }

      ChildrenExecutableResponse childrenExecutableResponse = serviceStepV3Helper.executeFreezePart(
          ambiance, entityMap, List.of(SERVICE_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT));
      if (childrenExecutableResponse != null) {
        return childrenExecutableResponse;
      }

      return ChildrenExecutableResponse.newBuilder()
          .addAllLogKeys(emptyIfNull(StepUtils.generateLogKeys(
              StepUtils.generateLogAbstractions(ambiance), List.of(SERVICE_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT))))
          .addAllUnits(List.of(SERVICE_STEP_COMMAND_UNIT, OVERRIDES_COMMAND_UNIT))
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
    Set<String> envRefs = envClusterRefs.stream().map(EnvClusterRefs::getEnvRef).collect(Collectors.toSet());
    List<ParameterField<String>> envRefsList =
        envRefs.stream().map(ParameterField::createValueField).collect(Collectors.toList());
    stepParameters.setEnvRefs(envRefsList);
  }

  private void validate(ServiceStepV3Parameters stepParameters) {
    validateNullCheckForRefs(stepParameters);
    checkIfServiceRefIsExpAndThrow(stepParameters.getServiceRef());
    checkIfEnvTypesEntityRefIsExpAndThrow(stepParameters.getEnvRef(), ENV_REF,
        "[Hint]: service variables expression should not be used as environment ref.");
    checkForMultiEnvRefsAreExpAndThrow(stepParameters.getEnvRefs());
    checkIfEnvTypesEntityRefIsExpAndThrow(stepParameters.getEnvGroupRef(), ENV_GROUP_REF,
        "[Hint]: service variables expression should not be used as environment group ref.");
  }
  private void checkIfEnvTypesEntityRefIsExpAndThrow(
      ParameterField<String> envTypeEntityRef, String entityTypeName, String suffixErrorMessage) {
    if (ParameterField.isNotNull(envTypeEntityRef) && envTypeEntityRef.isExpression()) {
      String errorSuffix = StringUtils.EMPTY;
      if (serviceVariablePattern.matcher(envTypeEntityRef.getExpressionValue()).matches()) {
        errorSuffix = suffixErrorMessage;
      }
      throw new InvalidRequestException(String.format("Expression (%s) given for %s could not be resolved. ",
                                            envTypeEntityRef.getExpressionValue(), entityTypeName)
          + errorSuffix);
    }
  }

  private void checkForMultiEnvRefsAreExpAndThrow(List<ParameterField<String>> envRefs) {
    if (isNotEmpty(envRefs)) {
      String errorSuffix = StringUtils.EMPTY;
      List<String> envRefExpValues = new ArrayList<>();
      for (ParameterField<String> envRef : envRefs) {
        if (ParameterField.isNotNull(envRef) && envRef.isExpression()) {
          envRefExpValues.add(envRef.getExpressionValue());
          if (serviceVariablePattern.matcher(envRef.getExpressionValue()).matches()) {
            errorSuffix = "[Hint]: service variables expression should not be used as environment refs.";
          }
        }
      }
      if (isNotEmpty(envRefExpValues)) {
        throw new UnresolvedExpressionsException(envRefExpValues,
            String.format("Expression (%s) given for environment refs could not be resolved. %s",
                envRefExpValues.stream().filter(Objects::nonNull).collect(Collectors.joining(", ")), errorSuffix));
      }
    }
  }

  private void checkIfServiceRefIsExpAndThrow(ParameterField<String> serviceRef) {
    if (serviceRef.isExpression()) {
      String errorSuffix = StringUtils.EMPTY;
      if (envVariablePattern.matcher(serviceRef.getExpressionValue()).matches()) {
        errorSuffix = "[Hint]: environment variables expression should not be used as service ref.";
      }
      throw new InvalidRequestException(String.format("Expression (%s) given for service ref could not be resolved. %s",
          serviceRef.getExpressionValue(), errorSuffix));
    }
  }

  private void validateNullCheckForRefs(ServiceStepV3Parameters stepParameters) {
    if (ParameterField.isNull(stepParameters.getServiceRef())) {
      throw new InvalidRequestException("service ref not provided");
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
      ServicePartResponse servicePartResponse, NGLogCallback serviceStepLogCallback) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

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
        serviceStepV3Helper.setYamlInEnvironment(environment);
      }
      try {
        if (isNotEmpty(parameters.getEnvToEnvInputs())) {
          ngEnvironmentConfig =
              serviceStepV3Helper.mergeEnvironmentInputs(accountId, environment.getIdentifier(), environment.getYaml(),
                  parameters.getEnvToEnvInputs().get(
                      getEnvRefOrId(environment.fetchRef(), parameters.getEnvGroupRef(), environment.getIdentifier())));
        } else {
          ngEnvironmentConfig = serviceStepV3Helper.mergeEnvironmentInputs(
              accountId, environment.getIdentifier(), environment.getYaml(), null);
        }
      } catch (IOException ex) {
        throw new InvalidRequestException(format("Unable to read yaml for environment [Name: %s, Identifier: %s]",
                                              environment.getName(), environment.getIdentifier()),
            ex);
      }
      List<NGVariable> variables = ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables();
      envToEnvVariables.put(
          EnvironmentStepsUtils.inheritEnvGroupScope(environment.fetchRef(), parameters.getEnvGroupRef()),
          NGVariablesUtils.getMapOfVariables(variables));
      if (variables != null) {
        secretNGVariables.addAll(
            variables.stream().filter(SecretNGVariable.class ::isInstance).collect(Collectors.toList()));
      }
      final Optional<NGServiceOverridesEntity> ngServiceOverridesEntity = serviceOverrideService.get(accountId,
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
          environment.fetchRef(), parameters.getServiceRef().getValue());
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
        envToSvcVariables.put(
            EnvironmentStepsUtils.inheritEnvGroupScope(environment.fetchRef(), parameters.getEnvGroupRef()),
            NGVariablesUtils.getMapOfVariables(svcOverrideVariables));
      }
    }

    serviceStepsHelper.checkForAccessOrThrow(ambiance, secretNGVariables);

    serviceStepV3Helper.resolve(ambiance, envToEnvVariables, envToSvcVariables);

    GitOpsEnvOutCome gitOpsEnvOutCome = new GitOpsEnvOutCome(envToEnvVariables, envToSvcVariables);

    sweepingOutputService.consume(ambiance, GITOPS_ENV_OUTCOME, gitOpsEnvOutCome, StepCategory.STAGE.name());

    serviceStepV3Helper.processServiceAndEnvironmentVariables(
        ambiance, servicePartResponse, serviceStepLogCallback, null, false, new HashMap<>());

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

    serviceStepOverrideHelper.prepareAndSaveFinalServiceHooksMetadataToSweepingOutput(
        servicePartResponse.getNgServiceConfig(), ambiance, ServiceStepV3Constants.SERVICE_HOOKS_SWEEPING_OUTPUT);
  }

  private String getEnvRefOrId(String envRef, ParameterField<String> envGroupRef, String envId) {
    if (ParameterField.isNull(envGroupRef) || StringUtils.isEmpty(envGroupRef.getValue())) {
      return envRef;
    }
    return envId;
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
    List<String> envRefsIds = envRefs.stream().map(ParameterField::getValue).collect(Collectors.toList());

    List<Environment> environments =
        environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), envRefsIds);

    if (environments.isEmpty()) {
      throw new InvalidRequestException(
          "Unable to fetch environments from environment identifiers. Please verify if referred environments still exist");
    }
    return environments;
  }

  private void executeEnvironmentPart(Ambiance ambiance, ServiceStepV3Parameters parameters,
      ServicePartResponse servicePartResponse, NGLogCallback serviceStepLogCallback,
      Map<FreezeEntityType, List<String>> entityMap, NGLogCallback overrideLogCallback) throws IOException {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    final ParameterField<String> envRef = parameters.getEnvRef();
    if (ParameterField.isNull(envRef)) {
      throw new InvalidRequestException("Environment ref not found in stage yaml");
    }

    if (envRef.isExpression()) {
      serviceStepV3Helper.resolve(ambiance, envRef);
    }

    log.info("Starting execution for Environment Step [{}]", envRef.getValue());

    EnvironmentStepsUtils.checkForEnvAccessOrThrow(accessControlClient, ambiance, envRef);

    if (envRef.fetchFinalValue() != null) {
      Optional<Environment> environment = serviceStepV3Helper.getEnvironmentWithYaml(
          accountId, orgIdentifier, projectIdentifier, parameters.getEnvRef().getValue(), parameters.getEnvGitBranch());
      if (environment.isEmpty()) {
        throw new InvalidRequestException(String.format("Environment with ref: [%s] not found", envRef.getValue()));
      }

      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
          new EnumMap<>(ServiceOverridesType.class);

      boolean isOverridesV2enabled =
          overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);

      if (isOverridesV2enabled) {
        if (!ParameterField.isNull(parameters.getInfraId()) && parameters.getInfraId().isExpression()) {
          serviceStepV3Helper.resolve(ambiance, parameters.getInfraId());
        }
        mergedOverrideV2Configs = serviceOverrideUtilityFacade.getMergedServiceOverrideConfigs(
            accountId, orgIdentifier, projectIdentifier, parameters, environment.get(), overrideLogCallback);
      }

      // handle old environments
      if (isEmpty(environment.get().getYaml())) {
        serviceStepV3Helper.setYamlInEnvironment(environment.get());
      }

      NGEnvironmentConfig ngEnvironmentConfig =
          serviceStepV3Helper.getNgEnvironmentConfig(ambiance, parameters, accountId, environment);

      if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SCOPE_INFRA_TO_SERVICES)) {
        infrastructureEntityService.checkIfInfraIsScopedToService(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
            parameters.getServiceRef().getValue(), parameters.getEnvRef().getValue(),
            parameters.getInfraId().getValue());
      }

      final Optional<NGServiceOverridesEntity> ngServiceOverridesEntity =
          serviceOverrideService.get(AmbianceUtils.getAccountId(ambiance), orgIdentifier, projectIdentifier,
              envRef.getValue(), parameters.getServiceRef().getValue());
      NGServiceOverrideConfig ngServiceOverrides = NGServiceOverrideConfig.builder().build();
      if (ngServiceOverridesEntity.isPresent()) {
        ngServiceOverrides =
            mergeSvcOverrideInputs(ngServiceOverridesEntity.get().getYaml(), parameters.getServiceOverrideInputs());
      }

      serviceStepV3Helper.resolve(ambiance, ngEnvironmentConfig, ngServiceOverrides);

      serviceStepV3Helper.handleSecretVariables(
          ngEnvironmentConfig, ngServiceOverrides, mergedOverrideV2Configs, ambiance, isOverridesV2enabled);

      entityMap.put(FreezeEntityType.ENVIRONMENT,
          Lists.newArrayList(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.get().getAccountId(),
              environment.get().getOrgIdentifier(), environment.get().getProjectIdentifier(),
              environment.get().getIdentifier())));
      entityMap.put(FreezeEntityType.ENV_TYPE, Lists.newArrayList(environment.get().getType().name()));

      Optional<EnvironmentGroupEntity> envGroupOpt = Optional.empty();
      if (ParameterField.isNotNull(parameters.getEnvGroupRef())
          && EmptyPredicate.isNotEmpty(parameters.getEnvGroupRef().getValue())) {
        envGroupOpt = environmentGroupService.get(AmbianceUtils.getAccountId(ambiance), orgIdentifier,
            projectIdentifier, parameters.getEnvGroupRef().getValue(), false);
      }
      final EnvironmentOutcome environmentOutcome =
          EnvironmentMapper.toEnvironmentOutcome(environment.get(), ngEnvironmentConfig, ngServiceOverrides,
              envGroupOpt.orElse(null), mergedOverrideV2Configs, isOverridesV2enabled);

      sweepingOutputService.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());

      serviceStepV3Helper.processServiceAndEnvironmentVariables(ambiance, servicePartResponse, serviceStepLogCallback,
          environmentOutcome, isOverridesV2enabled, mergedOverrideV2Configs);

      if (isOverridesV2enabled) {
        NGServiceV2InfoConfig ngServiceV2InfoConfig =
            servicePartResponse.getNgServiceConfig().getNgServiceV2InfoConfig();
        if (ngServiceV2InfoConfig == null) {
          throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
        }
        final String scopedEnvironmentRef =
            IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, environment.get().getOrgIdentifier(),
                environment.get().getProjectIdentifier(), environment.get().getIdentifier());
        serviceStepOverrideHelper.saveFinalManifestsToSweepingOutputV2(ngServiceV2InfoConfig, ambiance,
            ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT, mergedOverrideV2Configs, scopedEnvironmentRef);
        serviceStepOverrideHelper.saveFinalConfigFilesToSweepingOutputV2(ngServiceV2InfoConfig, mergedOverrideV2Configs,
            scopedEnvironmentRef, ambiance, ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT);
        serviceStepOverrideHelper.saveFinalAppSettingsToSweepingOutputV2(ngServiceV2InfoConfig, mergedOverrideV2Configs,
            ambiance, ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT);
        serviceStepOverrideHelper.saveFinalConnectionStringsToSweepingOutputV2(ngServiceV2InfoConfig,
            mergedOverrideV2Configs, ambiance, ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT);
      } else {
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

      serviceStepOverrideHelper.prepareAndSaveFinalServiceHooksMetadataToSweepingOutput(
          servicePartResponse.getNgServiceConfig(), ambiance, ServiceStepV3Constants.SERVICE_HOOKS_SWEEPING_OUTPUT);
    }
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

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, Map<String, ResponseData> responseDataMap) {
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    UnitProgress serviceStepUnitProgress = null;
    long serviceStepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);

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

    ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.fromServiceStepV2(
        stepParameters.getServiceRef().getValue().trim(), ngServiceV2InfoConfig.getName(),
        ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(), ngServiceV2InfoConfig.getDescription(),
        ngServiceV2InfoConfig.getTags(), ngServiceV2InfoConfig.getGitOpsEnabled());

    StepResponse stepResponse =
        serviceStepV3Helper.handleFreezeResponse(ambiance, serviceStepOutcome, OutcomeExpressionConstants.SERVICE);
    if (stepResponse != null) {
      return stepResponse;
    }

    stepResponse = SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);

    final NGLogCallback logCallback =
        serviceStepsHelper.getServiceLogCallback(ambiance, false, SERVICE_STEP_COMMAND_UNIT);
    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      serviceStepV3Helper.saveExecutionLog(logCallback,
          LogHelper.color("Failed to complete service step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
      serviceStepUnitProgress = UnitProgress.newBuilder()
                                    .setStatus(UnitStatus.FAILURE)
                                    .setUnitName(SERVICE_STEP_COMMAND_UNIT)
                                    .setStartTime(serviceStepStartTs)
                                    .setEndTime(System.currentTimeMillis())
                                    .build();
    } else {
      serviceStepV3Helper.saveExecutionLog(
          logCallback, "Completed service step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      serviceStepUnitProgress = UnitProgress.newBuilder()
                                    .setStatus(UnitStatus.SUCCESS)
                                    .setUnitName(SERVICE_STEP_COMMAND_UNIT)
                                    .setStartTime(serviceStepStartTs)
                                    .setEndTime(System.currentTimeMillis())
                                    .build();
    }
    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.SERVICE)
                         .outcome(getServiceStepOutcome(serviceRef, ngServiceV2InfoConfig))
                         .group(StepCategory.STAGE.name())
                         .build());

    serviceStepV3Helper.addManifestsOutputToStepOutcome(ambiance, stepOutcomes);

    final OptionalSweepingOutput artifactsOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutput.isFound()) {
      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.ARTIFACTS)
                           .outcome((ArtifactsOutcome) artifactsOutput.getOutput())
                           .group(StepCategory.STAGE.name())
                           .build());
    }

    serviceStepV3Helper.addConfigFilesOutputToStepOutcome(ambiance, stepOutcomes);

    final OptionalSweepingOutput serviceHooksOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.SERVICE_HOOKS));
    if (serviceHooksOutput.isFound()) {
      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.SERVICE_HOOKS)
                           .outcome((ServiceHooksOutcome) serviceHooksOutput.getOutput())
                           .group(StepCategory.STAGE.name())
                           .build());
    }
    // Todo: Add azure outcomes here
    stepResponse = stepResponse.withStepOutcomes(stepOutcomes);
    serviceStepsHelper.saveServiceExecutionDataToStageInfo(ambiance, stepResponse);

    UnitProgress overridesUnit = UnitProgress.newBuilder()
                                     .setStatus(UnitStatus.SUCCESS)
                                     .setUnitName(OVERRIDES_COMMAND_UNIT)
                                     .setStartTime(serviceStepStartTs)
                                     .setEndTime(System.currentTimeMillis())
                                     .build();

    return stepResponse.toBuilder().unitProgressList(List.of(overridesUnit, serviceStepUnitProgress)).build();
  }

  private ServiceStepOutcome getServiceStepOutcome(String serviceRef, NGServiceV2InfoConfig ngServiceV2InfoConfig) {
    Optional<HarnessRelease> harnessReleaseOptional =
        getHarnessReleaseOptional(ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec());
    if (harnessReleaseOptional.isPresent()) {
      HarnessRelease harnessRelease = harnessReleaseOptional.get();
      return ServiceStepOutcome.fromServiceStepV2(serviceRef, ngServiceV2InfoConfig.getName(),
          ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(), ngServiceV2InfoConfig.getDescription(),
          ngServiceV2InfoConfig.getTags(), ngServiceV2InfoConfig.getGitOpsEnabled(), harnessRelease);
    }

    return ServiceStepOutcome.fromServiceStepV2(serviceRef, ngServiceV2InfoConfig.getName(),
        ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(), ngServiceV2InfoConfig.getDescription(),
        ngServiceV2InfoConfig.getTags(), ngServiceV2InfoConfig.getGitOpsEnabled());
  }

  private Optional<HarnessRelease> getHarnessReleaseOptional(ServiceSpec serviceSpec) {
    if (serviceSpec instanceof KubernetesServiceSpec) {
      KubernetesServiceSpec spec = (KubernetesServiceSpec) serviceSpec;
      return Optional.ofNullable(spec.getRelease());
    }
    if (serviceSpec instanceof NativeHelmServiceSpec) {
      NativeHelmServiceSpec spec = (NativeHelmServiceSpec) serviceSpec;
      return Optional.ofNullable(spec.getRelease());
    }
    return Optional.empty();
  }

  private ServicePartResponse executeServicePart(
      Ambiance ambiance, ServiceStepV3Parameters stepParameters, Map<FreezeEntityType, List<String>> entityMap) {
    final Optional<ServiceEntity> serviceOpt = getServiceEntityWithYaml(ambiance, stepParameters);

    if (serviceOpt.isEmpty()) {
      throw new InvalidRequestException(
          format("service with ref: [%s] not found", stepParameters.getServiceRef().fetchFinalValue()));
    }

    final ServiceEntity serviceEntity = serviceOpt.get();

    String mergedServiceYaml;
    if (stepParameters.getInputs() != null && isNotEmpty(stepParameters.getInputs().getValue())) {
      mergedServiceYaml = mergeServiceInputsIntoService(serviceEntity.getYaml(), stepParameters.getInputs().getValue());
    } else {
      mergedServiceYaml = serviceEntity.getYaml();
    }

    final NGServiceConfig ngServiceConfig = getNgServiceConfig(AmbianceUtils.getAccountId(ambiance), mergedServiceYaml,
        serviceEntity.getName(), serviceEntity.getIdentifier());

    sweepingOutputService.consume(ambiance, ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT,
        ServiceSweepingOutput.builder().finalServiceYaml(mergedServiceYaml).build(), "");

    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();

    if (ngServiceV2InfoConfig.getServiceDefinition() == null) {
      throw new InvalidRequestException(
          format("Service Definition is not defined for service [Name: %s, Identifier: %s]", serviceEntity.getName(),
              serviceEntity.getIdentifier()));
    }

    if (ngServiceV2InfoConfig.getServiceDefinition().getType() != null && stepParameters.getDeploymentType() != null
        && ngServiceV2InfoConfig.getServiceDefinition().getType() != stepParameters.getDeploymentType()) {
      throw new InvalidRequestException(format("Deployment type of the stage [%s] and the service [%s] do not match",
          stepParameters.getDeploymentType().getYamlName(),
          ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName()));
    }

    serviceCustomSweepingOutputHelper.saveAdditionalServiceFieldsToSweepingOutput(ngServiceConfig, ambiance);

    serviceStepsHelper.checkForVariablesAccessOrThrow(
        ambiance, ngServiceConfig, stepParameters.getServiceRef().getValue());

    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(AmbianceUtils.getOrgIdentifier(ambiance)));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(AmbianceUtils.getProjectIdentifier(ambiance)));
    // serviceRef instead of identifier to be passed here
    entityMap.put(FreezeEntityType.SERVICE,
        Lists.newArrayList(IdentifierRefHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
            serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier())));

    entityMap.put(FreezeEntityType.PIPELINE, Lists.newArrayList(AmbianceUtils.getPipelineIdentifier(ambiance)));

    ServiceStepOutcome outcome = ServiceStepOutcome.fromServiceStepV2(serviceEntity, ngServiceV2InfoConfig);

    sweepingOutputService.consume(ambiance, OutcomeExpressionConstants.SERVICE, outcome, StepCategory.STAGE.name());

    return ServicePartResponse.builder().ngServiceConfig(ngServiceConfig).build();
  }

  private Optional<ServiceEntity> getServiceEntityWithYaml(Ambiance ambiance, ServiceStepV3Parameters stepParameters) {
    String serviceGitBranch = getServiceGitBranch(stepParameters);
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceGitBranch)) {
      return serviceEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getServiceRef().getValue(), false);
    }
  }

  private String getServiceGitBranch(ServiceStepV3Parameters stepParameters) {
    String serviceGitBranch;
    if (isBlank(stepParameters.getServiceGitBranch())
        || SERVICE_GIT_BRANCH_EXPRESSION.equals(stepParameters.getServiceGitBranch())) {
      serviceGitBranch = null;
    } else {
      serviceGitBranch = stepParameters.getServiceGitBranch();
    }
    return serviceGitBranch;
  }

  private NGServiceConfig getNgServiceConfig(
      String accountId, String mergedServiceYaml, String name, String identifier) {
    final NGServiceConfig ngServiceConfig;
    try {
      ngServiceConfig = YamlUtils.read(mergedServiceYaml, NGServiceConfig.class);
    } catch (IOException e) {
      serviceEntityYamlSchemaHelper.validateSchema(accountId, mergedServiceYaml);
      log.error(String.format(
          "Service schema validation succeeded but failed to convert service yaml to service config [%s]", identifier));
      throw new InvalidRequestException(
          format("Unable to read yaml for service [Name: %s, Identifier: %s]", name, identifier), e);
    }
    return ngServiceConfig;
  }

  private String mergeServiceInputsIntoService(String originalServiceYaml, Map<String, Object> serviceInputs) {
    Map<String, Object> serviceInputsYaml = new HashMap<>();
    serviceInputsYaml.put(YamlTypes.SERVICE_ENTITY, serviceInputs);
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalServiceYaml, YamlPipelineUtils.writeYamlString(serviceInputsYaml), true, true);
  }
}
