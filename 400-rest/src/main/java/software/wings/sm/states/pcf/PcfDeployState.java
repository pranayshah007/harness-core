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
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Upsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.model.PcfConstants.DEFAULT_PCF_TASK_TIMEOUT_MIN;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponse;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PcfInstanceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.api.pcf.DeploySweepingOutputPcf;
import software.wings.api.pcf.PcfDeployStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.service.mappers.artifact.CfConfigToInternalMapper;
import software.wings.service.mappers.artifact.CfInstanceElementMapper;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
public class PcfDeployState extends State {
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient SettingsService settingsService;
  @Inject private transient ActivityService activityService;
  @Inject private transient PcfStateHelper pcfStateHelper;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject protected transient FeatureFlagService featureFlagService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Attributes(title = "Desired Instances(cumulative)", required = true) private Integer instanceCount;
  @Attributes(title = "Instance Unit Type", required = true)
  private InstanceUnitType instanceUnitType = InstanceUnitType.PERCENTAGE;
  @Attributes(title = "Desired Instances- Old version") private Integer downsizeInstanceCount;
  @Getter @Setter private boolean useAppResizeV2;
  @Attributes(title = "Instance Unit Type")
  private InstanceUnitType downsizeInstanceUnitType = InstanceUnitType.PERCENTAGE;
  @Getter @Setter private List<String> tags;
  public static final String PCF_RESIZE_COMMAND = "PCF Resize";
  static final String NO_PREV_DEPLOYMENT_MSG = "No rollback required, skipping rollback";
  @Getter
  @Setter
  @Attributes(title = "App resize Timeout Interval (Minutes)")
  @DefaultValue("5")
  private Integer timeoutIntervalInMinutes = 5;

  /**
   * Instantiates a new state.
   *
   * @param name      the name
   */
  public PcfDeployState(String name) {
    super(name, StateType.PCF_RESIZE.name());
  }

  public PcfDeployState(String name, String stateType) {
    super(name, stateType);
  }

  public Integer getInstanceCount() {
    return instanceCount;
  }
  public void setInstanceCount(Integer instanceCount) {
    this.instanceCount = instanceCount;
  }
  public InstanceUnitType getInstanceUnitType() {
    return instanceUnitType;
  }
  public void setInstanceUnitType(InstanceUnitType instanceUnitType) {
    this.instanceUnitType = instanceUnitType;
  }

  public Integer getDownsizeInstanceCount() {
    return downsizeInstanceCount;
  }

  public void setDownsizeInstanceCount(Integer downsizeInstanceCount) {
    this.downsizeInstanceCount = downsizeInstanceCount;
  }

  public InstanceUnitType getDownsizeInstanceUnitType() {
    return downsizeInstanceUnitType;
  }

  public void setDownsizeInstanceUnitType(InstanceUnitType downsizeInstanceUnitType) {
    this.downsizeInstanceUnitType = downsizeInstanceUnitType;
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
    return pcfStateHelper.getStateTimeoutMillis(context, DEFAULT_PCF_TASK_TIMEOUT_MIN, isRollback());
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    Application app = appService.get(context.getAppId());
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);

