/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.FeatureName.CDP_SKIP_DEFAULT_VALUES_YAML_CG;
import static io.harness.beans.FeatureName.CUSTOM_MANIFEST;
import static io.harness.beans.FeatureName.DISABLE_HELM_REPO_YAML_CACHE;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.beans.FeatureName.OVERRIDE_VALUES_YAML_FROM_HELM_CHART;
import static io.harness.beans.FeatureName.USE_LATEST_CHARTMUSEUM_VERSION;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.helm.HelmConstants.DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS;
import static io.harness.helm.HelmConstants.HELM_NAMESPACE_PLACEHOLDER_REGEX;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.TaskType.HELM_COMMAND_TASK;
import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.sm.ExecutionContextImpl.PHASE_PARAM;
import static software.wings.sm.StateType.HELM_DEPLOY;
import static software.wings.sm.StateType.HELM_ROLLBACK;
import static software.wings.sm.states.k8s.K8sStateHelper.fetchSafeTimeoutInMillis;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.KubernetesConvention;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceConfig;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.HelmDeployContextElement;
import software.wings.api.HelmDeployStateExecutionData;
import software.wings.api.HelmDeployStateExecutionData.HelmDeployStateExecutionDataBuilder;
import software.wings.api.InfraMappingElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.helm.HelmReleaseInfoElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.HelmDummyCommandUnit;
import software.wings.beans.command.HelmDummyCommandUnitConstants;
import software.wings.beans.container.ContainerTaskCommons;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.dto.Log;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.helm.HelmCommandExecutionResponse;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest.HelmInstallCommandRequestBuilder;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.helm.response.ReleaseInfo;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.HelmChartConfigHelperService;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ContainerHelper;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionContext.StateExecutionContextBuilder;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.k8s.K8sStateHelper;
import software.wings.sm.states.utils.StateTimeoutUtils;
import software.wings.stencils.DefaultValue;
import software.wings.utils.ApplicationManifestUtils;
import software.wings.utils.HelmChartSpecificationMapper;

