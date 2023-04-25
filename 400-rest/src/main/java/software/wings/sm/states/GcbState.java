/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;

import static software.wings.beans.TaskType.GCB;
import static software.wings.beans.command.GcbTaskParams.GcbTaskType.CANCEL;
import static software.wings.beans.command.GcbTaskParams.GcbTaskType.POLL;
import static software.wings.beans.command.GcbTaskParams.GcbTaskType.START;
import static software.wings.helpers.ext.gcb.models.GcbBuildStatus.CANCELLED;
import static software.wings.sm.states.gcbconfigs.GcbOptions.GcbSpecSource.REMOTE;
import static software.wings.sm.states.gcbconfigs.GcbOptions.GcbSpecSource.TRIGGER;
import static software.wings.utils.GitUtilsManager.fetchCompleteGitRepoUrl;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.UnsupportedOperationException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.GcbExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.GcbTaskParams;
import software.wings.beans.command.GcbTaskParams.GcbTaskType;
import software.wings.beans.template.TemplateUtils;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.GcbDelegateResponse;
import software.wings.helpers.ext.gcb.models.GcbBuildStatus;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.gcbconfigs.GcbOptions;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class GcbState extends State implements SweepingOutputStateMixin {
  public static final String GCB_LOGS = "GCB Output";
  public static final String BUILD_NO = "buildNo";

  @Getter @Setter private GcbOptions gcbOptions;
  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;

  @Transient @Inject private DelegateService delegateService;
  @Transient @Inject private ActivityService activityService;
  @Transient @Inject private SecretManager secretManager;
  @Transient @Inject private SweepingOutputService sweepingOutputService;
  @Transient @Inject private TemplateExpressionProcessor templateExpressionProcessor;
  @Transient @Inject private TemplateUtils templateUtils;
  @Transient @Inject private SettingsService settingsService;
  @Transient @Inject KryoSerializer kryoSerializer;
  @Transient @Inject InfrastructureMappingService infrastructureMappingService;
  @Transient @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public GcbState(String name) {
    super(name, StateType.GCB.name());
  }

  @Override
  @Attributes(title = "Wait interval before execution (s)")
  public Integer getWaitInterval() {
    return super.getWaitInterval();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ASYNC_CALL_TIMEOUT)
  @Override
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis() != null ? super.getTimeoutMillis() : Math.toIntExact(DEFAULT_ASYNC_CALL_TIMEOUT);
  }

  @Override
  public ExecutionResponse execute(final @NotNull ExecutionContext context) {
    String activityId = createActivity(context);
    return executeInternal(context, activityId);
  }

  Map<String, String> evaluate(
      @NotNull final ExecutionContext context, @Nullable final List<NameValuePair> parameters) {
    return Stream.of(parameters)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .collect(toMap(NameValuePair::getName,
            entry -> context.renderExpression(entry.getValue()), CollectionUtils::overrideOperator, HashMap::new));
  }

  /**
   * Execute internal execution response.
   *
   * @param context the context
   * @return the execution response
   */
  protected ExecutionResponse executeInternal(
      final @NotNull ExecutionContext context, final @NotNull String activityId) {
    resolveGcbOptionExpressions(context);

    final Application application = context.fetchRequiredApp();
    final String appId = application.getAppId();
    Map<String, String> substitutions = null;
    if (gcbOptions.getSpecSource() == TRIGGER && !isEmpty(gcbOptions.getTriggerSpec().getSubstitutions())) {
      substitutions = evaluate(context, gcbOptions.getTriggerSpec().getSubstitutions());
    }
    if (!isEmpty(getTemplateExpressions())) {
      TemplateExpression gcpConfigExp =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "gcpConfigId");
      TemplateExpression gitConfigExp =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "gitConfigId");
      if (gcpConfigExp != null && !resolveGcpTemplateExpression(gcpConfigExp, context)) {
        return ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage("Google Cloud Provider does not exist. Please update with an appropriate cloud provider.")
            .build();
      }
      if (gitConfigExp != null && !resolveGitTemplateExpression(gitConfigExp, context)) {
        return ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage("Git connector does not exist. Please update with an appropriate git connector.")
            .build();
      }
    }
    GcpConfig gcpConfig = context.getGlobalSettingValue(context.getAccountId(), gcbOptions.getGcpConfigId());

    GitConfig gitConfig = gcbOptions.getSpecSource() == REMOTE
        ? context.getGlobalSettingValue(context.getAccountId(), gcbOptions.getRepositorySpec().getGitConfigId())
        : null;

    List<EncryptedDataDetail> gitConfigEncryptionDetails = new ArrayList<>();
    if (gitConfig != null) {
      gitConfigEncryptionDetails = secretManager.getEncryptionDetails(gitConfig);
    }
    if (gitConfig != null && gitConfig.getUrlType() == GitConfig.UrlType.ACCOUNT) {
      String repoName = gcbOptions.getRepositorySpec().getRepoName();
      gitConfig.setRepoName(repoName);
      gitConfig.setRepoUrl(fetchCompleteGitRepoUrl(gitConfig, repoName));
    }
    List<EncryptedDataDetail> gcpEncryptionDetails =
        secretManager.getEncryptionDetails(gcpConfig, context.getAppId(), context.getWorkflowExecutionId());
    List<EncryptedDataDetail> allEncryptionDetails = Stream.of(gcpEncryptionDetails, gitConfigEncryptionDetails)
                                                         .flatMap(Collection::stream)
                                                         .collect(Collectors.toList());
    GcbTaskParams gcbTaskParams = GcbTaskParams.builder()
                                      .gcpConfig(gcpConfig)
                                      .type(START)
                                      .gcbOptions(gcbOptions)
                                      .substitutions(substitutions)
                                      .encryptedDataDetails(allEncryptionDetails)
                                      .activityId(activityId)
                                      .unitName(GCB_LOGS)
                                      .gitConfig(gitConfig)
                                      .appId(appId)
                                      .build();

    if (getTimeoutMillis() != null) {
      gcbTaskParams.setTimeout(getTimeoutMillis());
      gcbTaskParams.setStartTs(System.currentTimeMillis());
    }
    DelegateTask delegateTask = delegateTaskOf(activityId, context, infrastructureMappingService, gcbTaskParams);
    delegateService.queueTaskV2(delegateTask);

    GcbExecutionData gcbExecutionData = GcbExecutionData.builder().activityId(activityId).build();
    gcbExecutionData.setTemplateVariable(templateUtils.processTemplateVariables(context, getTemplateVariables()));
    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(gcbExecutionData)
        .correlationIds(singletonList(activityId))
        .build();
  }

  private DelegateTask delegateTaskOf(@NotNull final String activityId, @NotNull final ExecutionContext context,
      InfrastructureMappingService infrastructureMappingService, Object... parameters) {
    final Application application = context.fetchRequiredApp();
    final WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String envId = Optional.ofNullable(workflowStandardParams)
                       .map(standardParams -> this.workflowStandardParamsExtensionService.getEnv(standardParams))
                       .map(Environment::getUuid)
                       .orElse(null);
    String infrastructureMappingId = context.fetchInfraMappingId();
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(context.getAppId(), infrastructureMappingId);
    String serviceId = infrastructureMapping == null ? null : infrastructureMapping.getServiceId();

    GcbTaskParams gcbTaskParams = (GcbTaskParams) parameters[0];
    GcbTaskType taskType = gcbTaskParams.getType();
    String action = StringUtils.capitalize(taskType.name());
    return DelegateTask.builder()
        .accountId(application.getAccountId())
        .waitId(activityId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, application.getAppId())
        .description(action + " GCB Build")
        .data(TaskData.builder()
                  .async(true)
                  .taskType(GCB.name())
                  .parameters(parameters)
                  .timeout(getTimeoutMillis())
                  .build())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())

        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, serviceId)
        .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      return ExecutionResponse.builder()
          .executionStatus(FAILED)
          .errorMessage(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage())
          .build();
    }

    GcbDelegateResponse delegateResponse = (GcbDelegateResponse) notifyResponseData;

    if (delegateResponse.isInterrupted()) {
      return ExecutionResponse.builder().executionStatus(DISCONTINUING).build();
    }
    if (delegateResponse.isWorking()) {
      return startPollTask(context, delegateResponse);
    }
    final GcbExecutionData gcbExecutionData = context.getStateExecutionData();
    activityService.updateStatus(
        delegateResponse.getParams().getActivityId(), context.getAppId(), delegateResponse.getStatus());

    handleSweepingOutput(sweepingOutputService, context, gcbExecutionData.withDelegateResponse(delegateResponse));

    ExecutionResponseBuilder responseBuilder =
        ExecutionResponse.builder().executionStatus(delegateResponse.getStatus()).stateExecutionData(gcbExecutionData);
    if (delegateResponse.getErrorMsg() != null) {
      responseBuilder.errorMessage(delegateResponse.getErrorMsg());
    }
    return responseBuilder.build();
  }

  protected ExecutionResponse startPollTask(ExecutionContext context, GcbDelegateResponse delegateResponse) {
    GcbTaskParams parameters = delegateResponse.getParams();
    parameters.setType(POLL);
    final String waitId = UUIDGenerator.generateUuid();
    DelegateTask delegateTask = delegateTaskOf(waitId, context, infrastructureMappingService, parameters);
    delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
    final GcbExecutionData gcbExecutionData = context.getStateExecutionData();
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(waitId))
        .stateExecutionData(gcbExecutionData.withDelegateResponse(delegateResponse))
        .executionStatus(RUNNING)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    if (!(context.getStateExecutionData() instanceof GcbExecutionData)) {
      context.getStateExecutionData().setErrorMsg(
          "Google Cloud Build step has been aborted while waiting before execution");
    } else if (((GcbExecutionData) context.getStateExecutionData()).getBuildStatus() == null) {
      context.getStateExecutionData().setErrorMsg("Google Cloud Build step has been aborted before build started");
    } else {
      GcbTaskParams params =
          GcbTaskParams.builder()
              .type(CANCEL)
              .gcpConfig(
                  (GcpConfig) settingsService.get(((GcbExecutionData) context.getStateExecutionData()).getGcpConfigId())
                      .getValue())
              .accountId(context.getAccountId())
              .buildId(String.valueOf(context.getStateExecutionData().getExecutionDetails().get(BUILD_NO).getValue()))
              .encryptedDataDetails(secretManager.getEncryptionDetails(
                  (GcpConfig) settingsService.get(((GcbExecutionData) context.getStateExecutionData()).getGcpConfigId())
                      .getValue(),
                  context.getAppId(), context.getWorkflowExecutionId()))
              .build();
      GcbDelegateResponse delegateResponse = null;
      try {
        delegateResponse = delegateService.executeTaskV2(
            delegateTaskOf(((GcbExecutionData) context.getStateExecutionData()).getActivityId(), context,
                infrastructureMappingService, params));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (delegateResponse != null && delegateResponse.getBuild() != null
          && delegateResponse.getBuild().getStatus() != null) {
        GcbBuildStatus buildStatus = delegateResponse.getBuild().getStatus();
        if (buildStatus == CANCELLED) {
          context.getStateExecutionData().setErrorMsg(
              String.format("Build with number: %s has been successfully cancelled",
                  context.getStateExecutionData().getExecutionDetails().get(BUILD_NO).getValue()));
          ((GcbExecutionData) context.getStateExecutionData()).setBuildStatus(CANCELLED);
        } else {
          context.getStateExecutionData().setErrorMsg(String.format("Failed to cancel build with number: %s",
              context.getStateExecutionData().getExecutionDetails().get(BUILD_NO).getValue()));
          context.getStateExecutionData().setStatus(FAILED);
          ((GcbExecutionData) context.getStateExecutionData()).setBuildStatus(buildStatus);
        }
      }
    }
  }

  @NotNull
  protected String createActivity(ExecutionContext executionContext) {
    ActivityBuilder builder = Activity.builder().commandUnitType(CommandUnitType.GCB);
    State.populateActivity(builder, this);
    ExecutionContextImpl.populateActivity(builder, executionContext);
    return activityService.save(builder.build()).getUuid();
  }

  @VisibleForTesting
  protected void resolveGcbOptionExpressions(ExecutionContext context) {
    switch (gcbOptions.getSpecSource()) {
      case TRIGGER:
        gcbOptions.getTriggerSpec().setSourceId(context.renderExpression(gcbOptions.getTriggerSpec().getSourceId()));
        gcbOptions.getTriggerSpec().setName(context.renderExpression(gcbOptions.getTriggerSpec().getName()));
        break;
      case INLINE:
        gcbOptions.setInlineSpec(context.renderExpression(gcbOptions.getInlineSpec()));
        break;
      case REMOTE:
        gcbOptions.getRepositorySpec().setSourceId(
            context.renderExpression(gcbOptions.getRepositorySpec().getSourceId()));
        gcbOptions.getRepositorySpec().setFilePath(
            context.renderExpression(gcbOptions.getRepositorySpec().getFilePath()));
        gcbOptions.getRepositorySpec().setRepoName(
            context.renderExpression(gcbOptions.getRepositorySpec().getRepoName()));
        break;
      default:
        throw new UnsupportedOperationException("Gcb option " + gcbOptions.getSpecSource() + " not supported");
    }
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  @VisibleForTesting
  protected boolean resolveGcpTemplateExpression(TemplateExpression gcpConfigExp, ExecutionContext context) {
    if (gcpConfigExp != null) {
      String resolvedExpression = templateExpressionProcessor.resolveTemplateExpression(context, gcpConfigExp);
      GcpConfig gcpConfig = context.getGlobalSettingValue(context.getAccountId(), resolvedExpression);
      if (gcpConfig != null) {
        gcbOptions.setGcpConfigId(resolvedExpression);
      } else {
        SettingAttribute setting =
            settingsService.getSettingAttributeByName(context.getAccountId(), resolvedExpression);
        if (setting == null) {
          return false;
        }
        gcbOptions.setGcpConfigId(setting.getUuid());
      }
    }
    return true;
  }

  @VisibleForTesting
  protected boolean resolveGitTemplateExpression(TemplateExpression gitConfigExp, ExecutionContext context) {
    if (gitConfigExp != null) {
      String resolvedExpression = templateExpressionProcessor.resolveTemplateExpression(context, gitConfigExp);
      GitConfig gitConfig = context.getGlobalSettingValue(context.getAccountId(), resolvedExpression);
      if (gitConfig != null) {
        gcbOptions.getRepositorySpec().setGitConfigId(resolvedExpression);
      } else {
        SettingAttribute setting =
            settingsService.getSettingAttributeByName(context.getAccountId(), resolvedExpression);
        if (setting == null) {
          return false;
        }
        gcbOptions.getRepositorySpec().setGitConfigId(setting.getUuid());
      }
    }
    return true;
  }
}