    PcfInfrastructureMapping pcfInfrastructureMapping =
        (PcfInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    SetupSweepingOutputPcf setupSweepingOutputPcf = null;

    try {
      setupSweepingOutputPcf = pcfStateHelper.findSetupSweepingOutputPcf(context, isRollback());
    } catch (InvalidArgumentsException ex) {
      if (isRollback()) {
        setupSweepingOutputPcf = SetupSweepingOutputPcf.builder().build();
      } else {
        throw ex;
      }
    }

    pcfStateHelper.populatePcfVariables(context, setupSweepingOutputPcf);

    Activity activity = createActivity(context, setupSweepingOutputPcf);
    if (isRollback() && pcfStateHelper.isRollBackNotNeeded(setupSweepingOutputPcf)) {
      return pcfStateHelper.handleRollbackSkipped(
          context.getAppId(), activity.getUuid(), PCF_RESIZE_COMMAND, NO_PREV_DEPLOYMENT_MSG);
    }

    SettingAttribute settingAttribute = settingsService.get(pcfInfrastructureMapping.getComputeProviderSettingId());
    CfInternalConfig pcfConfig = CfConfigToInternalMapper.toCfInternalConfig((PcfConfig) settingAttribute.getValue());

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(pcfConfig, context.getAppId(), context.getWorkflowExecutionId());

    Integer upsizeUpdateCount = getUpsizeUpdateCount(setupSweepingOutputPcf, pcfConfig);
    Integer downsizeUpdateCount = getDownsizeUpdateCount(setupSweepingOutputPcf, pcfConfig);

    PcfDeployStateExecutionData stateExecutionData =
        getPcfDeployStateExecutionData(setupSweepingOutputPcf, activity, upsizeUpdateCount, downsizeUpdateCount);

    CfCommandRequest commandRequest = getPcfCommandRequest(context, app, activity.getUuid(), setupSweepingOutputPcf,
        pcfConfig, upsizeUpdateCount, downsizeUpdateCount, stateExecutionData, pcfInfrastructureMapping);

    if (isRollback() && isNotEmpty(stateExecutionData.getSetupSweepingOutputPcf().getTags())) {
      tags = pcfStateHelper.getRenderedTags(context, stateExecutionData.getSetupSweepingOutputPcf().getTags());
    }
    List<String> renderedTags = pcfStateHelper.getRenderedTags(context, tags);
    DelegateTask task =
        pcfStateHelper.getDelegateTask(PcfDelegateTaskCreationData.builder()
                                           .appId(app.getUuid())
                                           .accountId(app.getAccountId())
                                           .taskType(TaskType.PCF_COMMAND_TASK)
                                           .waitId(activity.getUuid())
                                           .envId(env.getUuid())
                                           .environmentType(env.getEnvironmentType())
                                           .infrastructureMappingId(pcfInfrastructureMapping.getUuid())
                                           .serviceId(pcfInfrastructureMapping.getServiceId())
                                           .parameters(new Object[] {commandRequest, encryptedDataDetails})
                                           .timeout(timeoutIntervalInMinutes)
                                           .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
                                           .taskDescription("PCF Deploy task execution")
                                           .tagList(renderedTags)
                                           .build());

    delegateService.queueTaskV2(task);
    appendDelegateTaskDetails(context, task);

    return ExecutionResponse.builder()
        .correlationIds(Arrays.asList(activity.getUuid()))
        .stateExecutionData(stateExecutionData)
        .async(true)
        .build();
  }

  private PcfDeployStateExecutionData getPcfDeployStateExecutionData(SetupSweepingOutputPcf setupSweepingOutputPcf,
      Activity activity, Integer upsizeUpdateCount, Integer downsizeUpdateCount) {
    return PcfDeployStateExecutionData.builder()
        .activityId(activity.getUuid())
        .commandName(PCF_RESIZE_COMMAND)
        .releaseName(getApplicationNameFromSetupContext(setupSweepingOutputPcf))
        .updateCount(upsizeUpdateCount)
        .updateDetails(new StringBuilder()
                           .append("{Name: ")
                           .append(getApplicationNameFromSetupContext(setupSweepingOutputPcf))
                           .append(", DesiredCount: ")
                           .append(upsizeUpdateCount)
                           .append("}")
                           .toString())
        .setupSweepingOutputPcf(setupSweepingOutputPcf)
        .build();
  }

  private String getApplicationNameFromSetupContext(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    if (setupSweepingOutputPcf == null || setupSweepingOutputPcf.getNewPcfApplicationDetails() == null) {
      return StringUtils.EMPTY;
    }
    return setupSweepingOutputPcf.getNewPcfApplicationDetails().getApplicationName();
  }

