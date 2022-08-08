/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.tasks.ResponseData;

import software.wings.api.AwsLambdaContextElement.FunctionMeta;
import software.wings.api.AwsLambdaExecutionData;
import software.wings.api.AwsLambdaFunctionElement;
import software.wings.api.CommandStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.LambdaTestEvent;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.LambdaConvention;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class AwsLambdaVerification extends State {
  @Attributes(title = "Function Test Events") private List<LambdaTestEvent> lambdaTestEvents = new ArrayList<>();

  private static final long TIME_OUT_IN_MINUTES = 2;

  @Transient @Inject private ActivityService activityService;
  @Transient @Inject private SecretManager secretManager;
  @Inject @Transient private transient InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private transient SettingsService settingsService;
  @Inject @Transient private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private DelegateService delegateService;

  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public AwsLambdaVerification(String name) {
    super(name, StateType.AWS_LAMBDA_VERIFICATION.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    AwsLambdaExecutionData awsLambdaExecutionData = new AwsLambdaExecutionData();
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);

      AwsLambdaInfraStructureMapping infrastructureMapping =
          (AwsLambdaInfraStructureMapping) infrastructureMappingService.get(
              app.getUuid(), context.fetchInfraMappingId());

      SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();

      AwsLambdaFunctionElement awsLambdaFunctionElement =
          context.getContextElement(ContextElementType.AWS_LAMBDA_FUNCTION);
      FunctionMeta functionMeta = awsLambdaFunctionElement.getFunctionArn();

      awsLambdaExecutionData.setFunctionArn(functionMeta.getFunctionArn());
      awsLambdaExecutionData.setFunctionName(functionMeta.getFunctionName());
      awsLambdaExecutionData.setFunctionVersion(functionMeta.getVersion());

      ImmutableMap<String, LambdaTestEvent> functionNameMap = lambdaTestEvents == null
          ? ImmutableMap.of()
          : Maps.uniqueIndex(lambdaTestEvents,
              lambdaTestEvent
              -> LambdaConvention.normalizeFunctionName(context.renderExpression(lambdaTestEvent.getFunctionName())));

      LambdaTestEvent lambdaTestEvent =
          functionNameMap.getOrDefault(functionMeta.getFunctionName(), LambdaTestEvent.builder().build());

      return executeTask(awsConfig.getAccountId(),
          AwsLambdaExecuteFunctionRequest.builder()
              .awsConfig(awsConfig)
              .encryptionDetails(
                  secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId()))
              .region(awsLambdaFunctionElement.getRegion())
              .functionName(functionMeta.getFunctionArn())
              .qualifier(functionMeta.getVersion())
              .payload(isNotBlank(lambdaTestEvent.getPayload()) ? lambdaTestEvent.getPayload() : null)
              .awsLambdaExecutionData(awsLambdaExecutionData)
              .lambdaTestEvent(lambdaTestEvent)
              .build(),
          activityId, infrastructureMapping, context);

    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse executeTask(String accountId, AwsLambdaRequest request, String activityId,
      InfrastructureMapping infrastructureMapping, ExecutionContext context) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(
                Cd1SetupFields.APP_ID_FIELD, isNotEmpty(context.getAppId()) ? context.getAppId() : GLOBAL_APP_ID)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, infrastructureMapping.getEnvId())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMapping.getUuid())
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Aws Lambda Verification task")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AWS_LAMBDA_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                      .build())
            .waitId(activityId)
            .build();

    String delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(
            CommandStateExecutionData.Builder.aCommandStateExecutionData().withActivityId(activityId).build())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      String activityId = response.keySet().iterator().next();
      AwsLambdaExecuteFunctionResponse functionResponse =
          (AwsLambdaExecuteFunctionResponse) response.values().iterator().next();

      AwsLambdaExecutionData awsLambdaExecutionData = functionResponse.getAwsLambdaExecutionData();
      awsLambdaExecutionData.setStatusCode(functionResponse.getStatusCode());
      awsLambdaExecutionData.setFunctionError(functionResponse.getFunctionError());

      if (functionResponse.getExecutionStatus() == ExecutionStatus.FAILED) {
        return ExecutionResponse.builder()
            .executionStatus(functionResponse.getExecutionStatus())
            .stateExecutionData(awsLambdaExecutionData)
            .errorMessage(functionResponse.getErrorMessage())
            .build();
      }

      boolean assertionStatus = true;
      ExecutionStatus executionStatus = functionResponse.getExecutionStatus();
      String logResult = functionResponse.getLogResult();
      if (logResult != null) {
        awsLambdaExecutionData.setLogResult(logResult);
      }
      awsLambdaExecutionData.setPayload(functionResponse.getPayload());

      // The assertion could be null
      LambdaTestEvent lambdaTestEvent = functionResponse.getLambdaTestEvent();
      if (lambdaTestEvent != null) {
        awsLambdaExecutionData.setAssertionStatement(lambdaTestEvent.getAssertion());
        if (isNotBlank(lambdaTestEvent.getAssertion())) {
          assertionStatus = (boolean) context.evaluateExpression(lambdaTestEvent.getAssertion(),
              StateExecutionContext.builder().stateExecutionData(awsLambdaExecutionData).build());
        }
      }

      if (!assertionStatus || awsLambdaExecutionData.getStatusCode() < 200
          || awsLambdaExecutionData.getStatusCode() > 299) { // Lambda return non 200 range for failure
        executionStatus = ExecutionStatus.FAILED;
      }
      awsLambdaExecutionData.setAssertionStatus(executionStatus.name());

      updateActivityStatus(activityId, ((ExecutionContextImpl) context).getApp().getUuid(), executionStatus);
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .stateExecutionData(awsLambdaExecutionData)
          .build();

    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public Integer getTimeoutMillis() {
    return Ints.checkedCast(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES));
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private String createActivity(ExecutionContext executionContext) {
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);

    Activity activity = Activity.builder()
                            .applicationName(app.getName())
                            .environmentId(env.getUuid())
                            .environmentName(env.getName())
                            .environmentType(env.getEnvironmentType())
                            .commandName(getName())
                            .type(Type.Verification)
                            .workflowType(executionContext.getWorkflowType())
                            .workflowExecutionName(executionContext.getWorkflowExecutionName())
                            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                            .commandType(getStateType())
                            .workflowExecutionId(executionContext.getWorkflowExecutionId())
                            .workflowId(executionContext.getWorkflowId())
                            .commandUnits(Collections.emptyList())
                            .status(ExecutionStatus.RUNNING)
                            .build();
    activity.setAppId(app.getUuid());
    return activityService.save(activity).getUuid();
  }

  private void updateActivityStatus(String activityId, String appId, ExecutionStatus status) {
    activityService.updateStatus(activityId, appId, status);
  }

  @Override
  @SchemaIgnore
  public ContextElementType getRequiredContextElementType() {
    return ContextElementType.AWS_LAMBDA_FUNCTION;
  }

  public List<LambdaTestEvent> getLambdaTestEvents() {
    return lambdaTestEvents;
  }

  public void setLambdaTestEvents(List<LambdaTestEvent> lambdaTestEvents) {
    this.lambdaTestEvents = lambdaTestEvents;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
