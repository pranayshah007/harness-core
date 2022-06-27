/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.context.ContextElementType.CLOUD_FORMATION_ROLLBACK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.infrastructure.CloudFormationRollbackConfig;
import software.wings.beans.infrastructure.CloudFormationRollbackConfig.CloudFormationRollbackConfigKeys;
import software.wings.helpers.ext.cloudformation.CloudFormationCompletionFlag;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest.CloudFormationCreateStackRequestBuilder;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;

import com.amazonaws.services.cloudformation.model.StackStatus;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class CloudFormationRollbackStackState extends CloudFormationState {
  private static final String COMMAND_UNIT = "Rollback Stack";

  public CloudFormationRollbackStackState(String name) {
    super(name, StateType.CLOUD_FORMATION_ROLLBACK_STACK.name());
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
  @SchemaIgnore
  public String getProvisionerId() {
    return super.getProvisionerId();
  }

  @Override
  @SchemaIgnore
  public String getRegion() {
    return super.getRegion();
  }

  @Override
  @SchemaIgnore
  public String getAwsConfigId() {
    return super.getAwsConfigId();
  }

  @Override
  @SchemaIgnore
  public List<NameValuePair> getVariables() {
    return super.getVariables();
  }

  @Override
  @SchemaIgnore
  public String getCustomStackName() {
    return super.getCustomStackName();
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  @SchemaIgnore
  public boolean isUseCustomStackName() {
    return super.isUseCustomStackName();
  }

  @Override
  protected ExecutionResponse buildAndQueueDelegateTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId) {
    throw new InvalidRequestException("Method should not be called");
  }

  @Override
  protected List<CloudFormationElement> handleResponse(
      CloudFormationCommandResponse commandResponse, ExecutionContext context) {
    Optional<CloudFormationRollbackInfoElement> rollbackElement = getRollbackElement(context);
    if (!rollbackElement.isPresent()) {
      return emptyList();
    }
    CloudFormationRollbackInfoElement stackElement = rollbackElement.get();
    if (stackElement.isStackExisted()) {
      updateInfraMappings(commandResponse, context, stackElement.getProvisionerId());
      saveCloudFormationRollbackConfig(((CloudFormationCreateStackResponse) commandResponse).getRollbackInfo(),
          (ExecutionContextImpl) context, stackElement.getAwsConfigId());
    } else {
      clearRollbackConfig((ExecutionContextImpl) context);
    }
    return emptyList();
  }

  private Optional<CloudFormationRollbackInfoElement> getRollbackElement(ExecutionContext context) {
    List<CloudFormationRollbackInfoElement> allRollbackElements =
        context.getContextElementList(CLOUD_FORMATION_ROLLBACK);
    if (isNotEmpty(allRollbackElements)) {
      return allRollbackElements.stream()
          .filter(element -> element.getProvisionerId().equals(provisionerId))
          .findFirst();
    }
    return Optional.empty();
  }

  /**
   * We are re-designing the CF rollback as a part of the change for CD-3461.
   * As a part of this change, we need to handle the case where the first deploy after CD-3461 is rolled out fails.
   * For that case, we will need to revert to use of context element and not DB stored config.
   * We will eventually need to ref-factor this code to not do that, which can be picked up after
   * about a month. For details see CD-4767.
   */
  private ExecutionResponse executeInternalWithSavedElement(ExecutionContext context, String activityId) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String entityId = getStackNameSuffix(executionContext, provisionerId);

    Query<CloudFormationRollbackConfig> getRollbackConfig =
        wingsPersistence.createQuery(CloudFormationRollbackConfig.class)
            .filter(CloudFormationRollbackConfigKeys.appId, context.getAppId())
            .filter(CloudFormationRollbackConfigKeys.entityId, entityId);
    if (featureFlagService.isEnabled(FeatureName.CF_ROLLBACK_CONFIG_FILTER, context.getAccountId())) {
      getRollbackConfig.filter(CloudFormationRollbackConfigKeys.awsConfigId, fetchResolvedAwsConfigId(context));
    }
    getRollbackConfig.order(Sort.descending(CloudFormationRollbackConfigKeys.createdAt)).fetch();
    try (HIterator<CloudFormationRollbackConfig> configIterator = new HIterator(getRollbackConfig.fetch())) {
      if (!configIterator.hasNext()) {
        /**
         * No config found.
         * Revert to stored element
         */
        return null;
      }

      CloudFormationRollbackConfig configParameter = null;
      CloudFormationRollbackConfig currentConfig = null;
      while (configIterator.hasNext()) {
        configParameter = configIterator.next();

        if (configParameter.getWorkflowExecutionId().equals(context.getWorkflowExecutionId())) {
          if (currentConfig == null) {
            currentConfig = configParameter;
          }
        } else {
          break;
        }
      }

      if (configParameter == currentConfig) {
        /**
         * We only found the current execution.
         * This is the special case we are to handle, the first deployment
         * after rollout of CD-3461 failed.
         * Revert to stored element.
         */
        return null;
      }

      List<NameValuePair> allVariables = configParameter.getVariables();
      Map<String, String> textVariables = null;
      Map<String, EncryptedDataDetail> encryptedTextVariables = null;
      if (isNotEmpty(allVariables)) {
        textVariables = infrastructureProvisionerService.extractTextVariables(allVariables, context);
        encryptedTextVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
            allVariables, context.getAppId(), executionContext.getWorkflowExecutionId());
      }

      /**
       * For now, we will be handling ONLY the case where we need to update the stack.
       * The deletion of the stack case would also be handled by the context element.
       * For details, see CD-4767
       */
      AwsConfig awsConfig = getAwsConfig(configParameter.getAwsConfigId());
      AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context,awsConfig);
      String roleArnRendered = executionContext.renderExpression(configParameter.getCloudFormationRoleArn());
      CloudFormationCreateStackRequestBuilder builder = CloudFormationCreateStackRequest.builder().awsConfig(awsConfig);
      if (CLOUDFORMATION_STACK_CREATE_URL.equals(configParameter.getCreateType())) {
        builder.createType(CLOUDFORMATION_STACK_CREATE_URL);
        builder.data(configParameter.getUrl());
      } else {
        // This handles both the Git case and template body. We don't need to checkout again.
        builder.createType(CLOUDFORMATION_STACK_CREATE_BODY);
        builder.data(configParameter.getBody());
      }

      if (configParameter.isSkipBasedOnStackStatus() == false
          || isEmpty(configParameter.getStackStatusesToMarkAsSuccess())) {
        builder.stackStatusesToMarkAsSuccess(new ArrayList<>());
      } else {
        List<String> stackStatusesToMarkAsSuccessRendered =
            configParameter.getStackStatusesToMarkAsSuccess()
                .stream()
                .map(status -> executionContext.renderExpression(status))
                .collect(Collectors.toList());
        builder.stackStatusesToMarkAsSuccess(stackStatusesToMarkAsSuccessRendered.stream()
                                                 .map(status -> StackStatus.fromValue(status))
                                                 .collect(Collectors.toList()));
      }

      CloudFormationCreateStackRequest request = builder.stackNameSuffix(configParameter.getEntityId())
                                                     .customStackName(configParameter.getCustomStackName())
                                                     .region(configParameter.getRegion())
                                                     .commandType(CloudFormationCommandType.CREATE_STACK)
                                                     .accountId(context.getAccountId())
                                                     .appId(context.getAppId())
                                                     .cloudFormationRoleArn(roleArnRendered)
                                                     .commandName(mainCommandUnit())
                                                     .activityId(activityId)
                                                     .variables(textVariables)
                                                     .encryptedVariables(encryptedTextVariables)
                                                     .build();
      setTimeOutOnRequest(request);
      DelegateTask delegateTask =
          DelegateTask.builder()
              .accountId(executionContext.getAccountId())
              .waitId(activityId)
              .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getAppId())
              .data(TaskData.builder()
                        .async(true)
                        .taskType(CLOUD_FORMATION_TASK.name())
                        .parameters(new Object[] {request,
                            secretManager.getEncryptionDetails(
                                awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId())})
                        .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                        .build())
              .build();
      String delegateTaskId = delegateService.queueTask(delegateTask);
      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(Collections.singletonList(activityId))
          .delegateTaskId(delegateTaskId)
          .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
          .build();
    }
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    CloudFormationCompletionFlag cloudFormationCompletionFlag = getCloudFormationCompletionFlag(context);
    if (cloudFormationCompletionFlag == null || !cloudFormationCompletionFlag.isCreateStackCompleted()) {
      return ExecutionResponse.builder().executionStatus(SKIPPED).errorMessage("Skipping rollback").build();
    }

    ExecutionResponse executionResponse = executeInternalWithSavedElement(context, activityId);
    if (executionResponse != null) {
      log.info("Cloud Formation Rollback. Rollback done via DB stored config for execution: [%s]",
          context.getWorkflowExecutionId());
      return executionResponse;
    }

    log.warn("Cloud Formation Rollback. Reverting to pre saved element method for execution: [{}]",
        context.getWorkflowExecutionId());

    Optional<CloudFormationRollbackInfoElement> stackElementOptional = getRollbackElement(context);
    if (!stackElementOptional.isPresent()) {
      return ExecutionResponse.builder()
          .executionStatus(SUCCESS)
          .errorMessage("No cloud formation rollback state found")
          .build();
    }
    CloudFormationRollbackInfoElement stackElement = stackElementOptional.get();
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    AwsConfig awsConfig = getAwsConfig(stackElement.getAwsConfigId());
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context,awsConfig);
    DelegateTask delegateTask;
    if (!stackElement.isStackExisted()) {
      CloudFormationDeleteStackRequest request =
          CloudFormationDeleteStackRequest.builder()
              .region(stackElement.getRegion())
              .stackNameSuffix(stackElement.getStackNameSuffix())
              .customStackName(stackElement.getCustomStackName())
              .commandType(CloudFormationCommandType.DELETE_STACK)
              .accountId(executionContext.getApp().getAccountId())
              .cloudFormationRoleArn(executionContext.renderExpression(getCloudFormationRoleArn()))
              .appId(executionContext.getApp().getUuid())
              .activityId(activityId)
              .commandName(mainCommandUnit())
              .awsConfig(awsConfig)
              .build();

      setTimeOutOnRequest(request);

      delegateTask =
          DelegateTask.builder()
              .accountId(executionContext.getApp().getAccountId())
              .waitId(activityId)
              .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
              .data(TaskData.builder()
                        .async(true)
                        .taskType(CLOUD_FORMATION_TASK.name())
                        .parameters(new Object[] {request,
                            secretManager.getEncryptionDetails(
                                awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId())})
                        .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                        .build())
              .build();
    } else {
      CloudFormationCreateStackRequestBuilder builder = CloudFormationCreateStackRequest.builder();
      builder.createType(CLOUDFORMATION_STACK_CREATE_BODY).data(stackElement.getOldStackBody());
      builder.stackNameSuffix(stackElement.getStackNameSuffix())
          .customStackName(stackElement.getCustomStackName())
          .region(stackElement.getRegion())
          .commandType(CloudFormationCommandType.CREATE_STACK)
          .accountId(executionContext.fetchRequiredApp().getAccountId())
          .cloudFormationRoleArn(executionContext.renderExpression(getCloudFormationRoleArn()))
          .appId(executionContext.fetchRequiredApp().getUuid())
          .activityId(activityId)
          .commandName(mainCommandUnit())
          .variables(stackElement.getOldStackParameters())
          .awsConfig(awsConfig);
      if (stackElement.isSkipBasedOnStackStatus() == false || isEmpty(stackElement.getStackStatusesToMarkAsSuccess())) {
        builder.stackStatusesToMarkAsSuccess(new ArrayList<>());
      } else {
        List<String> stackStatusesToMarkAsSuccessRendered =
            stackElement.getStackStatusesToMarkAsSuccess()
                .stream()
                .map(status -> executionContext.renderExpression(status))
                .collect(Collectors.toList());
        builder.stackStatusesToMarkAsSuccess(stackStatusesToMarkAsSuccessRendered.stream()
                                                 .map(status -> StackStatus.fromValue(status))
                                                 .collect(Collectors.toList()));
      }
      CloudFormationCreateStackRequest request = builder.build();
      setTimeOutOnRequest(request);

      notNullCheck("Application cannot be null", context.getApp());

      delegateTask =
          DelegateTask.builder()
              .accountId(executionContext.getApp().getAccountId())
              .waitId(activityId)
              .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
              .data(TaskData.builder()
                        .async(true)
                        .taskType(CLOUD_FORMATION_TASK.name())
                        .parameters(new Object[] {request,
                            secretManager.getEncryptionDetails(
                                awsConfig, GLOBAL_APP_ID, executionContext.getWorkflowExecutionId())})
                        .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                        .build())
              .build();
    }
    String delegateTaskId = delegateService.queueTask(delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }
}