  protected Integer getUpsizeUpdateCount(SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig) {
    Integer count = setupSweepingOutputPcf.getDesiredActualFinalCount();
    return getInstanceCountToBeUpdated(
        count, instanceCount, instanceUnitType, true, pcfConfig, true, setupSweepingOutputPcf.isWebProcessCountZero());
  }

  @VisibleForTesting
  protected Integer getDownsizeUpdateCount(SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig) {
    boolean hasUserDefinedDownsizeForOldApp = downsizeInstanceCount != null;
    Integer downsizeUpdateCount = downsizeInstanceCount == null ? instanceCount : downsizeInstanceCount;
    downsizeInstanceUnitType = downsizeInstanceUnitType == null ? instanceUnitType : downsizeInstanceUnitType;

    Integer existingAppInstanceCount = getInstanceCountForExistingApp(setupSweepingOutputPcf);

    Integer runningInstanceCount = existingAppInstanceCount != null
        ? existingAppInstanceCount
        : setupSweepingOutputPcf.getDesiredActualFinalCount();
    downsizeUpdateCount = getInstanceCountToBeUpdated(runningInstanceCount, downsizeUpdateCount,
        downsizeInstanceUnitType, false, pcfConfig, hasUserDefinedDownsizeForOldApp, false);

    return downsizeUpdateCount;
  }

  private Integer getInstanceCountForExistingApp(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    List<CfAppSetupTimeDetails> appDetailsToBeDownsized = setupSweepingOutputPcf.getAppDetailsToBeDownsized();
    CfAppSetupTimeDetails existingAppDetails = null;
    if (isNotEmpty(appDetailsToBeDownsized)) {
      existingAppDetails = appDetailsToBeDownsized.get(0);
    }

    if (existingAppDetails != null && existingAppDetails.getInitialInstanceCount() != null
        && existingAppDetails.getInitialInstanceCount() > 0) {
      return existingAppDetails.getInitialInstanceCount();
    }

    return null;
  }

  private Integer getInstanceCountToBeUpdated(Integer maxInstanceCount, Integer instanceCountValue,
      InstanceUnitType unitType, boolean upsize, CfInternalConfig pcfConfig, boolean hasUserDefinedDownsizeForOldApp,
      boolean isWebProcessCountZero) {
    // final count after upsize or downsize in this deploy phase
    if (isWebProcessCountZero) {
      return 0;
    }
    Integer updateCount;
    if (unitType == PERCENTAGE) {
      int percent = Math.min(instanceCountValue, 100);
      int count = (int) Math.round((percent * maxInstanceCount) / 100.0);
      if (upsize) {
        // if use inputs 40%, means count after this phase deployment should be 40% of maxInstances
        updateCount = Math.max(count, 1);
      } else {
        if (featureFlagService.isEnabled(FeatureName.PCF_OLD_APP_RESIZE, pcfConfig.getAccountId()) && useAppResizeV2
            && hasUserDefinedDownsizeForOldApp) {
          updateCount = Math.max(count, 0);
        } else {
          // if use inputs 40%, means 60% (100 - 40) of maxInstances should be downsized for old apps
          // so only 40% of the max instances would remain
          updateCount = Math.max(count, 0);
          updateCount = maxInstanceCount - updateCount;
        }
      }
    } else {
      if (upsize) {
        // if use inputs 5, means count after this phase deployment should be 5
        updateCount = Math.min(maxInstanceCount, instanceCountValue);
      } else {
        if (featureFlagService.isEnabled(FeatureName.PCF_OLD_APP_RESIZE, pcfConfig.getAccountId()) && useAppResizeV2
            && hasUserDefinedDownsizeForOldApp) {
          updateCount = Math.max(0, instanceCountValue);
        } else {
          // if use inputs 5, means count after this phase deployment for old apps should be,
          // so manxInstances - 5 should be downsized
          updateCount = Math.max(0, maxInstanceCount - instanceCountValue);
        }
      }
    }
    return updateCount;
  }

