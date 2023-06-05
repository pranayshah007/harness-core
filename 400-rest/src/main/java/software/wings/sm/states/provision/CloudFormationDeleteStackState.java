/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
public class CloudFormationDeleteStackState extends CloudFormationState {
  private static final String COMMAND_UNIT = "Delete Stack";

  public CloudFormationDeleteStackState(String name) {
    super(name, StateType.CLOUD_FORMATION_DELETE_STACK.name());
  }

  @Override
  protected List<String> commandUnits(CloudFormationInfrastructureProvisioner provisioner) {
    return Collections.singletonList(mainCommandUnit());
  }

  @Override
  protected String mainCommandUnit() {
    return COMMAND_UNIT;
  }

  @Override
  protected ExecutionResponse buildAndQueueDelegateTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId) {
    notNullCheck("Application cannot be null", executionContext.getApp());

    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, executionContext.getAccountId());

    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .region(region)
            .stackNameSuffix(getStackNameSuffix(executionContext, provisioner.getUuid()))
            .customStackName(
                useCustomStackName ? executionContext.renderExpression(customStackName) : StringUtils.EMPTY)
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .cloudFormationRoleArn(executionContext.renderExpression(getCloudFormationRoleArn()))
            .accountId(executionContext.getApp().getAccountId())
            .appId(executionContext.getApp().getUuid())
            .activityId(activityId)
            .commandName(mainCommandUnit())
            .awsConfig(awsConfig)
            .timeoutSupported(isTimeoutFailureSupported)
            .skipWaitForResources(
                featureFlagService.isEnabled(CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES, executionContext.getAccountId()))
            .build();
    setTimeOutOnRequest(request);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(executionContext.getApp().getAccountId())
            .waitId(activityId)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("CloudFormation delete stack task execution")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(CLOUD_FORMATION_TASK.name())
                      .parameters(new Object[] {request,
                          secretManager.getEncryptionDetails(
                              awsConfig, GLOBAL_APP_ID, executionContext.getWorkflowExecutionId())})
                      .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                      .build())
            .build();
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(executionContext, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  @Override
  protected List<CloudFormationElement> handleResponse(
      CloudFormationCommandResponse commandResponse, ExecutionContext context) {
    clearRollbackConfig((ExecutionContextImpl) context);
    return emptyList();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
