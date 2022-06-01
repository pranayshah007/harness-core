/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.collaboration;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.SERVICENOW_ASYNC;
import static software.wings.utils.Utils.isJSONValid;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.ServiceNowExecutionData;
import software.wings.beans.Activity.Type;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.servicenow.ServiceNowCreateUpdateParams;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.servicenow.ServiceNowTaskParameters;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.delegatetasks.servicenow.ServiceNowAction;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ServiceNowCreateUpdateState extends State implements SweepingOutputStateMixin {
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;
  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private ServiceNowCreateUpdateParams serviceNowCreateUpdateParams;
  @Inject private transient ActivityService activityService;
  @Inject private transient WingsPersistence wingsPersistence;
  @Inject private transient SecretManager secretManager;
  @Inject private transient DelegateService delegateService;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject private transient ActivityHelperService activityHelperService;
  @Inject private transient SettingsService settingsService;
  @Transient @Inject KryoSerializer kryoSerializer;
  @Inject @Transient private FeatureFlagService featureFlagService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private static final long ASYNC_TASK_TIMEOUT_MILLIS = 60 * 1000;
  private static final String ISSUE_NUMBER = "issueNumber";
  private static final String ISSUE_ID = "issueId";
  private static final String TRANSFORMATION_VALUES = "transformationValues";
  public ServiceNowCreateUpdateState(String name) {
    super(name, StateType.SERVICENOW_CREATE_UPDATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId =
        activityHelperService.createAndSaveActivity(context, Type.Command, getName(), getStateType(), Arrays.asList())
            .getUuid();
    return executeInternal(context, activityId);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();

    ServiceNowExecutionData snowExecutionData = (ServiceNowExecutionData) responseEntry.getValue();
    snowExecutionData.setActivityId(activityId);

    if (snowExecutionData.getExecutionStatus() == ExecutionStatus.SUCCESS) {
      Map<String, Object> sweepingOutputMap = new HashMap<>();
      sweepingOutputMap.put(ISSUE_ID, snowExecutionData.getIssueId());
      sweepingOutputMap.put(ISSUE_NUMBER, snowExecutionData.getIssueNumber());
      if (EmptyPredicate.isNotEmpty(snowExecutionData.getTransformationValues())) {
        sweepingOutputMap.put(TRANSFORMATION_VALUES, snowExecutionData.getTransformationValues());
      }
      handleSweepingOutput(sweepingOutputService, context, sweepingOutputMap);
    }

    return ExecutionResponse.builder()
        .stateExecutionData(snowExecutionData)
        .executionStatus(snowExecutionData.getExecutionStatus())
        .errorMessage(snowExecutionData.getResponseMsg())
        .build();
  }

  private ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String accountId = executionContext.fetchRequiredApp().getAccountId();
    ServiceNowConfig config = getSnowConfig(serviceNowCreateUpdateParams.getSnowConnectorId(), accountId);
    renderExpressions(context, serviceNowCreateUpdateParams);

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    boolean isScopingHonored = featureFlagService.isEnabled(FeatureName.HONOR_DELEGATE_SCOPING, accountId);
    String envId = null;
    String envType = null;
    if (isScopingHonored) {
      envId = (workflowStandardParams == null
                  || workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null)
          ? null
          : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();
      envType = context.getEnvType();
    }

    ServiceNowTaskParameters serviceNowTaskParameters;
    if (serviceNowCreateUpdateParams.getAction() == ServiceNowAction.IMPORT_SET) {
      if (!isJSONValid(serviceNowCreateUpdateParams.getJsonBody())) {
        throw new ServiceNowException("Json Body is not a valid Json: " + serviceNowCreateUpdateParams.getJsonBody(),
            ErrorCode.SERVICENOW_ERROR, WingsException.USER);
      }
      serviceNowTaskParameters = ServiceNowTaskParameters.builder()
                                     .serviceNowConfig(config)
                                     .encryptionDetails(secretManager.getEncryptionDetails(config,
                                         executionContext.getAppId(), executionContext.getWorkflowExecutionId()))
                                     .action(serviceNowCreateUpdateParams.getAction())
                                     .accountId(accountId)
                                     .importSetTableName(serviceNowCreateUpdateParams.getImportSetTableName())
                                     .jsonBody(serviceNowCreateUpdateParams.getJsonBody())
                                     .build();
    } else {
      serviceNowTaskParameters =
          ServiceNowTaskParameters.builder()
              .serviceNowConfig(config)
              .encryptionDetails(secretManager.getEncryptionDetails(
                  config, executionContext.getAppId(), executionContext.getWorkflowExecutionId()))
              .ticketType(ServiceNowTicketType.valueOf(serviceNowCreateUpdateParams.getTicketType()))
              .issueNumber(serviceNowCreateUpdateParams.getIssueNumber())
              .issueId(serviceNowCreateUpdateParams.getTicketId())
              .action(serviceNowCreateUpdateParams.getAction())
              .updateMultiple(serviceNowCreateUpdateParams.isUpdateMultiple())
              .accountId(accountId)
              .fields(serviceNowCreateUpdateParams.fetchFields())
              .additionalFields(serviceNowCreateUpdateParams.fetchAdditionalFields())
              .build();
    }

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(activityId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.fetchRequiredApp().getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, envType)
            .description(serviceNowTaskParameters.getAction() != null
                    ? serviceNowCreateUpdateParams.getAction().getDisplayName() + " ServiceNow ticket"
                    : "ServiceNow task")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(SERVICENOW_ASYNC.name())
                      .parameters(new Object[] {serviceNowTaskParameters})
                      .timeout(ASYNC_TASK_TIMEOUT_MILLIS)
                      .build())
            .tags(config.getDelegateSelectors())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();
    String delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ServiceNowExecutionData.builder().activityId(activityId).build())
        .build();
  }

  private void renderExpressions(ExecutionContext context, ServiceNowCreateUpdateParams params) {
    params.setIssueNumber(context.renderExpression(params.getIssueNumber()));
    Map<ServiceNowFields, String> renderedFields = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(params.fetchFields())) {
      for (Entry<ServiceNowFields, String> entry : params.fetchFields().entrySet()) {
        renderedFields.put(entry.getKey(), context.renderExpression(entry.getValue()));
      }
    }
    params.setFields(renderedFields);
    Map<String, String> renderedAdditionalFields = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(params.fetchAdditionalFields())) {
      for (Entry<String, String> entry : params.fetchAdditionalFields().entrySet()) {
        renderedAdditionalFields.put(entry.getKey(), context.renderExpression(entry.getValue()));
      }
    }

    params.setAdditionalFields(renderedAdditionalFields);
  }

  private ServiceNowConfig getSnowConfig(String snowConnectorId, String accountId) {
    SettingAttribute snowSettingAttribute = settingsService.getByAccountAndId(accountId, snowConnectorId);
    notNullCheck("Service Now connector may be deleted.", snowSettingAttribute);

    if (!(snowSettingAttribute.getValue() instanceof ServiceNowConfig)) {
      throw new InvalidRequestException("Type of Setting Attribute Value is not SnowConfig");
    }

    return (ServiceNowConfig) snowSettingAttribute.getValue();
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
