/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.LIMIT_PCF_THREADS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pcf.CfCommandUnitConstants.FetchGitFiles;
import static io.harness.pcf.CfCommandUnitConstants.Pcfplugin;
import static io.harness.pcf.model.PcfConstants.DEFAULT_PCF_TASK_TIMEOUT_MIN;

import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.beans.TaskType.PCF_COMMAND_TASK;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FileData;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.ListUtils;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.model.PcfConstants;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.pcf.PcfPluginStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitFileConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.beans.template.TemplateUtils;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.mappers.artifact.CfConfigToInternalMapper;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.ApplicationManifestUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class PcfPluginState extends State {
  @Inject private transient DelegateService delegateService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient SecretManager secretManager;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private transient ActivityService activityService;
  @Inject private transient ServiceTemplateHelper serviceTemplateHelper;
  @Inject private transient AppService appService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient PcfStateHelper pcfStateHelper;
  @Inject private transient ApplicationManifestUtils applicationManifestUtils;
  @Inject private transient TemplateUtils templateUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public static final String PCF_PLUGIN_COMMAND = "Execute CF Command";
  public static final String FILE_START_REPO_ROOT_REGEX = PcfConstants.FILE_START_REPO_ROOT_REGEX;
  public static final String FILE_START_SERVICE_MANIFEST_REGEX = PcfConstants.FILE_START_SERVICE_MANIFEST_REGEX;
  public static final String FILE_END_REGEX = "(\\s|,|;|'|\"|:|$)";

  public static final Pattern PATH_REGEX_REPO_ROOT_PATTERN =
      Pattern.compile(FILE_START_REPO_ROOT_REGEX + ".*?" + FILE_END_REGEX);
  public static final Pattern FILE_START_SERVICE_MANIFEST_PATTERN =
      Pattern.compile(FILE_START_SERVICE_MANIFEST_REGEX + ".*?" + FILE_END_REGEX);

  @Getter @Setter @Attributes(title = "API Timeout Interval (Minutes)") private Integer timeoutIntervalInMinutes = 5;

  @Getter @Setter @Attributes(title = "Script") private String scriptString;

  @Getter @Setter @Attributes(title = "Tags") private List<String> tags;
  public static final String START_SLASH_ALL_MATCH = "\\A/+";
  public static final String END_SLASH_ALL_MATCH = "/+\\Z";

  public PcfPluginState(String name) {
    super(name, StateType.PCF_PLUGIN.name());
  }

  public PcfPluginState(String name, String stateType) {
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
  public Integer getTimeoutMillis(ExecutionContext context) {
    int timeoutInMinutes = resolveTimeoutIntervalInMinutes(getPcfPluginStateExecutionData(context));
    return Ints.checkedCast(TimeUnit.MINUTES.toMillis(timeoutInMinutes));
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    PcfPluginStateExecutionData pcfPluginStateExecutionData = PcfPluginStateExecutionData.builder().build();
    // resolve template variables
    pcfPluginStateExecutionData.setTemplateVariable(
        templateUtils.processTemplateVariables(context, getTemplateVariables()));

    // render script
    String rawScript = pcfStateHelper.removeCommentedLineFromScript(scriptString);
    // find out the paths from the script
    final ApplicationManifest serviceManifest = applicationManifestUtils.getApplicationManifestForService(context);
    final boolean serviceManifestRemote = isServiceManifestRemote(serviceManifest);

    final Activity activity = createActivity(context, serviceManifestRemote);
    String repoRoot = "/";
    if (serviceManifestRemote) {
      repoRoot = getRepoRoot(serviceManifest);
    }
    final List<String> pathsFromScript = findPathFromScript(rawScript, repoRoot);

    if (!pathsFromScript.isEmpty() && serviceManifestRemote) {
      //  fire task to fetch remote files
      return executeGitTask(context, serviceManifest, activity.getUuid(), pathsFromScript, rawScript, repoRoot);
    } else {
      return executePcfPluginTask(context, activity.getUuid(), serviceManifest, pathsFromScript, rawScript, repoRoot);
    }
  }

  private String getRepoRoot(ApplicationManifest serviceManifest) {
    final GitFileConfig gitFileConfig = serviceManifest.getGitFileConfig();
    return "/" + toRelativePath(defaultIfEmpty(gitFileConfig.getFilePath(), "/").trim());
  }

  @VisibleForTesting
  List<String> findPathFromScript(String rendredScript, String repoRoot) {
    final Set<String> finalPathLists = new HashSet<>();
    final List<String> repoRootPrefixPathList =
        findPathFromScript(rendredScript, PATH_REGEX_REPO_ROOT_PATTERN, FILE_START_REPO_ROOT_REGEX, FILE_END_REGEX);
    List<String> serviceManifestPrefixPathList = findPathFromScript(
        rendredScript, FILE_START_SERVICE_MANIFEST_PATTERN, FILE_START_SERVICE_MANIFEST_REGEX, FILE_END_REGEX);

    if (!(isEmpty(repoRoot) || "/".equals(repoRoot))) {
      serviceManifestPrefixPathList = serviceManifestPrefixPathList.stream()
                                          .map(path -> repoRoot + path)
                                          .map(this::removeTrailingSlash)
                                          .collect(Collectors.toList());
    }

    finalPathLists.addAll(repoRootPrefixPathList);
    finalPathLists.addAll(serviceManifestPrefixPathList);
    return new ArrayList<>(finalPathLists);
  }

  private String removeTrailingSlash(String s) {
    return s.replaceFirst(END_SLASH_ALL_MATCH, "");
  }

  private List<String> findPathFromScript(
      String renderedScript, Pattern matchPattern, String prefixRegex, String fileEndRegex) {
    final Matcher matcher = matchPattern.matcher(renderedScript);
    List<String> filePathList = new ArrayList<>();
    while (matcher.find()) {
      final String filePath = renderedScript.substring(matcher.start(), matcher.end())
                                  .trim()
                                  .replaceFirst(prefixRegex, "")
                                  .replaceFirst(fileEndRegex, "");
      filePathList.add(filePath);
    }
    return filePathList.stream().map(this::canonacalizePath).distinct().collect(Collectors.toList());
  }

  private String canonacalizePath(String path) {
    return Strings.defaultIfEmpty(path.trim(), "/");
  }

  private boolean isServiceManifestRemote(ApplicationManifest serviceManifest) {
    return serviceManifest.getStoreType() == StoreType.Remote;
  }

  private ExecutionResponse executeGitTask(ExecutionContext context, ApplicationManifest serviceManifest,
      String activityId, List<String> pathsFromScript, String rawScriptString, String repoRoot) {
    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    final Map<K8sValuesLocation, ApplicationManifest> appManifestMap =
        prepareManifestForGitFetchTask(serviceManifest, pathsFromScript);
    final DelegateTask gitFetchFileTask = pcfStateHelper.createGitFetchFileAsyncTask(
        context, appManifestMap, activityId, isSelectionLogsTrackingForTasksEnabled());
    gitFetchFileTask.getData().setExpressionFunctorToken(expressionFunctorToken);
    gitFetchFileTask.setTags(resolveTags(getTags(), null));

    PcfPluginStateExecutionData stateExecutionData =
        PcfPluginStateExecutionData.builder()
            .activityId(activityId)
            .commandName(PCF_PLUGIN_COMMAND)
            .taskType(GIT_FETCH_FILES_TASK)
            .appManifestMap(appManifestMap)
            .filePathsInScript(pathsFromScript)
            .renderedScriptString(rawScriptString)
            .timeoutIntervalInMinutes(resolveTimeoutIntervalInMinutes(null))
            .tagList(getTags())
            .repoRoot(repoRoot)
            .build();
    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    renderDelegateTask(context, gitFetchFileTask, stateExecutionContext);
    // resolve template variables
    stateExecutionData.setTemplateVariable(templateUtils.processTemplateVariables(context, getTemplateVariables()));

    final String delegateTaskId = delegateService.queueTaskV2(gitFetchFileTask);
    appendDelegateTaskDetails(context, gitFetchFileTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(gitFetchFileTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private Map<K8sValuesLocation, ApplicationManifest> prepareManifestForGitFetchTask(
      ApplicationManifest serviceManifest, List<String> pathsFromScript) {
    List<String> gitFiles = pathsFromScript.stream().map(this::toRelativePath).collect(Collectors.toList());
    // in case root folder is accessed, remove all the file paths
    if (gitFiles.contains("")) {
      gitFiles = new ArrayList<>();
      gitFiles.add("");
    }
    serviceManifest.getGitFileConfig().setFilePathList(gitFiles);
    serviceManifest.getGitFileConfig().setFilePath(null);
    return ImmutableMap.of(K8sValuesLocation.Service, serviceManifest);
  }
  private String toRelativePath(String path) {
    return path.trim().replaceFirst(START_SLASH_ALL_MATCH, "");
  }

  private int resolveTimeoutIntervalInMinutes(PcfPluginStateExecutionData pcfPluginStateExecutionData) {
    if (timeoutIntervalInMinutes != null) {
      return timeoutIntervalInMinutes;
    }
    if (pcfPluginStateExecutionData != null && pcfPluginStateExecutionData.getTimeoutIntervalInMinutes() != null) {
      return pcfPluginStateExecutionData.getTimeoutIntervalInMinutes();
    }
    return DEFAULT_PCF_TASK_TIMEOUT_MIN;
  }

  private String resolveRenderedScript(
      String renderedScriptString, PcfPluginStateExecutionData pcfPluginStateExecutionData) {
    if (EmptyPredicate.isNotEmpty(renderedScriptString)) {
      return renderedScriptString;
    }
    if (pcfPluginStateExecutionData != null
        && EmptyPredicate.isNotEmpty(pcfPluginStateExecutionData.getRenderedScriptString())) {
      return pcfPluginStateExecutionData.getRenderedScriptString();
    }
    return "";
  }
  @VisibleForTesting
  ExecutionResponse executePcfPluginTask(ExecutionContext context, String activityId,
      ApplicationManifest serviceManifest, List<String> pathsFromScript, String rawScriptString, String repoRoot) {
    PhaseElement phaseElement = getPhaseElement(context);
    WorkflowStandardParams workflowStandardParams = getWorkflowStandardParams(context);
    Application app = requireNonNull(appService.get(context.getAppId()), "App cannot be null");
    Environment env =
        requireNonNull(workflowStandardParamsExtensionService.getEnv(workflowStandardParams), "Env cannot be null");
    ServiceElement serviceElement = phaseElement.getServiceElement();

    final PcfInfrastructureMapping pcfInfrastructureMapping = getPcfInfrastructureMapping(context, app);
    SettingAttribute settingAttribute = settingsService.get(pcfInfrastructureMapping.getComputeProviderSettingId());
    CfInternalConfig pcfConfig = CfConfigToInternalMapper.toCfInternalConfig((PcfConfig) settingAttribute.getValue());
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());

    final PcfPluginStateExecutionData pcfPluginStateExecutionData = getPcfPluginStateExecutionData(context);
    // prepare file for transfer if any
    List<FileData> fileDataList = prepareFilesForTransfer(serviceManifest, pathsFromScript, context);

    // todo @rk : add the skip ssl validation parameter if available in context
    // get all tags
    final List<String> renderedTags = getRenderedTags(context, resolveTags(getTags(), pcfPluginStateExecutionData));
    final int timeoutIntervalInMin = resolveTimeoutIntervalInMinutes(pcfPluginStateExecutionData);

    PcfPluginStateExecutionData stateExecutionData = PcfPluginStateExecutionData.builder()
                                                         .activityId(activityId)
                                                         .accountId(app.getAccountId())
                                                         .appId(app.getUuid())
                                                         .envId(env.getUuid())
                                                         .environmentType(env.getEnvironmentType())
                                                         .serviceId(serviceElement.getUuid())
                                                         .infraMappingId(pcfInfrastructureMapping.getUuid())
                                                         .commandName(PCF_PLUGIN_COMMAND)
                                                         .taskType(PCF_COMMAND_TASK)
                                                         .repoRoot(repoRoot)
                                                         .build();

    // resolve template variables
    stateExecutionData.setTemplateVariable(templateUtils.processTemplateVariables(context, getTemplateVariables()));

    CfCommandRequest commandRequest =
        CfRunPluginCommandRequest.builder()
            .activityId(activityId)
            .appId(app.getUuid())
            .accountId(app.getAccountId())
            .commandName(PCF_PLUGIN_COMMAND)
            .organization(context.renderExpression(pcfInfrastructureMapping.getOrganization()))
            .space(context.renderExpression(pcfInfrastructureMapping.getSpace()))
            .pcfConfig(pcfConfig)
            .pcfCommandType(PcfCommandType.RUN_PLUGIN)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .timeoutIntervalInMin(timeoutIntervalInMin)
            .renderedScriptString(resolveRenderedScript(rawScriptString, pcfPluginStateExecutionData))
            .filePathsInScript(resolveFilePathsInScript(pathsFromScript, pcfPluginStateExecutionData))
            .fileDataList(fileDataList)
            .encryptedDataDetails(encryptedDataDetails)
            .repoRoot(repoRoot)
            .limitPcfThreads(featureFlagService.isEnabled(LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
            .cfCliVersion(pcfStateHelper.getCfCliVersionOrDefault(app.getAppId(), serviceElement.getUuid()))
            .build();

    stateExecutionData.setPcfCommandRequest(commandRequest);

    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    DelegateTask delegateTask =
        pcfStateHelper.getDelegateTask(PcfDelegateTaskCreationData.builder()
                                           .accountId(app.getAccountId())
                                           .appId(app.getUuid())
                                           .waitId(activityId)
                                           .taskType(TaskType.PCF_COMMAND_TASK)
                                           .envId(env.getUuid())
                                           .environmentType(env.getEnvironmentType())
                                           .infrastructureMappingId(pcfInfrastructureMapping.getUuid())
                                           .serviceId(pcfInfrastructureMapping.getServiceId())
                                           .parameters(new Object[] {commandRequest})
                                           .timeout(timeoutIntervalInMin)
                                           .tagList(renderedTags)
                                           .serviceTemplateId(getServiceTemplateId(pcfInfrastructureMapping))
                                           .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
                                           .taskDescription("PCF Plugin task execution")
                                           .build());
    delegateTask.getData().setExpressionFunctorToken(expressionFunctorToken);
    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    renderDelegateTask(context, delegateTask, stateExecutionContext);

    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .correlationIds(Collections.singletonList(activityId))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  private String getServiceTemplateId(PcfInfrastructureMapping pcfInfrastructureMapping) {
    return pcfInfrastructureMapping == null ? null
                                            : serviceTemplateHelper.fetchServiceTemplateId(pcfInfrastructureMapping);
  }

  private List<String> getRenderedTags(ExecutionContext context, List<String> tagList) {
    final List<String> renderedTags = CollectionUtils.emptyIfNull(tagList)
                                          .stream()
                                          .map(context::renderExpression)
                                          .distinct()
                                          .collect(Collectors.toList());
    return ListUtils.trimStrings(renderedTags);
  }

  private PcfPluginStateExecutionData getPcfPluginStateExecutionData(ExecutionContext context) {
    return (PcfPluginStateExecutionData) context.getStateExecutionData();
  }

  private List<FileData> prepareFilesForTransfer(
      ApplicationManifest serviceManifest, List<String> pathsFromScript, ExecutionContext context) {
    if (EmptyPredicate.isEmpty(pathsFromScript)) {
      return new ArrayList<>();
    }
    Map<String, String> pathToContentMap;
    if (isServiceManifestRemote(serviceManifest)) {
      pathToContentMap = getGitFilesForTransfer(context);
    } else {
      pathToContentMap = getLocalFilesForTransfer(serviceManifest, pathsFromScript);
    }
    if (EmptyPredicate.isEmpty(pathToContentMap)) {
      return new ArrayList<>();
    }

    return pathToContentMap.entrySet()
        .stream()
        .map(entry -> FileData.builder().filePath(entry.getKey()).fileContent(entry.getValue()).build())
        .collect(Collectors.toList());
  }

  private Map<String, String> getLocalFilesForTransfer(
      ApplicationManifest serviceManifest, List<String> pathsFromScript) {
    if (EmptyPredicate.isEmpty(pathsFromScript)) {
      return Collections.emptyMap();
    }
    return CollectionUtils
        .emptyIfNull(applicationManifestService.getManifestFilesByAppManifestId(
            serviceManifest.getAppId(), serviceManifest.getUuid()))
        .stream()
        .collect(Collectors.toMap(ManifestFile::getFileName, ManifestFile::getFileContent));
    //  todo @rk improvement : filter only required files, currently sending all the files
  }

  private Map<String, String> getGitFilesForTransfer(ExecutionContext context) {
    // get the files from the context
    final PcfPluginStateExecutionData pcfPluginStateExecutionData = getPcfPluginStateExecutionData(context);
    if (pcfPluginStateExecutionData != null) {
      final GitFetchFilesFromMultipleRepoResult fetchFilesResult = pcfPluginStateExecutionData.getFetchFilesResult();
      if (fetchFilesResult != null) {
        final GitFetchFilesResult serviceManifestFetchResult =
            MapUtils.emptyIfNull(fetchFilesResult.getFilesFromMultipleRepo()).get(K8sValuesLocation.Service.name());
        if (serviceManifestFetchResult != null && EmptyPredicate.isNotEmpty(serviceManifestFetchResult.getFiles())) {
          return serviceManifestFetchResult.getFiles().stream().collect(
              Collectors.toMap(GitFile::getFilePath, GitFile::getFileContent));
        }
      }
    }

    return Collections.emptyMap();
  }

  private List<String> resolveFilePathsInScript(
      List<String> pathsFromScript, PcfPluginStateExecutionData pcfPluginStateExecutionData) {
    if (EmptyPredicate.isNotEmpty(pathsFromScript)) {
      return pathsFromScript;
    }
    if (pcfPluginStateExecutionData != null
        && EmptyPredicate.isNotEmpty(pcfPluginStateExecutionData.getFilePathsInScript())) {
      return pcfPluginStateExecutionData.getFilePathsInScript();
    }
    return Collections.emptyList();
  }

  private List<String> resolveTags(List<String> tags, PcfPluginStateExecutionData pcfPluginStateExecutionData) {
    if (CollectionUtils.isNotEmpty(tags)) {
      return tags;
    }
    if (pcfPluginStateExecutionData != null) {
      return org.apache.commons.collections4.ListUtils.emptyIfNull(pcfPluginStateExecutionData.getTagList());
    }
    return Collections.emptyList();
  }

  private PhaseElement getPhaseElement(ExecutionContext context) {
    return context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
  }

  private PcfInfrastructureMapping getPcfInfrastructureMapping(ExecutionContext context, Application app) {
    return requireNonNull(
        (PcfInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId()),
        "PcfInfrastructureMapping cannot be null");
  }

  private WorkflowStandardParams getWorkflowStandardParams(ExecutionContext context) {
    return context.getContextElement(ContextElementType.STANDARD);
  }

  private Activity createActivity(ExecutionContext executionContext, boolean remoteManifestType) {
    Application app = executionContext.fetchRequiredApp();
    Environment env = executionContext.fetchRequiredEnvironment();

    List<CommandUnit> commandUnitList = getCommandUnitList(remoteManifestType);

    ActivityBuilder activityBuilder = pcfStateHelper.getActivityBuilder(PcfActivityBuilderCreationData.builder()
                                                                            .appName(app.getName())
                                                                            .appId(app.getUuid())
                                                                            .commandName(PCF_PLUGIN_COMMAND)
                                                                            .type(Type.Command)
                                                                            .executionContext(executionContext)
                                                                            .commandType(getStateType())
                                                                            .commandUnitType(CommandUnitType.PCF_PLUGIN)
                                                                            .commandUnits(commandUnitList)
                                                                            .environment(env)
                                                                            .build());

    return activityService.save(activityBuilder.build());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to do here
  }

  List<CommandUnit> getCommandUnitList(boolean remoteStoreType) {
    final Builder<CommandUnit> canaryCommandUnitsBuilder = ImmutableList.builder();

    if (remoteStoreType) {
      canaryCommandUnitsBuilder.add(new PcfDummyCommandUnit(FetchGitFiles));
    }
    canaryCommandUnitsBuilder.add(new PcfDummyCommandUnit(Pcfplugin));
    return canaryCommandUnitsBuilder.build();
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
    PcfPluginStateExecutionData stateExecutionData = getPcfPluginStateExecutionData(context);
    TaskType taskType = stateExecutionData.getTaskType();

    switch (taskType) {
      case GIT_FETCH_FILES_TASK:
        return handleAsyncResponseForGitTask(context, response);
      case PCF_COMMAND_TASK:
        return handleAsyncResponseForPluginTask(context, response);
      default:
        throw new InvalidRequestException("Unhandled task type " + taskType);
    }
  }

  private ExecutionResponse handleAsyncResponseForPluginTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    final String activityId = getActivityId(context);

    CfCommandExecutionResponse executionResponse = (CfCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);
    PcfPluginStateExecutionData pcfPluginStateExecutionData = getPcfPluginStateExecutionData(context);
    pcfPluginStateExecutionData.setStatus(executionStatus);
    pcfPluginStateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(pcfPluginStateExecutionData)
        .build();
  }

  private ExecutionResponse handleAsyncResponseForGitTask(
      ExecutionContext context, Map<String, ResponseData> response) {
    final String activityId = getActivityId(context);

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getGitCommandStatus() == GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      WorkflowStandardParams workflowStandardParams = getWorkflowStandardParams(context);
      activityService.updateStatus(activityId, workflowStandardParams.getAppId(), executionStatus);
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }

    PcfPluginStateExecutionData pcfPluginStateExecutionData = getPcfPluginStateExecutionData(context);
    pcfPluginStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    return executePcfPluginTask(context, activityId,
        pcfPluginStateExecutionData.getAppManifestMap().get(K8sValuesLocation.Service),
        pcfPluginStateExecutionData.getFilePathsInScript(), pcfPluginStateExecutionData.getRenderedScriptString(),
        pcfPluginStateExecutionData.getRepoRoot());
  }

  private String getActivityId(ExecutionContext context) {
    return ((PcfPluginStateExecutionData) context.getStateExecutionData()).getActivityId();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