  protected CfCommandRequest getPcfCommandRequest(ExecutionContext context, Application application, String activityId,
      SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig, Integer updateCount,
      Integer downsizeUpdateCount, PcfDeployStateExecutionData stateExecutionData,
      PcfInfrastructureMapping infrastructureMapping) {
    boolean useAppAutoscalar = setupSweepingOutputPcf.isUseAppAutoscalar();
    return CfCommandDeployRequest.builder()
        .activityId(activityId)
        .commandName(PCF_RESIZE_COMMAND)
        .workflowExecutionId(context.getWorkflowExecutionId())
        .organization(setupSweepingOutputPcf.getPcfCommandRequest().getOrganization())
        .space(setupSweepingOutputPcf.getPcfCommandRequest().getSpace())
        .pcfConfig(pcfConfig)
        .pcfCommandType(PcfCommandType.RESIZE)
        .maxCount(setupSweepingOutputPcf.getDesiredActualFinalCount())
        .updateCount(updateCount)
        .downSizeCount(downsizeUpdateCount)
        .totalPreviousInstanceCount(setupSweepingOutputPcf.getTotalPreviousInstanceCount() == null
                ? Integer.valueOf(0)
                : setupSweepingOutputPcf.getTotalPreviousInstanceCount())
        .resizeStrategy(setupSweepingOutputPcf.getResizeStrategy())
        .instanceData(emptyList())
        .routeMaps(setupSweepingOutputPcf.getRouteMaps())
        .appId(application.getUuid())
        .accountId(application.getAccountId())
        .newReleaseName(getApplicationNameFromSetupContext(setupSweepingOutputPcf))
        .timeoutIntervalInMin(setupSweepingOutputPcf.getTimeoutIntervalInMinutes())
        .downsizeAppDetail(isEmpty(setupSweepingOutputPcf.getAppDetailsToBeDownsized())
                ? null
                : setupSweepingOutputPcf.getAppDetailsToBeDownsized().get(0))
        .isStandardBlueGreen(setupSweepingOutputPcf.isStandardBlueGreenWorkflow())
        .useAppAutoscalar(useAppAutoscalar)
        .enforceSslValidation(setupSweepingOutputPcf.isEnforceSslValidation())
        .pcfManifestsPackage(setupSweepingOutputPcf.getPcfManifestsPackage())
        .useCfCLI(true)
        .limitPcfThreads(featureFlagService.isEnabled(LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
        .cfCliVersion(
            pcfStateHelper.getCfCliVersionOrDefault(application.getAppId(), setupSweepingOutputPcf.getServiceId()))
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    CfCommandExecutionResponse executionResponse = (CfCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    CfDeployCommandResponse cfDeployCommandResponse =
        (CfDeployCommandResponse) executionResponse.getPcfCommandResponse();

    if (cfDeployCommandResponse.getInstanceDataUpdated() == null) {
      cfDeployCommandResponse.setInstanceDataUpdated(new ArrayList<>());
    }

    // update PcfDeployStateExecutionData,
    PcfDeployStateExecutionData stateExecutionData = (PcfDeployStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
    stateExecutionData.setInstanceData(cfDeployCommandResponse.getInstanceDataUpdated());

    List<PcfInstanceElement> respPcfInstanceElements =
        CfInstanceElementMapper.toPcfInstanceElements(cfDeployCommandResponse.getPcfInstanceElements());

    // For now, only use newInstances. Do not use existing instances. It will be done as a part of separate story
    List<PcfInstanceElement> pcfInstanceElements = isEmpty(respPcfInstanceElements)
        ? emptyList()
        : respPcfInstanceElements.stream()
              .filter(pcfInstanceElement -> pcfInstanceElement.isNewInstance())
              .collect(toList());

    List<PcfInstanceElement> pcfOldInstanceElements = isEmpty(respPcfInstanceElements)
        ? emptyList()
        : respPcfInstanceElements.stream()
              .filter(pcfInstanceElement -> !pcfInstanceElement.isNewInstance())
              .collect(toList());

    List<InstanceStatusSummary> instanceStatusSummaries =
        pcfInstanceElements.stream()
            .map(pcfInstanceElement
                -> anInstanceStatusSummary()
                       .withInstanceElement((InstanceElement) cloneMinAsInstanceElement(pcfInstanceElement))
                       .withStatus(ExecutionStatus.SUCCESS)
                       .build())
            .collect(toList());

    stateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);

    if (!isRollback()) {
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                     .name(pcfStateHelper.obtainDeploySweepingOutputName(context, false))
                                     .value(DeploySweepingOutputPcf.builder()
                                                .instanceData(stateExecutionData.getInstanceData())
                                                .name(stateExecutionData.getReleaseName())
                                                .build())
                                     .build());

      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails = pcfStateHelper.generateInstanceDetails(respPcfInstanceElements);
      boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
      sweepingOutputService.save(
          context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
              .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
              .value(InstanceInfoVariables.builder()
                         .instanceElements(pcfStateHelper.generateInstanceElement(respPcfInstanceElements))
                         .instanceDetails(instanceDetails)
                         .skipVerification(skipVerification)
                         .build())
              .build());
    } else {
      pcfStateHelper.updateAppNamesVariables(context, cfDeployCommandResponse);
    }

    InstanceElementListParam instanceElementListParam =
        InstanceElementListParam.builder()
            .instanceElements(pcfStateHelper.generateInstanceElement(pcfInstanceElements))
            .pcfInstanceElements(pcfInstanceElements)
            .pcfOldInstanceElements(pcfOldInstanceElements)
            .build();

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(executionResponse.getErrorMessage())
        .stateExecutionData(stateExecutionData)
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private Activity createActivity(ExecutionContext executionContext, SetupSweepingOutputPcf setupSweepingOutputPcf) {
    Application app = ((ExecutionContextImpl) executionContext).getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();

    if (app == null) {
      throw new InvalidRequestException("Application was null in Context");
    }

    ActivityBuilder activityBuilder =
        pcfStateHelper.getActivityBuilder(PcfActivityBuilderCreationData.builder()
                                              .appId(app.getUuid())
                                              .appName(app.getName())
                                              .commandName(PCF_RESIZE_COMMAND)
                                              .type(Type.Command)
                                              .executionContext(executionContext)
                                              .commandType(getStateType())
                                              .commandUnitType(CommandUnitType.PCF_RESIZE)
                                              .environment(env)
                                              .commandUnits(getCommandUnitList(setupSweepingOutputPcf))
                                              .build());

    return activityService.save(activityBuilder.build());
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = newHashMap();
    if (instanceCount == null || instanceCount < 0) {
      invalidFields.put("instanceCount", "Instance count needs to be populated");
    }
    return invalidFields;
  }

  @VisibleForTesting
  List<CommandUnit> getCommandUnitList(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    List<CommandUnit> canaryCommandUnits = new ArrayList<>();
    if (isRollback() || RESIZE_NEW_FIRST == setupSweepingOutputPcf.getResizeStrategy()) {
      canaryCommandUnits.add(new PcfDummyCommandUnit(Upsize));
      canaryCommandUnits.add(new PcfDummyCommandUnit(Downsize));
    } else {
      canaryCommandUnits.add(new PcfDummyCommandUnit(Downsize));
      canaryCommandUnits.add(new PcfDummyCommandUnit(Upsize));
    }

    canaryCommandUnits.add(new PcfDummyCommandUnit(Wrapup));

    return canaryCommandUnits;
  }

  private ContextElement cloneMinAsInstanceElement(PcfInstanceElement pcfInstanceElement) {
    return anInstanceElement()
        .uuid(pcfInstanceElement.getUuid())
        .displayName(pcfInstanceElement.getDisplayName())
        .build();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
