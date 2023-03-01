/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CF_ALLOW_SPECIAL_CHARACTERS;
import static io.harness.beans.FeatureName.CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK;
import static io.harness.beans.FeatureName.CF_CUSTOM_EXTRACTION;
import static io.harness.beans.FeatureName.LIMIT_PCF_THREADS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.Misc.normalizeExpression;
import static io.harness.pcf.CfCommandUnitConstants.CheckExistingApps;
import static io.harness.pcf.CfCommandUnitConstants.FetchCustomFiles;
import static io.harness.pcf.CfCommandUnitConstants.FetchGitFiles;
import static io.harness.pcf.CfCommandUnitConstants.PcfSetup;
import static io.harness.pcf.CfCommandUnitConstants.VerifyManifests;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.model.PcfConstants.DEFAULT_PCF_TASK_TIMEOUT_MIN;
import static io.harness.pcf.model.PcfConstants.INFRA_ROUTE;
import static io.harness.pcf.model.PcfConstants.PCF_INFRA_ROUTE;
import static io.harness.pcf.model.PcfConstants.VARS_YML;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.CUSTOM_MANIFEST_FETCH_TASK;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.beans.TaskType.PCF_COMMAND_TASK;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfSetupCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.model.PcfConstants;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.pcf.PcfSetupStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.api.pcf.SetupSweepingOutputPcf.SetupSweepingOutputPcfBuilder;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.service.mappers.artifact.CfConfigToInternalMapper;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;
import software.wings.utils.ApplicationManifestUtils;
import software.wings.utils.ServiceVersionConvention;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class PcfSetupState extends State {
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient SettingsService settingsService;
  @Inject private transient ActivityService activityService;
  @Inject private transient ArtifactStreamService artifactStreamService;
  @Inject private transient PcfStateHelper pcfStateHelper;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public static final String PCF_SETUP_COMMAND = "PCF Setup";
  public static final String URL = "url";
  private static final String ARTIFACT_STRING = "artifact/";
  private static final String JENKINS = "JENKINS";
  private static final String BAMBOO = "BAMBOO";
  private static final String ARTIFACTORY = "ARTIFACTORY";
  private static final String NEXUS = "NEXUS";
  private static final String S3 = "AMAZON_S3";

  @Getter
  @Setter
  @DefaultValue("${app.name}__${service.name}__${env.name}")
  @Attributes(title = "PCF App Name")
  private String pcfAppName;

  @Getter @Setter private boolean useCurrentRunningCount;
  @Getter @Setter private Integer currentRunningCount;
  @Getter @Setter @Attributes(title = "Total Number of Instances", required = true) private Integer maxInstances;

  @Getter @Setter @Attributes(title = "Resize Strategy", required = true) private ResizeStrategy resizeStrategy;

  @Getter @Setter @Attributes(title = "Map Route") private String route;

  @Getter
  @Setter
  @Attributes(title = "API Timeout Interval (Minutes)")
  @DefaultValue("5")
  private Integer timeoutIntervalInMinutes = 5;

  @Getter
  @Setter
  @Attributes(title = "Active Versions to Keep")
  @DefaultValue("3")
  private Integer olderActiveVersionCountToKeep;

  @Getter @Setter private boolean blueGreen;
  @Getter @Setter private boolean isWorkflowV2;
  @Getter @Setter private String[] tempRouteMap;
  @Getter @Setter private String[] finalRouteMap;
  @Getter @Setter private boolean useAppAutoscalar;
  @Getter @Setter private boolean enforceSslValidation;
  @Getter @Setter private boolean useArtifactProcessingScript;
  @Getter @Setter private String artifactProcessingScript;
  @Getter @Setter private List<String> tags;
  @Getter @Setter private boolean isNonVersioning;

  public PcfSetupState(String name) {
    super(name, StateType.PCF_SETUP.name());
  }

  public PcfSetupState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public Integer getTimeoutMillis() {
    if (timeoutIntervalInMinutes == null) {
      return Ints.checkedCast(TimeUnit.MINUTES.toMillis(5));
    }
    return Ints.checkedCast(TimeUnit.MINUTES.toMillis(timeoutIntervalInMinutes));
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.PCF_OVERRIDE);
    boolean valuesInGit = pcfStateHelper.isManifestInGit(appManifestMap);
    boolean valuesInCustomSource = pcfStateHelper.isValuesInCustomSource(appManifestMap);

    Activity activity = createActivity(context, valuesInGit, valuesInCustomSource);

    if (valuesInGit) {
      return executeGitTask(context, appManifestMap, activity.getUuid());
    } else if (valuesInCustomSource) {
      return executeCustomFetchValuesTask(context, appManifestMap, activity.getUuid());
    } else {
      return executePcfTask(context, activity.getUuid(), appManifestMap);
    }
  }

  protected ExecutionResponse executePcfTask(
      ExecutionContext context, String activityId, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = appService.get(context.getAppId());
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    notNullCheck("Env can not be null", env);
    ServiceElement serviceElement = phaseElement.getServiceElement();

    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceElement.getUuid());
    notNullCheck("Artifact Can not be null", artifact);

    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    notNullCheck(new StringBuilder(128)
                     .append("Unable to find artifact stream for service ")
                     .append(serviceElement.getName())
                     .append(" and Artifact: ")
                     .append(artifact.getUuid())
                     .toString(),
        artifactStream);

    // If git fetch has happened, we need to set workflow state variables from stateExecution data.
    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    restoreStateDataAfterGitFetchIfNeeded(pcfSetupStateExecutionData);

    if (olderActiveVersionCountToKeep == null) {
      olderActiveVersionCountToKeep = 3;
    }

    if (olderActiveVersionCountToKeep <= 0) {
      throw new InvalidRequestException("Value for Older Active Versions To Keep Must be > 0");
    }

    PcfInfrastructureMapping pcfInfrastructureMapping =
        (PcfInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    SettingAttribute settingAttribute = settingsService.get(pcfInfrastructureMapping.getComputeProviderSettingId());
    CfInternalConfig pcfConfig = CfConfigToInternalMapper.toCfInternalConfig((PcfConfig) settingAttribute.getValue());
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(pcfConfig, context.getAppId(), context.getWorkflowExecutionId());

    PcfManifestsPackage pcfManifestsPackage = pcfStateHelper.generateManifestMap(
        context, appManifestMap, serviceElement, activityId, pcfConfig.getAccountId());

    String applicationManifestYmlContent = pcfManifestsPackage.getManifestYml();
    String pcfAppNameSuffix = generateAppNamePrefix(context, app, serviceElement, env, pcfManifestsPackage, pcfConfig);
    boolean isOriginalRoute = shouldUseOriginalRoute();
    List<String> tempRouteMaps = fetchTempRoutes(context, pcfInfrastructureMapping);
    List<String> routeMaps = fetchRouteMaps(context, pcfManifestsPackage, pcfInfrastructureMapping);
    Integer maxCount = fetchMaxCount(pcfManifestsPackage);
    boolean isWebProcessCountZero = maxCount == 0;
    Map<String, String> serviceVariables = context.getServiceVariables().entrySet().stream().collect(
        Collectors.toMap(Entry::getKey, e -> e.getValue().toString()));
    if (serviceVariables != null) {
      serviceVariables.replaceAll((name, value) -> context.renderExpression(value));
    }

    boolean useCliForSetup = true;

    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    artifactStreamAttributes.setMetadata(artifact.getMetadata());
    artifactStreamAttributes.setArtifactStreamId(artifactStream.getUuid());
    artifactStreamAttributes.setServerSetting(settingsService.get(artifactStream.getSettingId()).toDTO());
    artifactStreamAttributes.setArtifactServerEncryptedDataDetails(
        secretManager.getEncryptionDetails((EncryptableSetting) artifactStreamAttributes.getServerSetting().getValue(),
            context.getAppId(), context.getWorkflowExecutionId()));
    artifactStreamAttributes.setArtifactName(artifact.getDisplayName());
    artifactStreamAttributes.setMetadataOnly(onlyMetaForArtifactaType(artifactStream));
    artifactStreamAttributes.getMetadata().put(
        ArtifactMetadataKeys.artifactFileName, artifactFileNameForSource(artifact, artifactStreamAttributes));
    artifactStreamAttributes.getMetadata().put(
        ArtifactMetadataKeys.artifactPath, artifactPathForSource(artifact, artifactStreamAttributes));

    boolean nonVersioningInactiveRollbackEnabled =
        featureFlagService.isEnabled(CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK, pcfConfig.getAccountId());
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder()
            .activityId(activityId)
            .appId(app.getUuid())
            .accountId(app.getAccountId())
            .commandName(PCF_SETUP_COMMAND)
            .releaseNamePrefix(pcfAppNameSuffix)
            .organization(context.renderExpression(pcfInfrastructureMapping.getOrganization()))
            .space(context.renderExpression(pcfInfrastructureMapping.getSpace()))
            .pcfConfig(pcfConfig)
            .pcfCommandType(PcfCommandType.SETUP)
            .artifactStreamAttributes(artifactStreamAttributes)
            .manifestYaml(applicationManifestYmlContent)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .artifactFiles(artifact.getArtifactFiles().stream().map(ArtifactFile::toDTO).collect(toList()))
            .routeMaps(isOriginalRoute ? routeMaps : tempRouteMaps)
            .serviceVariables(serviceVariables)
            .timeoutIntervalInMin(timeoutIntervalInMinutes == null ? Integer.valueOf(5) : timeoutIntervalInMinutes)
            .maxCount(maxCount)
            .useCurrentCount(useCurrentRunningCount)
            .currentRunningCount(getCurrentRunningCountForSetupRequest())
            .blueGreen(blueGreen)
            .olderActiveVersionCountToKeep(
                olderActiveVersionCountToKeep == null ? Integer.valueOf(3) : olderActiveVersionCountToKeep)
            .useCLIForPcfAppCreation(useCliForSetup)
            .pcfManifestsPackage(pcfManifestsPackage)
            .useAppAutoscalar(useAppAutoscalar)
            .enforceSslValidation(enforceSslValidation)
            .limitPcfThreads(featureFlagService.isEnabled(LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
            .cfCliVersion(pcfStateHelper.getCfCliVersionOrDefault(app.getAppId(), serviceElement.getUuid()))
            .isNonVersioning(nonVersioningInactiveRollbackEnabled && isNonVersioning)
            .nonVersioningInactiveRollbackEnabled(nonVersioningInactiveRollbackEnabled)
            .build();

    if (featureFlagService.isEnabled(CF_CUSTOM_EXTRACTION, pcfConfig.getAccountId()) && useArtifactProcessingScript
        && isNotEmpty(artifactProcessingScript)) {
      if (cfCommandSetupRequest.getArtifactStreamAttributes().isDockerBasedDeployment()) {
        throw new InvalidRequestException("Docker based deployment shouldn't contain an artifact processing script");
      }
      String rawScript = pcfStateHelper.removeCommentedLineFromScript(artifactProcessingScript);
      cfCommandSetupRequest.setArtifactProcessingScript(context.renderExpression(rawScript));
    }

    PcfSetupStateExecutionData stateExecutionData =
        PcfSetupStateExecutionData.builder()
            .activityId(activityId)
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .envId(env.getUuid())
            .infraMappingId(pcfInfrastructureMapping.getUuid())
            .pcfCommandRequest(cfCommandSetupRequest)
            .commandName(PCF_SETUP_COMMAND)
            .maxInstanceCount(maxCount)
            .useCurrentRunningInstanceCount(useCurrentRunningCount)
            .currentRunningInstanceCount(getCurrentRunningCountForSetupRequest())
            .desireActualFinalCount(useCurrentRunningCount ? getCurrentRunningCountForSetupRequest() : maxCount)
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .serviceId(serviceElement.getUuid())
            .routeMaps(routeMaps)
            .tempRouteMaps(tempRouteMaps)
            .isStandardBlueGreen(blueGreen)
            .useTempRoutes(!isOriginalRoute)
            .taskType(PCF_COMMAND_TASK)
            .useAppAutoscalar(useAppAutoscalar)
            .enforceSslValidation(enforceSslValidation)
            .resizeStrategy(resizeStrategy)
            .pcfManifestsPackage(pcfManifestsPackage)
            .useArtifactProcessingScript(useArtifactProcessingScript)
            .artifactProcessingScript(artifactProcessingScript)
            .tags(tags)
            .cfAppNamePrefix(pcfAppNameSuffix)
            .isWebProcessCountZero(isWebProcessCountZero)
            .build();

    String waitId = generateUuid();

    List<String> renderedTags = pcfStateHelper.getRenderedTags(context, tags);

    DelegateTask delegateTask = pcfStateHelper.getDelegateTask(
        PcfDelegateTaskCreationData.builder()
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .waitId(waitId)
            .taskType(TaskType.PCF_COMMAND_TASK)
            .envId(env.getUuid())
            .environmentType(env.getEnvironmentType())
            .infrastructureMappingId(pcfInfrastructureMapping.getUuid())
            .parameters(new Object[] {cfCommandSetupRequest, encryptedDataDetails})
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .taskDescription("PCF setup task execution")
            .serviceId(pcfInfrastructureMapping.getServiceId())
            .timeout(timeoutIntervalInMinutes == null ? DEFAULT_PCF_TASK_TIMEOUT_MIN : timeoutIntervalInMinutes)
            .tagList(renderedTags)
            .build());

    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  @VisibleForTesting
  void restoreStateDataAfterGitFetchIfNeeded(PcfSetupStateExecutionData pcfSetupStateExecutionData) {
    // means git fetch / custom fetch was not executed. No need to restore values
    if (pcfSetupStateExecutionData == null) {
      return;
    }

    useCurrentRunningCount = pcfSetupStateExecutionData.isUseCurrentRunningInstanceCount();
    olderActiveVersionCountToKeep = pcfSetupStateExecutionData.getActiveVersionsToKeep();
    timeoutIntervalInMinutes = pcfSetupStateExecutionData.getTimeout();
    pcfAppName = pcfSetupStateExecutionData.getPcfAppNameFromLegacyWorkflow();
    maxInstances = pcfSetupStateExecutionData.getMaxInstanceCount();
    enforceSslValidation = pcfSetupStateExecutionData.isEnforceSslValidation();
    useAppAutoscalar = pcfSetupStateExecutionData.isUseAppAutoscalar();
    resizeStrategy = pcfSetupStateExecutionData.getResizeStrategy();
    tempRouteMap = pcfSetupStateExecutionData.getTempRoutesOnSetupState();
    finalRouteMap = pcfSetupStateExecutionData.getFinalRoutesOnSetupState();
    useArtifactProcessingScript = pcfSetupStateExecutionData.isUseArtifactProcessingScript();
    artifactProcessingScript = pcfSetupStateExecutionData.getArtifactProcessingScript();
    tags = pcfSetupStateExecutionData.getTags();
  }

  @VisibleForTesting
  Integer fetchMaxCount(PcfManifestsPackage pcfManifestsPackage) {
    Integer maxCount;
    maxInstances = maxInstances == null || maxInstances < 0
        ? Integer.valueOf(PcfConstants.MANIFEST_INSTANCE_COUNT_DEFAULT)
        : maxInstances;
    maxCount = pcfStateHelper.fetchMaxCountFromManifest(
        pcfManifestsPackage, Integer.valueOf(PcfConstants.MANIFEST_INSTANCE_COUNT_DEFAULT));

    return maxCount;
  }

  /*
   * returns Artifactpath for source
   * */
  public String artifactPathForSource(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    switch (artifactStreamAttributes.getArtifactStreamType()) {
      case JENKINS:
        if (artifactStreamAttributes.getArtifactPaths().isEmpty()) {
          throw new InvalidRequestException("ArtifactPath missing, reqired for only-meta feature!");
        }
        return ARTIFACT_STRING + artifactStreamAttributes.getArtifactPaths().get(0);
      case BAMBOO:
        if (artifact.getArtifactFileMetadata().isEmpty()) {
          throw new InvalidRequestException("artifact url is required");
        }
        return artifact.getArtifactFileMetadata().get(0).getUrl();
      case ARTIFACTORY:
        return artifactStreamAttributes.getMetadata().get("artifactPath");
      case NEXUS:
        if (isEmpty(artifactStreamAttributes.getMetadata())
            || isEmpty(artifactStreamAttributes.getMetadata().get(URL))) {
          throw new InvalidRequestException("artifact url is required");
        }
        return artifactStreamAttributes.getMetadata().get(URL);
      default:
        return artifactStreamAttributes.getMetadata().get(URL);
    }
  }

  /*
   * returns ArtifactFileName for source
   * */
  public String artifactFileNameForSource(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    switch (artifactStreamAttributes.getArtifactStreamType()) {
      case JENKINS:
        return artifact.getDisplayName();
      case BAMBOO:
        if (artifactStreamAttributes.getArtifactPaths().isEmpty()) {
          throw new InvalidRequestException("ArtifactPath is missing!");
        }
        return artifactStreamAttributes.getArtifactPaths().get(0);
      case ARTIFACTORY:
        return artifactStreamAttributes.getMetadata().get("buildNo");
      case NEXUS:
        return artifact.getDisplayName().substring(artifact.getArtifactSourceName().length());
      default:
        return artifact.getDisplayName();
    }
  }

  private boolean onlyMetaForArtifactaType(ArtifactStream artifactStream) {
    switch (artifactStream.getArtifactStreamType()) {
      case JENKINS:
      case BAMBOO:
      case ARTIFACTORY:
      case NEXUS:
      case S3:
        return metadataOnlyBehindFlag(
            featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly());
      default:
        return false;
    }
  }

  @VisibleForTesting
  List<String> fetchRouteMaps(ExecutionContext context, PcfManifestsPackage pcfManifestsPackage,
      PcfInfrastructureMapping pcfInfrastructureMapping) {
    List<String> routeMaps =
        pcfStateHelper.getRouteMaps(pcfManifestsPackage.getManifestYml(), pcfInfrastructureMapping);

    if (blueGreen) {
      if (isEmpty(routeMaps) && isEmpty(finalRouteMap)) {
        throw new InvalidRequestException(
            "Final Routes can not be empty for BG deployment. Make sure manifest contains routes. no-route or random-route cant be used for BG as well.");
      }
      // In BlueGreen, we can not rely on cf push to perform variable substitution,
      // as temp routes will be used while creating app, and
      // routes mentioned in manifest will we swapped in the end after verification.
      // So, we need to resolve these values manually if they are referencing vars.yml
      routeMaps = pcfStateHelper.applyVarsYmlSubstitutionIfApplicable(routeMaps, pcfManifestsPackage);
    }

    // Add extra routes mentioned on SetupState
    List<String> finalRoutes = new ArrayList<>();
    if (isNotEmpty(routeMaps)) {
      finalRoutes.addAll(routeMaps);
    }
    if (isNotEmpty(finalRouteMap)) {
      finalRoutes.addAll(Arrays.asList(finalRouteMap));
    }

    return finalRoutes.stream().map(context::renderExpression).collect(toList());
  }

  @VisibleForTesting
  boolean shouldUseOriginalRoute() {
    // These constants were used in legacy pcf workflows
    String infraRouteConstLegacy = INFRA_ROUTE;
    String infraRouteConst = PCF_INFRA_ROUTE;

    boolean isOriginalRoute = false;
    // Always use tempRoute for BG
    if (blueGreen) {
      return false;
    }

    if (route == null || infraRouteConstLegacy.equalsIgnoreCase(route.trim())
        || infraRouteConst.equalsIgnoreCase(route.trim())) {
      isOriginalRoute = true;
    } else {
      isOriginalRoute = false;
    }

    return isOriginalRoute;
  }

  @VisibleForTesting
  String generateAppNamePrefix(ExecutionContext context, Application app, ServiceElement serviceElement,
      Environment env, PcfManifestsPackage pcfManifestsPackage, CfInternalConfig cfConfig) {
    String pcfAppNameSuffix = isNotBlank(pcfAppName)
        ? normalizeApplicationName(context.renderExpression(pcfAppName), cfConfig)
        : normalizeApplicationName(
            ServiceVersionConvention.getPrefix(app.getName(), serviceElement.getName(), env.getName()), cfConfig);

    pcfAppNameSuffix = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, pcfAppNameSuffix);
    return normalizeApplicationName(context.renderExpression(pcfAppNameSuffix), cfConfig);
  }

  private String normalizeApplicationName(String appName, CfInternalConfig cfConfig) {
    if (featureFlagService.isEnabled(CF_ALLOW_SPECIAL_CHARACTERS, cfConfig.getAccountId())) {
      return appName;
    }
    return normalizeExpression(appName);
  }

  @VisibleForTesting
  List<String> fetchTempRoutes(ExecutionContext context, PcfInfrastructureMapping pcfInfrastructureMapping) {
    List<String> tempRouteMaps =
        isEmpty(tempRouteMap) ? pcfInfrastructureMapping.getTempRouteMap() : Arrays.asList(tempRouteMap);
    if (isEmpty(tempRouteMaps)) {
      return emptyList();
    }
    tempRouteMaps = tempRouteMaps.stream().map(context::renderExpression).collect(toList());
    return tempRouteMaps;
  }

  @VisibleForTesting
  Integer getCurrentRunningCountForSetupRequest() {
    if (!useCurrentRunningCount) {
      return null;
    }

    if (currentRunningCount == null || currentRunningCount.intValue() == 0) {
      return Integer.valueOf(2);
    }

    return currentRunningCount;
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

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    PcfSetupStateExecutionData stateExecutionData = (PcfSetupStateExecutionData) context.getStateExecutionData();
    TaskType taskType = stateExecutionData.getTaskType();
    switch (taskType) {
      case GIT_FETCH_FILES_TASK:
        return handleAsyncResponseForGitTask(context, response);

      case CUSTOM_MANIFEST_FETCH_TASK:
        return handleAsyncResponseForCustomFetchValuesTaskWrapper(context, response);

      case PCF_COMMAND_TASK:
        return handleAsyncResponseForPCFTask(context, response);

      default:
        throw new InvalidRequestException("Unhandled task type " + taskType);
    }
  }

  protected ExecutionResponse handleAsyncResponseForPCFTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = getActivityId(context);
    CfCommandExecutionResponse executionResponse = (CfCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    PcfSetupStateExecutionData stateExecutionData = (PcfSetupStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

    CfSetupCommandResponse cfSetupCommandResponse = (CfSetupCommandResponse) executionResponse.getPcfCommandResponse();

    boolean isPcfSetupCommandResponseNull = cfSetupCommandResponse == null;
    SetupSweepingOutputPcfBuilder setupSweepingOutputPcfBuilder =
        SetupSweepingOutputPcf.builder()
            .serviceId(stateExecutionData.getServiceId())
            .commandName(PCF_SETUP_COMMAND)
            .maxInstanceCount(stateExecutionData.getMaxInstanceCount())
            .useCurrentRunningInstanceCount(stateExecutionData.isUseCurrentRunningInstanceCount())
            .currentRunningInstanceCount(generateCurrentRunningCount(cfSetupCommandResponse))
            .desiredActualFinalCount(getActualDesiredCount(stateExecutionData, cfSetupCommandResponse))
            .resizeStrategy(stateExecutionData.getResizeStrategy())
            .infraMappingId(stateExecutionData.getInfraMappingId())
            .pcfCommandRequest(stateExecutionData.getPcfCommandRequest())
            .isStandardBlueGreenWorkflow(stateExecutionData.isStandardBlueGreen())
            .mostRecentInactiveAppVersionDetails(
                isPcfSetupCommandResponseNull ? null : cfSetupCommandResponse.getMostRecentInactiveAppVersion())
            .useAppAutoscalar(stateExecutionData.isUseAppAutoscalar())
            .enforceSslValidation(stateExecutionData.isEnforceSslValidation())
            .pcfManifestsPackage(stateExecutionData.getPcfManifestsPackage())
            .tags(stateExecutionData.getTags())
            .cfAppNamePrefix(stateExecutionData.getCfAppNamePrefix())
            .nonVersioning(!isPcfSetupCommandResponseNull && cfSetupCommandResponse.isNonVersioning())
            .versioningChanged(!isPcfSetupCommandResponseNull && cfSetupCommandResponse.isVersioningChanged())
            .activeAppRevision(isPcfSetupCommandResponseNull ? null : cfSetupCommandResponse.getActiveAppRevision())
            .existingAppNamingStrategy(isPcfSetupCommandResponseNull
                    ? AppNamingStrategy.VERSIONING.name()
                    : cfSetupCommandResponse.getExistingAppNamingStrategy())
            .isWebProcessCountZero(stateExecutionData.isWebProcessCountZero())
            .isUseCfCli(true);

    if (!isPcfSetupCommandResponseNull) {
      setupSweepingOutputPcfBuilder.timeoutIntervalInMinutes(timeoutIntervalInMinutes)
          .totalPreviousInstanceCount(
              Optional.ofNullable(cfSetupCommandResponse.getTotalPreviousInstanceCount()).orElse(0))
          .appDetailsToBeDownsized(cfSetupCommandResponse.getDownsizeDetails());
      if (ExecutionStatus.SUCCESS == executionStatus) {
        setupSweepingOutputPcfBuilder.isSuccess(true);
        setupSweepingOutputPcfBuilder.newPcfApplicationDetails(cfSetupCommandResponse.getNewApplicationDetails());
        addNewlyCreateRouteMapIfRequired(stateExecutionData, cfSetupCommandResponse, setupSweepingOutputPcfBuilder,
            context.getWorkflowExecutionId());
      }
    }

    SetupSweepingOutputPcf setupSweepingOutputPcf = setupSweepingOutputPcfBuilder.build();
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(pcfStateHelper.obtainSetupSweepingOutputName(context, isRollback()))
                                   .value(setupSweepingOutputPcf)
                                   .build());
    pcfStateHelper.populatePcfVariables(context, setupSweepingOutputPcf);

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(stateExecutionData)
        .build();
  }

  @VisibleForTesting
  Integer getActualDesiredCount(
      PcfSetupStateExecutionData stateExecutionData, CfSetupCommandResponse cfSetupCommandResponse) {
    Integer actualDesiredCount = stateExecutionData.getMaxInstanceCount();

    // When currentRunningCount = 0, use instance count from manifest
    if (stateExecutionData.isUseCurrentRunningInstanceCount()) {
      Integer currentRunningCount = generateCurrentRunningCount(cfSetupCommandResponse);
      if (currentRunningCount.intValue() > 0) {
        actualDesiredCount = currentRunningCount;
      }
    }

    return actualDesiredCount;
  }

  @VisibleForTesting
  Integer generateCurrentRunningCount(CfSetupCommandResponse cfSetupCommandResponse) {
    if (cfSetupCommandResponse == null) {
      return Integer.valueOf(0);
    }

    Integer currentRunningCountFetched = cfSetupCommandResponse.getInstanceCountForMostRecentVersion();
    if (currentRunningCountFetched == null || currentRunningCountFetched.intValue() <= 0) {
      return Integer.valueOf(0);
    }

    return currentRunningCountFetched;
  }

  private void addNewlyCreateRouteMapIfRequired(PcfSetupStateExecutionData stateExecutionData,
      CfSetupCommandResponse cfSetupCommandResponse, SetupSweepingOutputPcfBuilder setupSweepingOutputPcfBuilder,
      String workflowExecutionId) {
    PcfInfrastructureMapping infrastructureMapping = (PcfInfrastructureMapping) infrastructureMappingService.get(
        stateExecutionData.getAppId(), stateExecutionData.getInfraMappingId());
    boolean isInfraUpdated = false;
    if (stateExecutionData.isUseTempRoutes()) {
      List<String> tempRoutes = stateExecutionData.getTempRouteMaps();
      if (EmptyPredicate.isEmpty(tempRoutes)
          || tempRoutes.stream().anyMatch(str -> str.startsWith("((") && str.endsWith("))"))) {
        tempRoutes = cfSetupCommandResponse.getNewApplicationDetails().getUrls();
        isInfraUpdated = true;
        infrastructureMapping.setTempRouteMap(tempRoutes);
      }
      setupSweepingOutputPcfBuilder.tempRouteMap(tempRoutes);
      stateExecutionData.setTempRouteMaps(tempRoutes);
      setupSweepingOutputPcfBuilder.routeMaps(stateExecutionData.getRouteMaps());
    } else {
      List<String> routes = stateExecutionData.getRouteMaps();
      if (EmptyPredicate.isEmpty(routes)
          || routes.stream().anyMatch(str -> str.startsWith("((") && str.endsWith("))"))) {
        routes = cfSetupCommandResponse.getNewApplicationDetails().getUrls();
        isInfraUpdated = true;
        infrastructureMapping.setRouteMaps(routes);
      }
      setupSweepingOutputPcfBuilder.routeMaps(routes);
      stateExecutionData.setRouteMaps(routes);
      setupSweepingOutputPcfBuilder.tempRouteMap(stateExecutionData.getTempRouteMaps());
    }

    if (isInfraUpdated) {
      infrastructureMappingService.update(infrastructureMapping, workflowExecutionId);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Nothing to be done here
  }

  private Activity createActivity(
      ExecutionContext executionContext, boolean valuesInGit, boolean valuesInCustomSource) {
    Application app = executionContext.getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();

    List<CommandUnit> commandUnitList = getCommandUnitList(valuesInGit, valuesInCustomSource);

    PhaseElement phaseElement = executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Artifact artifact =
        ((DeploymentExecutionContext) executionContext).getDefaultArtifactForService(serviceElement.getUuid());
    notNullCheck("Artifact Can not be null", artifact);

    ActivityBuilder activityBuilder = pcfStateHelper.getActivityBuilder(PcfActivityBuilderCreationData.builder()
                                                                            .appName(app.getName())
                                                                            .appId(app.getUuid())
                                                                            .commandName(PCF_SETUP_COMMAND)
                                                                            .type(Type.Command)
                                                                            .executionContext(executionContext)
                                                                            .commandType(getStateType())
                                                                            .commandUnitType(CommandUnitType.PCF_SETUP)
                                                                            .commandUnits(commandUnitList)
                                                                            .environment(env)
                                                                            .build());

    activityBuilder.artifactId(artifact.getUuid());
    activityBuilder.artifactName(artifact.getDisplayName());
    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    activityBuilder.artifactStreamId(artifactStream.getUuid());
    activityBuilder.artifactStreamName(artifactStream.getSourceName());
    return activityService.save(activityBuilder.build());
  }

  private ExecutionResponse executeGitTask(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId) {
    final DelegateTask gitFetchFileTask = pcfStateHelper.createGitFetchFileAsyncTask(
        context, appManifestMap, activityId, isSelectionLogsTrackingForTasksEnabled());
    gitFetchFileTask.setTags(pcfStateHelper.getRenderedTags(context, tags));

    final String delegateTaskId = delegateService.queueTaskV2(gitFetchFileTask);
    appendDelegateTaskDetails(context, gitFetchFileTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(gitFetchFileTask.getWaitId()))
        .stateExecutionData(PcfSetupStateExecutionData.builder()
                                .activityId(activityId)
                                .commandName(PCF_SETUP_COMMAND)
                                .taskType(GIT_FETCH_FILES_TASK)
                                .appManifestMap(appManifestMap)
                                .activeVersionsToKeep(olderActiveVersionCountToKeep)
                                .timeout(timeoutIntervalInMinutes)
                                .useAppAutoscalar(useAppAutoscalar)
                                .enforceSslValidation(enforceSslValidation)
                                .pcfAppNameFromLegacyWorkflow(pcfAppName)
                                .maxInstanceCount(maxInstances)
                                .resizeStrategy(resizeStrategy)
                                .useCurrentRunningInstanceCount(useCurrentRunningCount)
                                .tempRoutesOnSetupState(tempRouteMap)
                                .finalRoutesOnSetupState(finalRouteMap)
                                .useArtifactProcessingScript(useArtifactProcessingScript)
                                .artifactProcessingScript(artifactProcessingScript)
                                .tags(tags)
                                .isNonVersioning(isNonVersioning)
                                .build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private ExecutionResponse executeCustomFetchValuesTask(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId) {
    DelegateTask delegateTask = pcfStateHelper.createCustomFetchValuesTask(
        context, appManifestMap, activityId, isSelectionLogsTrackingForTasksEnabled(), getTimeoutMillis());

    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    delegateTask.getData().setExpressionFunctorToken(expressionFunctorToken);

    PcfSetupStateExecutionData newStateExecutionData = PcfSetupStateExecutionData.builder()
                                                           .activityId(activityId)
                                                           .commandName(PCF_SETUP_COMMAND)
                                                           .taskType(CUSTOM_MANIFEST_FETCH_TASK)
                                                           .appManifestMap(appManifestMap)
                                                           .timeout(timeoutIntervalInMinutes)
                                                           .activeVersionsToKeep(olderActiveVersionCountToKeep)
                                                           .useAppAutoscalar(useAppAutoscalar)
                                                           .enforceSslValidation(enforceSslValidation)
                                                           .pcfAppNameFromLegacyWorkflow(pcfAppName)
                                                           .maxInstanceCount(maxInstances)
                                                           .resizeStrategy(resizeStrategy)
                                                           .useCurrentRunningInstanceCount(useCurrentRunningCount)
                                                           .tempRoutesOnSetupState(tempRouteMap)
                                                           .finalRoutesOnSetupState(finalRouteMap)
                                                           .useArtifactProcessingScript(useArtifactProcessingScript)
                                                           .artifactProcessingScript(artifactProcessingScript)
                                                           .tags(tags)
                                                           .isNonVersioning(isNonVersioning)
                                                           .build();

    updateGitFetchFilesResult(context, newStateExecutionData);
    prepareDelegateTask(context, newStateExecutionData, delegateTask, expressionFunctorToken);
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(delegateTaskId))
        .stateExecutionData(newStateExecutionData)
        .build();
  }

  private void updateGitFetchFilesResult(ExecutionContext context, PcfSetupStateExecutionData newStateExecutionData) {
    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    if (null != pcfSetupStateExecutionData && null != pcfSetupStateExecutionData.getFetchFilesResult()) {
      newStateExecutionData.setFetchFilesResult(pcfSetupStateExecutionData.getFetchFilesResult());
    }
  }

  private void prepareDelegateTask(ExecutionContext context, StateExecutionData stateExecutionData,
      DelegateTask delegateTask, int expressionFunctorToken) {
    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    renderDelegateTask(context, delegateTask, stateExecutionContext);
  }

  private ExecutionResponse handleAsyncResponseForGitTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = getActivityId(context);

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getGitCommandStatus() == GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = pcfSetupStateExecutionData.getAppManifestMap();
    if (pcfStateHelper.isValuesInCustomSource(appManifestMap)) {
      return executeCustomFetchValuesTask(context, appManifestMap, activityId);
    } else {
      return executePcfTask(context, activityId, appManifestMap);
    }
  }

  private ExecutionResponse handleAsyncResponseForCustomFetchValuesTaskWrapper(
      ExecutionContext context, Map<String, ResponseData> response) {
    String appId = context.getAppId();
    String activityId = getActivityId(context);
    CustomManifestValuesFetchResponse executionResponse =
        (CustomManifestValuesFetchResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (executionStatus == ExecutionStatus.FAILED) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = pcfSetupStateExecutionData.getAppManifestMap();
    Map<K8sValuesLocation, Collection<String>> valuesFiles =
        applicationManifestUtils.getValuesFilesFromCustomFetchValuesResponse(
            context, appManifestMap, executionResponse, VARS_YML);
    if (null == pcfSetupStateExecutionData.getValuesFiles()) {
      pcfSetupStateExecutionData.setValuesFiles(new HashMap<>());
    }
    pcfSetupStateExecutionData.getValuesFiles().putAll(valuesFiles);
    pcfSetupStateExecutionData.setZippedManifestFileId(executionResponse.getZippedManifestFileId());

    return executePcfTask(context, activityId, pcfSetupStateExecutionData.getAppManifestMap());
  }

  private String getActivityId(ExecutionContext context) {
    return ((PcfSetupStateExecutionData) context.getStateExecutionData()).getActivityId();
  }

  @VisibleForTesting
  List<CommandUnit> getCommandUnitList(boolean valuesInGit, boolean valuesInCustomSource) {
    List<CommandUnit> canaryCommandUnits = new ArrayList<>();

    if (valuesInGit) {
      canaryCommandUnits.add(new PcfDummyCommandUnit(FetchGitFiles));
    }
    if (valuesInCustomSource) {
      canaryCommandUnits.add(new PcfDummyCommandUnit(FetchCustomFiles));
    }
    canaryCommandUnits.add(new PcfDummyCommandUnit(VerifyManifests));
    canaryCommandUnits.add(new PcfDummyCommandUnit(CheckExistingApps));
    canaryCommandUnits.add(new PcfDummyCommandUnit(PcfSetup));
    canaryCommandUnits.add(new PcfDummyCommandUnit(Wrapup));
    return canaryCommandUnits;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