import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by anubhaw on 3/25/18.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "HelmDeployStateKeys")
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class HelmDeployState extends State {
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private DelegateService delegateService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ActivityService activityService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentHelper;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private TemplateExpressionProcessor templateExpressionProcessor;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private HelmChartConfigHelperService helmChartConfigHelperService;
  @Inject private ServiceTemplateHelper serviceTemplateHelper;
  @Inject private K8sStateHelper k8sStateHelper;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private HelmHelper helmHelper;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject private LogService logService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private EnvironmentService environmentService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @DefaultValue("10") private int steadyStateTimeout; // Minutes

  // This field is in fact representing helmReleaseName. We will change iGIT_HOST_CONNECTIVITYt later on
  @Getter @Setter private String helmReleaseNamePrefix;
  @Getter @Setter private GitFileConfig gitFileConfig;
  @Getter @Setter private String commandFlags;
  @Getter @Setter @Attributes(title = "Ignore release hist failure") private boolean ignoreReleaseHistFailure;

  public static final String HELM_COMMAND_NAME = "Helm Deploy";
  private static final String DOCKER_IMAGE_TAG_PLACEHOLDER_REGEX = "\\$\\{DOCKER_IMAGE_TAG}";
  private static final String DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX = "\\$\\{DOCKER_IMAGE_NAME}";
  private static final String NO_PREV_DEPLOYMENT = "No previous version available for rollback";
  private static final String VALID_YAML_FILE_EXTENSIONS_REGEX = "(?i)(yaml|yml)$";

  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public HelmDeployState(String name) {
    super(name, HELM_DEPLOY.name());
  }

  public HelmDeployState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public Integer getTimeoutMillis() {
    return StateTimeoutUtils.getTimeoutMillisFromMinutes(steadyStateTimeout);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) throws InterruptedException {
    boolean valuesInGit = false;
    boolean valuesInHelmChartRepo = false;
    boolean isCustomManifestSource = false;
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new EnumMap<>(K8sValuesLocation.class);
    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap = new EnumMap<>(K8sValuesLocation.class);

    if (HELM_DEPLOY.name().equals(this.getStateType()) || HELM_ROLLBACK.name().equals(this.getStateType())) {
      appManifestMap = applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);
      helmOverrideManifestMap = applicationManifestUtils.getApplicationManifests(context, HELM_CHART_OVERRIDE);
      valuesInHelmChartRepo = applicationManifestUtils.isValuesInHelmChartRepo(context);
      valuesInGit = applicationManifestUtils.isValuesInGit(appManifestMap);
      isCustomManifestSource = applicationManifestUtils.isCustomManifest(context);
    }

    Activity activity =
        createActivity(context, getCommandUnits(valuesInGit, valuesInHelmChartRepo, isCustomManifestSource));

    boolean isCustomManifestFeatureEnabled =
        featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, context.getAccountId());
    if (valuesInHelmChartRepo) {
      return executeHelmValuesFetchTask(context, activity.getUuid(), helmOverrideManifestMap, appManifestMap);
    }
    if (valuesInGit) {
      return executeGitTask(context, activity.getUuid(), appManifestMap);
    }

    if (isCustomManifestSource) {
      if (!isCustomManifestFeatureEnabled) {
        throw new InvalidRequestException("Custom manifest can not be used with feature flag off", USER);
      }
      return executeCustomManifestFetchTask(context, activity.getUuid(), appManifestMap);
    }

    return executeHelmTask(context, activity.getUuid(), appManifestMap, helmOverrideManifestMap);
  }

  private ExecutionResponse executeCustomManifestFetchTask(
      ExecutionContext context, String activityId, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    ApplicationManifest applicationManifest = appManifestMap.get(K8sValuesLocation.Service);
    CustomSourceConfig customSourceConfig = null;
    if (applicationManifest != null) {
      customSourceConfig = applicationManifest.getCustomSourceConfig();
    }

    if (customSourceConfig != null && customSourceConfig.getScript() == null) {
      throw new InvalidRequestException("Script can not be null for custom manifest source", USER);
    }

    CustomManifestValuesFetchParams fetchValuesParams =
        applicationManifestUtils.createCustomManifestValuesFetchParams(context, appManifestMap, VALUES_YAML_KEY);
    fetchValuesParams.setActivityId(activityId);
    fetchValuesParams.setCommandUnitName(FetchFiles);
    fetchValuesParams.setAppId(context.getAppId());
    fetchValuesParams.setDelegateSelectors(getDelegateSelectors(applicationManifest, context));

    // CustomSourceConfig will be null if task is only to fetch value
    fetchValuesParams.setCustomManifestSource(customSourceConfig == null
            ? null
            : CustomManifestSource.builder()
                  .filePaths(Arrays.asList(customSourceConfig.getPath()))
                  .script(customSourceConfig.getScript())
                  .build());

    Environment env = k8sStateHelper.fetchEnvFromExecutionContext(context);
    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);
    final int expressionFunctorToken = HashGenerator.generateIntegerHash();
    String serviceTemplateId = infraMapping == null ? null : serviceTemplateHelper.fetchServiceTemplateId(infraMapping);

    HelmDeployStateExecutionDataBuilder helmDeployStateExecutionDataBuilder =
        HelmDeployStateExecutionData.builder()
            .activityId(activityId)
            .commandName(HELM_COMMAND_NAME)
            .currentTaskType(TaskType.CUSTOM_MANIFEST_FETCH_TASK)
            .appManifestMap(appManifestMap);

    StateExecutionContext stateExecutionContext =
        buildStateExecutionContext(helmDeployStateExecutionDataBuilder, expressionFunctorToken);

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(context.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env == null ? null : env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env == null ? null : env.getEnvironmentType().name())
            .setupAbstraction(
                Cd1SetupFields.SERVICE_ID_FIELD, infraMapping == null ? null : infraMapping.getServiceId())
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, serviceTemplateId)
            .setupAbstraction(
                Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infraMapping == null ? null : infraMapping.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.CUSTOM_MANIFEST_FETCH_TASK.name())
                      .parameters(new Object[] {fetchValuesParams})
                      .timeout(K8sStateHelper.fetchSafeTimeoutInMillis(getTimeoutMillis(context)))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    renderDelegateTask(context, delegateTask, stateExecutionContext);

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);

    Map<K8sValuesLocation, Collection<String>> valuesFiles = new EnumMap<>(K8sValuesLocation.class);
    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    if (stateExecutionData != null) {
      valuesFiles.putAll(stateExecutionData.getValuesFiles());
    }
    helmDeployStateExecutionDataBuilder.valuesFiles(valuesFiles);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(delegateTaskId))
        .stateExecutionData(helmDeployStateExecutionDataBuilder.build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private Set<String> getDelegateSelectors(ApplicationManifest applicationManifest, ExecutionContext context) {
    final Set<String> result = new HashSet<>();
    if (applicationManifest == null || applicationManifest.getCustomSourceConfig() == null) {
      return result;
    }

    result.addAll(k8sStateHelper.getRenderedAndTrimmedSelectors(
        context, applicationManifest.getCustomSourceConfig().getDelegateSelectors()));
    return result;
  }

  protected List<CommandUnit> getCommandUnits(
      boolean valuesInGit, boolean valuesInHelmChartRepo, boolean isCustomManifestSource) {
    List<CommandUnit> commandUnits = new ArrayList<>();

    if (valuesInGit || valuesInHelmChartRepo || isCustomManifestSource) {
      commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.FetchFiles));
    }

    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.Init));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.Prepare));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.InstallUpgrade));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.WaitForSteadyState));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.WrapUp));

    return commandUnits;
  }

  protected ImageDetails getImageDetails(ExecutionContext context, Artifact artifact) {
    return artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId());
  }

  protected void setNewAndPrevReleaseVersion(ExecutionContext context, Application app, String releaseName,
      ContainerServiceParams containerServiceParams, HelmDeployStateExecutionDataBuilder stateExecutionDataBuilder,
      GitConfig gitConfig, List<EncryptedDataDetail> encryptedDataDetails, String commandFlags, HelmVersion helmVersion,
      int expressionFunctorToken, HelmCommandFlag helmCommandFlag, String activityId) throws InterruptedException {
    log.info("Setting new and previous helm release version");
    int prevVersion =
        getPreviousReleaseVersion(context, app, releaseName, containerServiceParams, gitConfig, encryptedDataDetails,
            commandFlags, helmVersion, expressionFunctorToken, stateExecutionDataBuilder, helmCommandFlag, activityId);

    stateExecutionDataBuilder.releaseOldVersion(prevVersion);
    stateExecutionDataBuilder.releaseNewVersion(prevVersion + 1);
  }

  private void validateChartSpecification(HelmChartSpecification chartSpec) {
    if (chartSpec == null || (isEmpty(chartSpec.getChartName()) && isEmpty(chartSpec.getChartUrl()))) {
      throw new InvalidRequestException(
          "Invalid chart specification. " + (chartSpec == null ? "Chart Specification is null" : chartSpec.toString()),
          WingsException.USER);
    }
  }

  protected HelmCommandRequest getHelmCommandRequest(ExecutionContext context,
      HelmChartSpecification helmChartSpecification, ContainerServiceParams containerServiceParams, String releaseName,
      String accountId, String appId, String activityId, ImageDetails imageDetails, String repoName,
      GitConfig gitConfig, List<EncryptedDataDetail> encryptedDataDetails, String commandFlags,
      K8sDelegateManifestConfig manifestConfig, Map<K8sValuesLocation, ApplicationManifest> appManifestMap,
      HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    List<String> helmValueOverridesYamlFilesEvaluated =
        getValuesYamlOverrides(context, containerServiceParams, imageDetails, appManifestMap);

    steadyStateTimeout = steadyStateTimeout > 0 ? steadyStateTimeout : DEFAULT_STEADY_STATE_TIMEOUT;

    HelmInstallCommandRequestBuilder helmInstallCommandRequestBuilder =
        HelmInstallCommandRequest.builder()
            .appId(appId)
            .accountId(accountId)
            .activityId(activityId)
            .commandName(HELM_COMMAND_NAME)
            .chartSpecification(HelmChartSpecificationMapper.helmChartSpecificationDTO(helmChartSpecification))
            .releaseName(releaseName)
            .namespace(containerServiceParams.getNamespace())
            .containerServiceParams(containerServiceParams)
            .variableOverridesYamlFiles(helmValueOverridesYamlFilesEvaluated)
            .timeoutInMillis(fetchSafeTimeoutInMillis(getTimeoutMillis()))
            .repoName(repoName)
            .gitConfig(gitConfig)
            .encryptedDataDetails(encryptedDataDetails)
            .commandFlags(commandFlags)
            .sourceRepoConfig(manifestConfig)
            .helmVersion(helmVersion)
            .helmCommandFlag(helmCommandFlag)
            .mergeCapabilities(
                featureFlagService.isEnabled(FeatureName.HELM_MERGE_CAPABILITIES, context.getAccountId()))
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(FeatureName.GIT_HOST_CONNECTIVITY, context.getAccountId()))
            .optimizedFilesFetch(featureFlagService.isEnabled(OPTIMIZED_GIT_FETCH_FILES, context.getAccountId()))
            .useNewKubectlVersion(featureFlagService.isEnabled(FeatureName.NEW_KUBECTL_VERSION, context.getAccountId()))
            .ignoreReleaseHistFailStatus(this.ignoreReleaseHistFailure);

    if (gitFileConfig != null) {
      helmInstallCommandRequestBuilder.gitFileConfig(gitFileConfig);
      helmInstallCommandRequestBuilder.gitConfig(gitConfig);
      helmInstallCommandRequestBuilder.encryptedDataDetails(encryptedDataDetails);
    }
    return helmInstallCommandRequestBuilder.build();
  }

  private void setHelmCommandRequestReleaseVersion(
      HelmCommandRequest helmCommandRequest, HelmDeployStateExecutionData stateExecutionData) {
    if (helmCommandRequest instanceof HelmInstallCommandRequest) {
      HelmInstallCommandRequest helmInstallCommandRequest = (HelmInstallCommandRequest) helmCommandRequest;
      helmInstallCommandRequest.setNewReleaseVersion(stateExecutionData.getReleaseNewVersion());
    }
  }

  private String getImageName(String yamlFileContent, String imageNameTag, String domainName) {
    if (isNotEmpty(domainName)) {
      Pattern pattern = ContainerTaskCommons.compileRegexPattern(domainName);
      Matcher matcher = pattern.matcher(yamlFileContent);
      if (!matcher.find()) {
        imageNameTag = domainName + "/" + imageNameTag;
        imageNameTag = imageNameTag.replaceAll("//", "/");
      }
    }

    return imageNameTag;
  }

  protected int getPreviousReleaseVersion(ExecutionContext context, Application app, String releaseName,
      ContainerServiceParams containerServiceParams, GitConfig gitConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String commandFlags, HelmVersion helmVersion,
      int expressionFunctorToken, HelmDeployStateExecutionDataBuilder stateExecutionDataBuilder,
      HelmCommandFlag helmCommandFlag, String activityId) throws InterruptedException {
    int prevVersion = 0;
    HelmReleaseHistoryCommandRequest helmReleaseHistoryCommandRequest =
        HelmReleaseHistoryCommandRequest.builder()
            .activityId(activityId)
            .releaseName(releaseName)
            .containerServiceParams(containerServiceParams)
            .gitConfig(gitConfig)
            .encryptedDataDetails(encryptedDataDetails)
            .commandFlags(commandFlags)
            .helmCommandFlag(helmCommandFlag)
            .helmVersion(helmVersion)
            .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, context.getAccountId()))
            .build();

    ContainerInfrastructureMapping containerInfraMapping =
        (ContainerInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionDataBuilder.build())
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .data(TaskData.builder()
                      .async(false)
                      .taskType(HELM_COMMAND_TASK.name())
                      .parameters(new Object[] {helmReleaseHistoryCommandRequest})
                      .expressionFunctorToken(expressionFunctorToken)
                      .timeout(DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS * 2)
                      .build())
            .accountId(app.getAccountId())
            .description("Helm Release History")
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, containerInfraMapping.getEnvId())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
                environmentService.get(containerInfraMapping.getAppId(), containerInfraMapping.getEnvId())
                    .getEnvironmentType()
                    .name())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfraMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD,
                serviceTemplateHelper.fetchServiceTemplateId(containerInfraMapping))
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    renderDelegateTask(context, delegateTask, stateExecutionContext);
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    stateExecutionDataBuilder.commandFlags(
        expressionEvaluator.substitute(helmReleaseHistoryCommandRequest.getCommandFlags(), Collections.emptyMap()));

    HelmCommandExecutionResponse helmCommandExecutionResponse;
    appendDelegateTaskDetails(context, delegateTask);
    DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
    if (notifyResponseData instanceof HelmCommandExecutionResponse) {
      helmCommandExecutionResponse = (HelmCommandExecutionResponse) notifyResponseData;
    } else {
      StringBuilder builder = new StringBuilder(256);
      builder.append("Failed to find the previous helm release version. ");
      if (HelmVersion.isHelmV3(helmVersion)) {
        builder.append("Make sure Helm 3 is installed");
      } else {
        builder.append("Make sure that the helm client and tiller is installed");
      }

      if (gitConfig != null) {
        builder.append(" and delegate has git connectivity");
      }

      SettingValue value = containerServiceParams.getSettingAttribute().getValue();
      if (value instanceof KubernetesClusterConfig && ((KubernetesClusterConfig) value).isUseKubernetesDelegate()) {
        builder.append(" and correct delegate name is selected in the cloud provider");
      }

      log.info(builder.toString());
      throw new InvalidRequestException(builder.toString(), WingsException.USER);
    }

    if (helmCommandExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      List<ReleaseInfo> releaseInfoList =
          ((HelmReleaseHistoryCommandResponse) helmCommandExecutionResponse.getHelmCommandResponse())
              .getReleaseInfoList();
      prevVersion = isEmpty(releaseInfoList)
          ? 0
          : Integer.parseInt(releaseInfoList.get(releaseInfoList.size() - 1).getRevision());
    } else {
      String errorMsg = helmCommandExecutionResponse.getErrorMessage();
      throw new InvalidRequestException(errorMsg);
    }
    return prevVersion;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response)
      throws InterruptedException {
    HelmDeployStateExecutionData helmStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();

    boolean isCustomManifestFeatureEnabled =
        featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, context.getAccountId());

    TaskType taskType = helmStateExecutionData.getCurrentTaskType();
    switch (taskType) {
      case HELM_VALUES_FETCH:
        return handleAsyncResponseForHelmFetchTask(context, response);
      case GIT_COMMAND:
        return handleAsyncResponseForGitFetchFilesTask(context, response);
      case HELM_COMMAND_TASK:
        return handleAsyncResponseForHelmTask(context, response);
      case CUSTOM_MANIFEST_FETCH_TASK:
        if (isCustomManifestFeatureEnabled) {
          return handleAsyncCustomManifestFetchTask(context, response);
        }
        // fallthrough to ignore branch if FF is not enabled
      default:
        throw new UnsupportedOperationException("Unhandled task type " + taskType);
    }
  }

  private ExecutionResponse handleAsyncCustomManifestFetchTask(
      ExecutionContext context, Map<String, ResponseData> response) throws InterruptedException {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = obtainActivityId(context);
    CustomManifestValuesFetchResponse executionResponse =
        (CustomManifestValuesFetchResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .errorMessage(executionResponse.getErrorMessage())
          .build();
    }

    HelmDeployStateExecutionData helmDeployStateExecutionData = context.getStateExecutionData();
    if (executionResponse.getValuesFilesContentMap() != null) {
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap = helmDeployStateExecutionData.getAppManifestMap();
      Map<K8sValuesLocation, Collection<String>> valuesFiles =
          applicationManifestUtils.getValuesFilesFromCustomFetchValuesResponse(
              context, appManifestMap, executionResponse, VALUES_YAML_KEY);
      helmDeployStateExecutionData.getValuesFiles().putAll(valuesFiles);
    }

    helmDeployStateExecutionData.setZippedManifestFileId(executionResponse.getZippedManifestFileId());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap =
        applicationManifestUtils.getOverrideApplicationManifests(context, AppManifestKind.VALUES);

    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap =
        applicationManifestUtils.getOverrideApplicationManifests(context, HELM_CHART_OVERRIDE);

    return executeHelmTask(context, activityId, appManifestMap, helmOverrideManifestMap);
  }

  private ExecutionResponse handleAsyncResponseForHelmFetchTask(
      ExecutionContext context, Map<String, ResponseData> response) throws InterruptedException {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = obtainActivityId(context);
    HelmValuesFetchTaskResponse executionResponse = (HelmValuesFetchTaskResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .errorMessage(executionResponse.getErrorMessage())
          .build();
    }

    if (isNotEmpty(executionResponse.getMapK8sValuesLocationToContent())) {
      HelmDeployStateExecutionData helmDeployStateExecutionData =
          (HelmDeployStateExecutionData) context.getStateExecutionData();
      Map<K8sValuesLocation, List<String>> mapK8sValuesLocationToNonEmptyContents =
          applicationManifestUtils.getMapK8sValuesLocationToNonEmptyContents(
              executionResponse.getMapK8sValuesLocationToContent());
      helmDeployStateExecutionData.getValuesFiles().putAll(mapK8sValuesLocationToNonEmptyContents);
    }

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap =
        applicationManifestUtils.getOverrideApplicationManifests(context, AppManifestKind.VALUES);
    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap =
        applicationManifestUtils.getOverrideApplicationManifests(context, HELM_CHART_OVERRIDE);

    boolean valuesInGit = applicationManifestUtils.isValuesInGit(appManifestMap);
    boolean isCustomManifestFeatureEnabled =
        featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, context.getAccountId());

    if (valuesInGit) {
      return executeGitTask(context, activityId, appManifestMap);
    } else if (isValuesInCustomSource(appManifestMap) && isCustomManifestFeatureEnabled) {
      return executeCustomManifestFetchTask(context, activityId, appManifestMap);
    } else {
      return executeHelmTask(context, activityId, appManifestMap, helmOverrideManifestMap);
    }
  }

  private boolean isValuesInCustomSource(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    for (Map.Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      ApplicationManifest applicationManifest = entry.getValue();
      if (StoreType.CUSTOM == applicationManifest.getStoreType()) {
        return true;
      }
    }

    return false;
  }

  public String obtainActivityId(ExecutionContext context) {
    return ((HelmDeployStateExecutionData) context.getStateExecutionData()).getActivityId();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    /*
    Nothing to do in case of abort
     */
  }

  protected Activity createActivity(ExecutionContext executionContext, List<CommandUnit> commandUnits) {
    Application app = ((ExecutionContextImpl) executionContext).getApp();
    notNullCheck("Application", app);
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    InstanceElement instanceElement = executionContext.getContextElement(ContextElementType.INSTANCE);
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getUuid())
                                          .commandName(HELM_COMMAND_NAME)
                                          .type(Type.Command)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnits(commandUnits)
                                          .status(ExecutionStatus.RUNNING)
                                          .commandUnitType(CommandUnitType.HELM)
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }
    if (instanceElement != null) {
      activityBuilder.serviceTemplateId(instanceElement.getServiceTemplateElement().getUuid())
          .serviceTemplateName(instanceElement.getServiceTemplateElement().getName())
          .serviceId(instanceElement.getServiceTemplateElement().getServiceElement().getUuid())
          .serviceName(instanceElement.getServiceTemplateElement().getServiceElement().getName())
          .serviceInstanceId(instanceElement.getUuid())
          .hostName(instanceElement.getHost().getHostName());
    }

    Activity activity = activityBuilder.build();
    return activityService.save(activity);
  }

  public int getSteadyStateTimeout() {
    return steadyStateTimeout;
  }

  public void setSteadyStateTimeout(int steadyStateTimeout) {
    this.steadyStateTimeout = steadyStateTimeout;
  }

  private String getRepoName(String appName, String serviceName) {
    return KubernetesConvention.normalize(appName) + "-" + KubernetesConvention.normalize(serviceName);
  }

  private void evaluateHelmChartSpecificationExpression(
      ExecutionContext context, HelmChartSpecification helmChartSpec) {
    if (helmChartSpec == null) {
      return;
    }

    if (isNotBlank(helmChartSpec.getChartUrl())) {
      helmChartSpec.setChartUrl(context.renderExpression(helmChartSpec.getChartUrl()));
    }

    if (isNotBlank(helmChartSpec.getChartVersion())) {
      helmChartSpec.setChartVersion(context.renderExpression(helmChartSpec.getChartVersion()));
    }

    if (isNotBlank(helmChartSpec.getChartName())) {
      helmChartSpec.setChartName(context.renderExpression(helmChartSpec.getChartName()));
    }
  }

  private String obtainHelmReleaseNamePrefix(ExecutionContext context) {
    if (getStateType().equals(HELM_DEPLOY.name())) {
      if (isBlank(getHelmReleaseNamePrefix())) {
        throw new InvalidRequestException("Helm release name cannot be empty");
      }
      return KubernetesConvention.normalize(context.renderExpression(getHelmReleaseNamePrefix()));
    } else {
      HelmDeployContextElement contextElement = context.getContextElement(ContextElementType.HELM_DEPLOY);
      if (contextElement == null || isBlank(contextElement.getReleaseName())) {
        throw new InvalidRequestException("Helm rollback is not possible without deployment", USER);
      }
      return contextElement.getReleaseName();
    }
  }

  private String obtainCommandFlags(ExecutionContext context) {
    if (getStateType().equals(HELM_DEPLOY.name())) {
      return getCommandFlags();
    } else {
      HelmDeployContextElement contextElement = context.getContextElement(ContextElementType.HELM_DEPLOY);
      if (contextElement == null) {
        return null;
      }

      return contextElement.getCommandFlags();
    }
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isBlank(getHelmReleaseNamePrefix())) {
      invalidFields.put("Helm release name prefix", "Helm release name prefix must not be blank");
    }

    if (gitFileConfig != null && isNotBlank(gitFileConfig.getConnectorId())) {
      if (isBlank(gitFileConfig.getBranch()) && isBlank(gitFileConfig.getCommitId())) {
        invalidFields.put("Branch or commit id", "Branch or commit id must not be blank if git connector is selected");
      }

      String filePath = gitFileConfig.getFilePath();
      boolean isFilePathValid = true;
      if (isBlank(filePath)) {
        invalidFields.put("File path", "File path must not be blank if git connector is selected");
        isFilePathValid = false;
      }

      if (isFilePathValid && isBlank(FilenameUtils.getName(filePath))) {
        invalidFields.put("File path", "File path cannot be directory if git connector is selected");
        isFilePathValid = false;
      }

      String fileExtension = FilenameUtils.getExtension(filePath).trim();
      if (isFilePathValid && (isBlank(fileExtension) || !fileExtension.matches(VALID_YAML_FILE_EXTENSIONS_REGEX))) {
        invalidFields.put("File path", "File path has to be YAML file if git connector is selected");
      }
    }

    return invalidFields;
  }

  private void evaluateGitFileConfig(ExecutionContext context) {
    if (isNotBlank(gitFileConfig.getCommitId())) {
      gitFileConfig.setCommitId(context.renderExpression(gitFileConfig.getCommitId()));
    }

    if (isNotBlank(gitFileConfig.getBranch())) {
      gitFileConfig.setBranch(context.renderExpression(gitFileConfig.getBranch()));
    }

    if (isNotBlank(gitFileConfig.getFilePath())) {
      gitFileConfig.setFilePath(context.renderExpression(gitFileConfig.getFilePath()));
    }
  }

  private List<EncryptedDataDetail> fetchEncryptedDataDetail(ExecutionContext context, GitConfig gitConfig) {
    if (gitConfig == null) {
      return null;
    }

    return secretManager.getEncryptionDetails(gitConfig, context.getAppId(), context.getWorkflowExecutionId());
  }

  private HelmCommandExecutionResponse fetchHelmCommandExecutionResponse(ResponseData notifyResponseData) {
    if (!(notifyResponseData instanceof HelmCommandExecutionResponse)) {
      String msg = "Delegate returned error response. Could not convert delegate response to helm response. ";

      if (notifyResponseData instanceof RemoteMethodReturnValueData) {
        msg += notifyResponseData.toString();
      }
      throw new InvalidRequestException(msg, WingsException.USER);
    }

    return (HelmCommandExecutionResponse) notifyResponseData;
  }

  public void updateHelmReleaseNameInInfraMappingElement(ExecutionContext context, String helmReleaseName) {
    if (getStateType().equals(HELM_DEPLOY.name())) {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      if (workflowStandardParams == null) {
        return;
      }

      InfraMappingElement infraMappingElement = context.fetchInfraMappingElement();
      if (infraMappingElement != null && infraMappingElement.getHelm() != null) {
        infraMappingElement.getHelm().setReleaseName(helmReleaseName);
      }
    }
  }

  @VisibleForTesting
  ExecutionResponse executeHelmTask(ExecutionContext context, String activityId,
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap,
      Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap) throws InterruptedException {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    final Application app = appService.get(context.getAppId());
    final Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceElement.getUuid());
    String artifactStreamId = artifact == null ? null : artifact.getArtifactStreamId();

    ContainerInfrastructureMapping containerInfraMapping =
        (ContainerInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    String releaseName = obtainHelmReleaseNamePrefix(context);

    HelmVersion helmVersion = getHelmVersionWithDefault(app, serviceElement);
    updateHelmReleaseNameInInfraMappingElement(context, releaseName);

    String cmdFlags = obtainCommandFlags(context);

    ContainerServiceParams containerServiceParams =
        containerDeploymentHelper.getContainerServiceParams(containerInfraMapping, releaseName, context);

    if (isRollBackNotNeeded(context)) {
      return initialRollbackNotNeeded(context, activityId,
          HelmDeployStateExecutionData.builder()
              .activityId(activityId)
              .releaseName(releaseName)
              .namespace(containerServiceParams.getNamespace())
              .commandFlags(cmdFlags)
              .currentTaskType(HELM_COMMAND_TASK)
              .build());
    }

    HelmChartSpecification helmChartSpecification =
        serviceResourceService.getHelmChartSpecification(context.getAppId(), serviceElement.getUuid());

    K8sDelegateManifestConfig manifestConfig = null;
    ApplicationManifest appManifest = applicationManifestService.getAppManifest(
        app.getUuid(), null, serviceElement.getUuid(), AppManifestKind.K8S_MANIFEST);

    HelmCommandFlag helmCommandFlag = null;
    if (appManifest != null) {
      helmCommandFlag = ApplicationManifestUtils.getHelmCommandFlags(appManifest.getHelmCommandFlag());
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, context.getAccountId())) {
        // replacing app manifest in case of artifact is from manifest
        Service service = serviceResourceService.get(context.getAppId(), serviceElement.getUuid());
        if (Boolean.TRUE.equals(service.getArtifactFromManifest())) {
          appManifest =
              applicationManifestUtils.getAppManifestFromFromExecutionContextHelmChart(context, service.getUuid());
        }
      }

      switch (appManifest.getStoreType()) {
        case HelmSourceRepo:
          GitFileConfig sourceRepoGitFileConfig =
              gitFileConfigHelperService.renderGitFileConfig(context, appManifest.getGitFileConfig());
          GitConfig sourceRepoGitConfig =
              settingsService.fetchGitConfigFromConnectorId(sourceRepoGitFileConfig.getConnectorId());
          gitConfigHelperService.renderGitConfig(context, sourceRepoGitConfig);
          if (null != sourceRepoGitConfig) {
            gitConfigHelperService.convertToRepoGitConfig(sourceRepoGitConfig, sourceRepoGitFileConfig.getRepoName());
          }
          manifestConfig = K8sDelegateManifestConfig.builder()
                               .gitFileConfig(sourceRepoGitFileConfig)
                               .gitConfig(sourceRepoGitConfig)
                               .encryptedDataDetails(fetchEncryptedDataDetail(context, sourceRepoGitConfig))
                               .manifestStoreTypes(StoreType.HelmSourceRepo)
                               .helmCommandFlag(helmCommandFlag)
                               .skipApplyHelmDefaultValues(featureFlagService.isEnabled(
                                   CDP_SKIP_DEFAULT_VALUES_YAML_CG, context.getAccountId()))
                               .build();

          break;
        case HelmChartRepo:
          if (helmOverrideManifestMap.containsKey(K8sValuesLocation.EnvironmentGlobal)) {
            applicationManifestUtils.applyK8sValuesLocationBasedHelmChartOverride(
                appManifest, helmOverrideManifestMap, K8sValuesLocation.EnvironmentGlobal);
          }
          if (appManifest.getHelmChartConfig() == null) {
            helmChartSpecification = null;
          } else if (isBlank(appManifest.getHelmChartConfig().getConnectorId())) {
            if (helmChartSpecification == null) {
              helmChartSpecification = HelmChartSpecification.builder().build();
            }
            helmChartSpecification.setChartVersion(appManifest.getHelmChartConfig().getChartVersion());
            helmChartSpecification.setChartUrl(appManifest.getHelmChartConfig().getChartUrl());
            helmChartSpecification.setChartName(appManifest.getHelmChartConfig().getChartName());
          } else {
            HelmChartConfigParams helmChartConfigTaskParams =
                helmChartConfigHelperService.getHelmChartConfigTaskParams(context, appManifest);
            helmChartConfigTaskParams.setUseLatestChartMuseumVersion(
                featureFlagService.isEnabled(USE_LATEST_CHARTMUSEUM_VERSION, context.getAccountId()));

            if (HelmVersion.isHelmV3(helmVersion)) {
              helmChartConfigTaskParams.setUseRepoFlags(true);
              helmChartConfigTaskParams.setDeleteRepoCacheDir(true);
            }

            helmChartConfigTaskParams.setUseCache(helmVersion != HelmVersion.V2
                && !featureFlagService.isEnabled(DISABLE_HELM_REPO_YAML_CACHE, context.getAccountId()));

            helmChartConfigTaskParams.setCheckIncorrectChartVersion(true);

            manifestConfig = K8sDelegateManifestConfig.builder()
                                 .helmChartConfigParams(helmChartConfigTaskParams)
                                 .manifestStoreTypes(HelmChartRepo)
                                 .helmCommandFlag(helmCommandFlag)
                                 .skipApplyHelmDefaultValues(featureFlagService.isEnabled(
                                     CDP_SKIP_DEFAULT_VALUES_YAML_CG, context.getAccountId()))
                                 .build();
          }
          break;

        case CUSTOM: {
          if (featureFlagService.isEnabled(CUSTOM_MANIFEST, context.getAccountId())) {
            HelmDeployStateExecutionData helmDeployStateExecutionData = context.getStateExecutionData();
            CustomManifestSource customManifestSource =
                CustomManifestSource.builder()
                    .filePaths(Arrays.asList(appManifest.getCustomSourceConfig().getPath()))
                    .script(appManifest.getCustomSourceConfig().getScript())
                    .zippedManifestFileId(helmDeployStateExecutionData == null
                            ? null
                            : helmDeployStateExecutionData.getZippedManifestFileId())
                    .build();
            manifestConfig = K8sDelegateManifestConfig.builder()
                                 .customManifestEnabled(true)
                                 .customManifestSource(customManifestSource)
                                 .manifestStoreTypes(CUSTOM)
                                 .build();
          }
          break;
        }

        default:
          throw new InvalidRequestException("Unsupported store type: " + appManifest.getStoreType(), USER);
      }
    }

    if (StateType.HELM_DEPLOY.name().equals(getStateType())) {
      if ((gitFileConfig == null || gitFileConfig.getConnectorId() == null) && manifestConfig == null) {
        validateChartSpecification(helmChartSpecification);
      }
    }

    evaluateHelmChartSpecificationExpression(context, helmChartSpecification);

    HelmDeployStateExecutionDataBuilder stateExecutionDataBuilder =
        HelmDeployStateExecutionData.builder()
            .activityId(activityId)
            .releaseName(releaseName)
            .namespace(containerServiceParams.getNamespace())
            .commandFlags(cmdFlags)
            .currentTaskType(HELM_COMMAND_TASK);

    setHelmExecutionSummary(context, releaseName, helmChartSpecification, manifestConfig);

    if (helmChartSpecification != null) {
      stateExecutionDataBuilder.chartName(helmChartSpecification.getChartName());
      stateExecutionDataBuilder.chartRepositoryUrl(helmChartSpecification.getChartUrl());
      stateExecutionDataBuilder.chartVersion(helmChartSpecification.getChartVersion());
    }

    ImageDetails imageDetails = null;
    if (artifact != null) {
      imageDetails = getImageDetails(context, artifact);
    }

    String repoName = getRepoName(app.getName(), serviceElement.getName());

    List<EncryptedDataDetail> encryptedDataDetails = null;
    GitConfig gitConfig = null;
    if (gitFileConfig != null) {
      evaluateGitFileConfig(context);
      List<TemplateExpression> templateExpressions = getTemplateExpressions();
      if (isNotEmpty(templateExpressions)) {
        TemplateExpression configIdExpression =
            templateExpressionProcessor.getTemplateExpression(templateExpressions, "connectorId");
        SettingAttribute settingAttribute = templateExpressionProcessor.resolveSettingAttributeByNameOrId(
            context, configIdExpression, SettingVariableTypes.GIT);
        SettingValue settingValue = settingAttribute.getValue();
        if (!(settingValue instanceof GitConfig)) {
          throw new InvalidRequestException("Git connector not found", USER);
        }
        gitConfig = (GitConfig) settingValue;
        gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
      } else {
        gitConfig = settingsService.fetchGitConfigFromConnectorId(gitFileConfig.getConnectorId());
      }
      if (null != gitConfig) {
        gitConfigHelperService.convertToRepoGitConfig(gitConfig, gitFileConfig.getRepoName());
      }
      encryptedDataDetails = fetchEncryptedDataDetail(context, gitConfig);
    }

    final int expressionFunctorToken = HashGenerator.generateIntegerHash();
    setNewAndPrevReleaseVersion(context, app, releaseName, containerServiceParams, stateExecutionDataBuilder, gitConfig,
        encryptedDataDetails, cmdFlags, helmVersion, expressionFunctorToken, helmCommandFlag, activityId);
    HelmCommandRequest commandRequest = getHelmCommandRequest(context, helmChartSpecification, containerServiceParams,
        releaseName, app.getAccountId(), app.getUuid(), activityId, imageDetails, repoName, gitConfig,
        encryptedDataDetails, cmdFlags, manifestConfig, appManifestMap, helmVersion, helmCommandFlag);

    commandRequest.setK8SteadyStateCheckEnabled(
        featureFlagService.isEnabled(FeatureName.HELM_STEADY_STATE_CHECK_1_16, context.getAccountId()));

    StateExecutionContext stateExecutionContext =
        buildStateExecutionContext(stateExecutionDataBuilder, expressionFunctorToken);
    HelmDeployStateExecutionData stateExecutionData = stateExecutionDataBuilder.build();
    setHelmCommandRequestReleaseVersion(commandRequest, stateExecutionData);

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .description("Helm Command Execution")
            .waitId(activityId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfraMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD,
                serviceTemplateHelper.fetchServiceTemplateId(containerInfraMapping))
            .setupAbstraction(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, artifactStreamId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(HELM_COMMAND_TASK.name())
                      .parameters(new Object[] {commandRequest})
                      .timeout(fetchSafeTimeoutInMillis(getTimeoutMillis()))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    renderDelegateTask(context, delegateTask, stateExecutionContext);
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    stateExecutionData.setCommandFlags(
        expressionEvaluator.substitute(commandRequest.getCommandFlags(), Collections.emptyMap()));

    appendDelegateTaskDetails(context, delegateTask);
    delegateService.queueTaskV2(delegateTask);

    return ExecutionResponse.builder()
        .correlationIds(singletonList(activityId))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  private StateExecutionContext buildStateExecutionContext(
      HelmDeployStateExecutionDataBuilder stateExecutionDataBuilder, final int expressionFunctorToken) {
    StateExecutionContextBuilder stateExecutionContextBuilder =
        StateExecutionContext.builder().adoptDelegateDecryption(true).expressionFunctorToken(expressionFunctorToken);
    if (stateExecutionDataBuilder != null) {
      stateExecutionContextBuilder.stateExecutionData(stateExecutionDataBuilder.build());
    }
    return stateExecutionContextBuilder.build();
  }

  @VisibleForTesting
  boolean isRollBackNotNeeded(ExecutionContext context) {
    return StateType.HELM_ROLLBACK.name().equalsIgnoreCase(getStateType())
        && ((HelmDeployContextElement) context.getContextElement(ContextElementType.HELM_DEPLOY))
               .getPreviousReleaseRevision()
        == 0;
  }

  @VisibleForTesting
  ExecutionResponse initialRollbackNotNeeded(
      ExecutionContext context, String activityId, HelmDeployStateExecutionData stateExecutionData) {
    Log.Builder logBuilder = aLog()
                                 .appId(context.getAppId())
                                 .activityId(activityId)
                                 .commandUnitName(HelmDummyCommandUnitConstants.Rollback)
                                 .logLevel(LogLevel.INFO)
                                 .executionResult(CommandExecutionStatus.SUCCESS);
    ManagerExecutionLogCallback executionLogCallback =
        new ManagerExecutionLogCallback(logService, logBuilder, activityId);

    executionLogCallback.saveExecutionLog(NO_PREV_DEPLOYMENT, LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    Misc.logAllMessages(null, executionLogCallback, CommandExecutionStatus.SUCCESS);

    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private HelmVersion getHelmVersionWithDefault(Application app, ServiceElement serviceElement) {
    return serviceResourceService.getHelmVersionWithDefault(app.getUuid(), serviceElement.getUuid());
  }

  private ExecutionResponse executeGitTask(
      ExecutionContext context, String activityId, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    Application app = appService.get(context.getAppId());
    applicationManifestUtils.populateRemoteGitConfigFilePathList(context, appManifestMap);

    GitFetchFilesTaskParams fetchFilesTaskParams =
        applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap);
    fetchFilesTaskParams.setOptimizedFilesFetch(
        featureFlagService.isEnabled(OPTIMIZED_GIT_FETCH_FILES, context.getAccountId()));
    fetchFilesTaskParams.setActivityId(activityId);
    fetchFilesTaskParams.setFinalState(true);
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.VALUES);
    applicationManifestUtils.setValuesPathInGitFetchFilesTaskParams(fetchFilesTaskParams);

    final int expressionFunctorToken = HashGenerator.generateIntegerHash();

    HelmDeployStateExecutionDataBuilder helmDeployStateExecutionDataBuilder = HelmDeployStateExecutionData.builder()
                                                                                  .activityId(activityId)
                                                                                  .commandName(HELM_COMMAND_NAME)
                                                                                  .currentTaskType(TaskType.GIT_COMMAND)
                                                                                  .appManifestMap(appManifestMap);

    StateExecutionContext stateExecutionContext =
        buildStateExecutionContext(helmDeployStateExecutionDataBuilder, expressionFunctorToken);

    ContainerInfrastructureMapping containerInfraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

    String waitId = generateUuid();
    Environment env = k8sStateHelper.fetchEnvFromExecutionContext(context);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .description("Fetch Files")
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfraMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.GIT_FETCH_FILES_TASK.name())
                      .parameters(new Object[] {fetchFilesTaskParams})
                      .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    renderDelegateTask(context, delegateTask, stateExecutionContext);

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);

    Map<K8sValuesLocation, Collection<String>> valuesFiles = new EnumMap<>(K8sValuesLocation.class);
    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    if (stateExecutionData != null) {
      valuesFiles.putAll(stateExecutionData.getValuesFiles());
    }
    helmDeployStateExecutionDataBuilder.valuesFiles(valuesFiles);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(helmDeployStateExecutionDataBuilder.build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  protected ExecutionResponse handleAsyncResponseForHelmTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    HelmCommandExecutionResponse executionResponse =
        fetchHelmCommandExecutionResponse(response.values().iterator().next());
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder()
                                                            .executionStatus(executionStatus)
                                                            .errorMessage(executionResponse.getErrorMessage())
                                                            .stateExecutionData(stateExecutionData);

    HelmCommandResponse helmCommandResponse = executionResponse.getHelmCommandResponse();
    if (helmCommandResponse == null) {
      log.info("Helm command task failed with status " + executionResponse.getCommandExecutionStatus().toString()
          + " with error message " + executionResponse.getErrorMessage());

      return executionResponseBuilder.build();
    }

    updateHelmExecutionSummary(context, helmCommandResponse);

    if (CommandExecutionStatus.SUCCESS == helmCommandResponse.getCommandExecutionStatus()) {
      HelmInstallCommandResponse helmInstallCommandResponse = (HelmInstallCommandResponse) helmCommandResponse;

      List<InstanceStatusSummary> instanceStatusSummaries = containerDeploymentHelper.getInstanceStatusSummaries(
          context, helmInstallCommandResponse.getContainerInfoList());
      stateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);

      List<InstanceElement> instanceElements =
          instanceStatusSummaries.stream().map(InstanceStatusSummary::getInstanceElement).collect(toList());
      InstanceElementListParam instanceElementListParam =
          InstanceElementListParam.builder().instanceElements(instanceElements).build();

      List<InstanceDetails> instanceDetails =
          ContainerHelper.generateInstanceDetails(helmInstallCommandResponse.getContainerInfoList());
      saveInstanceInfoToSweepingOutput(context, instanceElements, instanceDetails);
      saveHelmReleaseInfoToSweepingOutput(
          context, HelmReleaseInfoElement.builder().releaseName(stateExecutionData.getReleaseName()).build());

      executionResponseBuilder.contextElement(instanceElementListParam);
      executionResponseBuilder.notifyElement(instanceElementListParam);

    } else {
      log.info("Got helm execution response with status "
          + executionResponse.getHelmCommandResponse().getCommandExecutionStatus().toString() + " with output "
          + executionResponse.getHelmCommandResponse().getOutput());
    }

    return executionResponseBuilder.build();
  }

  @VisibleForTesting
  void saveInstanceInfoToSweepingOutput(
      ExecutionContext context, List<InstanceElement> instanceElements, List<InstanceDetails> instanceDetails) {
    boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                   .value(InstanceInfoVariables.builder()
                                              .instanceElements(instanceElements)
                                              .instanceDetails(instanceDetails)
                                              .skipVerification(skipVerification)
                                              .build())
                                   .build());
  }

  @VisibleForTesting
  void saveHelmReleaseInfoToSweepingOutput(ExecutionContext context, HelmReleaseInfoElement helmReleaseInfoElement) {
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.PHASE)
                                   .name(HelmReleaseInfoElement.SWEEPING_OUTPUT_NAME)
                                   .value(helmReleaseInfoElement)
                                   .build());
  }

  private ExecutionResponse handleAsyncResponseForGitFetchFilesTask(
      ExecutionContext context, Map<String, ResponseData> response) throws InterruptedException {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = obtainActivityId(context);

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getGitCommandStatus() == GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = helmDeployStateExecutionData.getAppManifestMap();
    Map<K8sValuesLocation, Collection<String>> valuesFiles =
        applicationManifestUtils.getValuesFilesFromGitFetchFilesResponse(appManifestMap, executionResponse);

    helmDeployStateExecutionData.getValuesFiles().putAll(valuesFiles);
    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap =
        applicationManifestUtils.getOverrideApplicationManifests(context, HELM_CHART_OVERRIDE);
    boolean isCustomManifestFeatureEnabled =
        featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, context.getAccountId());

    if (isValuesInCustomSource(appManifestMap) && isCustomManifestFeatureEnabled) {
      return executeCustomManifestFetchTask(context, activityId, appManifestMap);
    } else {
      return executeHelmTask(
          context, activityId, helmDeployStateExecutionData.getAppManifestMap(), helmOverrideManifestMap);
    }
  }

  List<String> getValuesYamlOverrides(ExecutionContext context, ContainerServiceParams containerServiceParams,
      ImageDetails imageDetails, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    Map<K8sValuesLocation, Collection<String>> valuesFiles = new EnumMap<>(K8sValuesLocation.class);

    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();
    if (helmDeployStateExecutionData != null) {
      valuesFiles.putAll(helmDeployStateExecutionData.getValuesFiles());
    }

    applicationManifestUtils.populateValuesFilesFromAppManifest(appManifestMap, valuesFiles);

    log.info("Found Values at following sources: " + valuesFiles.keySet());
    List<String> helmValueOverridesYamlFiles = getOrderedValuesYamlList(valuesFiles);

    List<String> helmValueOverridesYamlFilesEvaluated = new ArrayList<>();
    if (isNotEmpty(helmValueOverridesYamlFiles)) {
      helmValueOverridesYamlFilesEvaluated =
          helmValueOverridesYamlFiles.stream()
              .filter(StringUtils::isNotBlank)
              .map(yamlFileContent -> {
                if (imageDetails != null) {
                  if (isNotBlank(imageDetails.getTag())) {
                    yamlFileContent =
                        yamlFileContent.replaceAll(DOCKER_IMAGE_TAG_PLACEHOLDER_REGEX, imageDetails.getTag());
                  }

                  if (isNotBlank(imageDetails.getName())) {
                    yamlFileContent = yamlFileContent.replaceAll(DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX,
                        getImageName(yamlFileContent, imageDetails.getName(), imageDetails.getDomainName()));
                  }
                }
                yamlFileContent =
                    yamlFileContent.replaceAll(HELM_NAMESPACE_PLACEHOLDER_REGEX, containerServiceParams.getNamespace());
                return yamlFileContent;
              })
              .collect(Collectors.toList());
    }

    return helmValueOverridesYamlFilesEvaluated;
  }

  List<String> getOrderedValuesYamlList(Map<K8sValuesLocation, Collection<String>> valuesFiles) {
    List<String> valuesList = new ArrayList<>();

    if (valuesFiles.containsKey(K8sValuesLocation.Service)) {
      valuesList.addAll(valuesFiles.get(K8sValuesLocation.Service));
    }

    if (valuesFiles.containsKey(K8sValuesLocation.ServiceOverride)) {
      valuesList.addAll(valuesFiles.get(K8sValuesLocation.ServiceOverride));
    }

    if (valuesFiles.containsKey(K8sValuesLocation.EnvironmentGlobal)) {
      valuesList.addAll(valuesFiles.get(K8sValuesLocation.EnvironmentGlobal));
    }

    if (valuesFiles.containsKey(K8sValuesLocation.Environment)) {
      valuesList.addAll(valuesFiles.get(K8sValuesLocation.Environment));
    }

    return valuesList;
  }

  public ExecutionResponse executeHelmValuesFetchTask(ExecutionContext context, String activityId,
      Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap) {
    Application app = appService.get(context.getAppId());
    HelmValuesFetchTaskParameters helmValuesFetchTaskParameters = getHelmValuesFetchTaskParameters(
        context, app.getUuid(), activityId, helmOverrideManifestMap, applicationManifestMap);

    String waitId = generateUuid();
    final int expressionFunctorToken = HashGenerator.generateIntegerHash();

    HelmDeployStateExecutionDataBuilder helmDeployStateExecutionDataBuilder =
        HelmDeployStateExecutionData.builder()
            .activityId(activityId)
            .commandName(HELM_COMMAND_NAME)
            .currentTaskType(TaskType.HELM_VALUES_FETCH);

    StateExecutionContext stateExecutionContext =
        buildStateExecutionContext(helmDeployStateExecutionDataBuilder, expressionFunctorToken);

    ContainerInfrastructureMapping containerInfraMapping =
        (ContainerInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    Environment env = k8sStateHelper.fetchEnvFromExecutionContext(context);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .description("Fetch Helm Values")
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD,
                k8sStateHelper.fetchContainerInfrastructureMappingId(context))
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD,
                serviceTemplateHelper.fetchServiceTemplateId(containerInfraMapping))
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HELM_VALUES_FETCH.name())
                      .parameters(new Object[] {helmValuesFetchTaskParameters})
                      .expressionFunctorToken(expressionFunctorToken)
                      .timeout(TimeUnit.MINUTES.toMillis(10))
                      .build())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    renderDelegateTask(context, delegateTask, stateExecutionContext);
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    helmDeployStateExecutionDataBuilder.commandFlags(
        expressionEvaluator.substitute(helmValuesFetchTaskParameters.getHelmCommandFlags(), Collections.emptyMap()));

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(helmDeployStateExecutionDataBuilder.build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private HelmValuesFetchTaskParameters getHelmValuesFetchTaskParameters(ExecutionContext context, String appId,
      String activityId, Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap) {
    ContainerInfrastructureMapping containerInfraMapping =
        (ContainerInfrastructureMapping) infrastructureMappingService.get(appId, context.fetchInfraMappingId());

    String releaseName = obtainHelmReleaseNamePrefix(context);
    ContainerServiceParams containerServiceParams =
        containerDeploymentHelper.getContainerServiceParams(containerInfraMapping, releaseName, context);

    String evaluatedCommandFlags = obtainCommandFlags(context);

    boolean isBindTaskFeatureSet = false;
    if (featureFlagService.isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, context.getAccountId())) {
      isBindTaskFeatureSet = true;
    }

    HelmValuesFetchTaskParameters helmValuesFetchTaskParameters =
        HelmValuesFetchTaskParameters.builder()
            .accountId(context.getAccountId())
            .appId(context.getAppId())
            .activityId(activityId)
            .helmCommandFlags(evaluatedCommandFlags)
            .containerServiceParams(containerServiceParams)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .isBindTaskFeatureSet(isBindTaskFeatureSet)
            .timeoutInMillis(fetchSafeTimeoutInMillis(getTimeoutMillis()))
            .useLatestChartMuseumVersion(
                featureFlagService.isEnabled(USE_LATEST_CHARTMUSEUM_VERSION, context.getAccountId()))
            .build();

    ApplicationManifest applicationManifest = applicationManifestUtils.getApplicationManifestForService(context);
    if (applicationManifest == null || HelmChartRepo != applicationManifest.getStoreType()) {
      return helmValuesFetchTaskParameters;
    }

    helmValuesFetchTaskParameters.setHelmCommandFlag(
        ApplicationManifestUtils.getHelmCommandFlags(applicationManifest.getHelmCommandFlag()));

    if (helmOverrideManifestMap.containsKey(K8sValuesLocation.EnvironmentGlobal)) {
      applicationManifestUtils.applyK8sValuesLocationBasedHelmChartOverride(
          applicationManifest, helmOverrideManifestMap, K8sValuesLocation.EnvironmentGlobal);
    }

    HelmChartConfigParams helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(context, applicationManifest);
    if (helmChartConfigTaskParams != null) {
      helmValuesFetchTaskParameters.setHelmChartConfigTaskParams(helmChartConfigTaskParams);
      SettingValue connectorConfig = helmChartConfigTaskParams.getConnectorConfig();
      if (connectorConfig != null) {
        if (connectorConfig instanceof AwsConfig) {
          AwsConfig awsConfig = (AwsConfig) connectorConfig;
          if (isNotEmpty(awsConfig.getTag())) {
            helmValuesFetchTaskParameters.setDelegateSelectors(singleton(awsConfig.getTag()));
          }
        } else if (connectorConfig instanceof GcpConfig) {
          GcpConfig gcpConfig = (GcpConfig) connectorConfig;
          if (isNotEmpty(gcpConfig.getDelegateSelectors())) {
            helmValuesFetchTaskParameters.setDelegateSelectors(new HashSet<>(gcpConfig.getDelegateSelectors()));
          }
        }
      }
    }

    if (featureFlagService.isEnabled(OVERRIDE_VALUES_YAML_FROM_HELM_CHART, context.getAccountId())) {
      Map<String, List<String>> mapK8sValuesLocationToFilePaths =
          applicationManifestUtils.getHelmFetchTaskMapK8sValuesLocationToFilePaths(context, applicationManifestMap);
      helmValuesFetchTaskParameters.setMapK8sValuesLocationToFilePaths(mapK8sValuesLocationToFilePaths);
    }

    return helmValuesFetchTaskParameters;
  }

  private void setHelmExecutionSummary(ExecutionContext context, String releaseName,
      HelmChartSpecification helmChartSpec, K8sDelegateManifestConfig repoConfig) {
    try {
      if (!HELM_DEPLOY.name().equals(this.getStateType())) {
        return;
      }

      HelmExecutionSummary summary = helmHelper.prepareHelmExecutionSummary(
          releaseName, HelmChartSpecificationMapper.helmChartSpecificationDTO(helmChartSpec), repoConfig);
      workflowExecutionService.refreshHelmExecutionSummary(context.getWorkflowExecutionId(), summary);
    } catch (Exception ex) {
      log.info("Exception while setting helm execution summary", ex);
    }
  }

  @VisibleForTesting
  void updateHelmExecutionSummary(ExecutionContext context, HelmCommandResponse helmCommandResponse) {
    try {
      if (helmCommandResponse instanceof HelmInstallCommandResponse) {
        HelmInstallCommandResponse helmInstallCommandResponse = (HelmInstallCommandResponse) helmCommandResponse;
        WorkflowExecution workflowExecution =
            workflowExecutionService.getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
        if (workflowExecution == null) {
          return;
        }
        HelmExecutionSummary summary = workflowExecution.getHelmExecutionSummary();
        if (HELM_DEPLOY.name().equals(getStateType())) {
          HelmChartInfo helmChartInfo = helmInstallCommandResponse.getHelmChartInfo();
          if (helmChartInfo == null) {
            return;
          }

          if (summary.getHelmChartInfo() == null) {
            summary.setHelmChartInfo(HelmChartInfo.builder().build());
          }

          if (isNotBlank(helmChartInfo.getName())) {
            summary.getHelmChartInfo().setName(helmChartInfo.getName());
          }

          if (isNotBlank(helmChartInfo.getVersion())) {
            summary.getHelmChartInfo().setVersion(helmChartInfo.getVersion());
          }

          if (isNotBlank(helmChartInfo.getRepoUrl())) {
            summary.getHelmChartInfo().setRepoUrl(helmChartInfo.getRepoUrl());
          }
        }

        if (isNotEmpty(helmInstallCommandResponse.getContainerInfoList())) {
          summary.setContainerInfoList(helmInstallCommandResponse.getContainerInfoList());
        }

        workflowExecutionService.refreshHelmExecutionSummary(context.getWorkflowExecutionId(), summary);
      }
    } catch (Exception ex) {
      log.info("Exception while updating helm execution summary", ex);
    }
  }
}