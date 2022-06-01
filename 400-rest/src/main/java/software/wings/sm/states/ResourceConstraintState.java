/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.govern.Switch.unhandled;

import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.RESOURCE_CONSTRAINT_BLOCKED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.RESOURCE_CONSTRAINT_UNBLOCKED_NOTIFICATION;
import static software.wings.sm.states.HoldingScope.PHASE;
import static software.wings.sm.states.HoldingScope.PIPELINE;
import static software.wings.sm.states.HoldingScope.WORKFLOW;
import static software.wings.sm.states.ResourceConstraintState.AcquireMode.ENSURE;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.ResourceConstraint;
import io.harness.context.ContextElementType;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintException;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.tasks.ResponseData;

import software.wings.api.PhaseElement;
import software.wings.api.ResourceConstraintExecutionData;
import software.wings.beans.Application;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationRule;
import software.wings.beans.ResourceConstraintInstance.ResourceConstraintInstanceKeys;
import software.wings.beans.ResourceConstraintNotification;
import software.wings.beans.ResourceConstraintUsage;
import software.wings.beans.ResourceConstraintUsage.ActiveScope;
import software.wings.beans.WorkflowExecution;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@FieldNameConstants(innerTypeName = "ResourceConstraintStateKeys")
public class ResourceConstraintState extends State {
  @Inject @Transient private AppService applicationService;
  @Inject @Transient private ResourceConstraintService resourceConstraintService;
  @Inject @Transient private NotificationSetupService notificationSetupService;
  @Inject @Transient private NotificationService notificationService;
  @Inject @Transient private WingsPersistence wingsPersistence;
  @Inject @Transient private WorkflowNotificationHelper workflowNotificationHelper;
  @Inject @Transient private FeatureFlagService featureFlagService;
  @Inject @Transient private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Getter @Setter private String resourceConstraintId;
  @Getter @Setter private String resourceUnit;

  @Getter @Setter @Min(value = 1) private int permits;

  @Getter @Setter private String holdingScope;

  public enum NotificationEvent { BLOCKED, UNBLOCKED }

  public enum AcquireMode { ACCUMULATE, ENSURE }

  private int acquiredPermits;

  @Getter @Setter private AcquireMode acquireMode;

  @Getter @Setter private List<NotificationEvent> notificationEvents;

  @Getter @Setter private List<String> notificationGroups;

  @Getter @Setter private boolean harnessOwned;

  public ResourceConstraintState(String name) {
    super(name, StateType.RESOURCE_CONSTRAINT.name());
  }

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ASYNC_CALL_TIMEOUT)
  @Override
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    return executeInternal(context);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // TODO: force the resource constraint to final. Not critical the backup job handles this so far.
  }

  @Override
  @SchemaIgnore
  public List<String> getPatternsForRequiredContextElementType() {
    return asList(resourceUnit);
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    String accountId = applicationService.getAccountIdByAppId(context.getAppId());
    final ResourceConstraint resourceConstraint = resourceConstraintService.get(accountId, resourceConstraintId);

    if (isNotEmpty(notificationEvents) && notificationEvents.contains(NotificationEvent.UNBLOCKED)) {
      sendNotification(accountId, context, resourceConstraint, RESOURCE_CONSTRAINT_UNBLOCKED_NOTIFICATION);
    }

    final ExecutionResponseBuilder executionResponseBuilder =
        executionResponseBuilder(resourceConstraint, context.renderExpression(resourceUnit));
    return executionResponseBuilder.build();
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    String accountId = applicationService.getAccountIdByAppId(context.getAppId());
    final ResourceConstraint resourceConstraint = resourceConstraintService.get(accountId, resourceConstraintId);
    final Constraint constraint = resourceConstraintService.createAbstraction(resourceConstraint);

    if (acquireMode == ENSURE) {
      acquiredPermits = alreadyAcquiredPermits(holdingScope, context);
      permits -= acquiredPermits;
    }

    String releaseEntityId = null;
    switch (HoldingScope.valueOf(holdingScope)) {
      case PIPELINE:
        if (featureFlagService.isEnabled(RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED, context.getAccountId())) {
          WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
          String pipelineDeploymentUuid = workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
          if (pipelineDeploymentUuid == null) {
            // for direct workflow executions, the scope should be workflow
            releaseEntityId = ResourceConstraintService.releaseEntityId(context.getWorkflowExecutionId());
            this.setHoldingScope(WORKFLOW.name());
            break;
          }
          releaseEntityId = ResourceConstraintService.releaseEntityId(pipelineDeploymentUuid);
          break;
        }
        throw new InvalidRequestException(String.format("Unhandled holding scope %s", holdingScope));
      case WORKFLOW:
        releaseEntityId = ResourceConstraintService.releaseEntityId(context.getWorkflowExecutionId());
        break;
      case PHASE:
        PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
        if (phaseElement == null) {
          throw new InvalidRequestException(
              "Resource constraint with holding scope 'Phase' cannot be used outside a phase");
        }
        releaseEntityId =
            ResourceConstraintService.releaseEntityId(context.getWorkflowExecutionId(), phaseElement.getPhaseName());
        break;
      default:
        throw new InvalidRequestException(String.format("Unhandled holding scope %s", holdingScope));
    }

    Map<String, Object> constraintContext = new HashMap();
    constraintContext.put(ResourceConstraintInstanceKeys.appId, context.getAppId());
    constraintContext.put(ResourceConstraintInstanceKeys.releaseEntityType, holdingScope);
    constraintContext.put(ResourceConstraintInstanceKeys.releaseEntityId, releaseEntityId);
    constraintContext.put(
        ResourceConstraintInstanceKeys.order, resourceConstraintService.getMaxOrder(resourceConstraintId) + 1);
    constraintContext.put(FeatureName.RESOURCE_CONSTRAINT_MAX_QUEUE.name(),
        featureFlagService.isEnabled(FeatureName.RESOURCE_CONSTRAINT_MAX_QUEUE, accountId));

    ConstraintUnit renderedResourceUnit = new ConstraintUnit(context.renderExpression(resourceUnit));

    final ExecutionResponseBuilder executionResponseBuilder =
        executionResponseBuilder(resourceConstraint, renderedResourceUnit.getValue());

    if (ExpressionEvaluator.matchesVariablePattern(renderedResourceUnit.getValue())) {
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.SKIPPED)
          .errorMessage("Cannot Resolve Constraint Unit " + renderedResourceUnit.getValue() + " Skipping...")
          .build();
    }

    String consumerId = generateUuid();
    try {
      if (permits <= 0) {
        return executionResponseBuilder.executionStatus(ExecutionStatus.SUCCESS).build();
      }
      final Consumer.State state = constraint.registerConsumer(renderedResourceUnit, new ConsumerId(consumerId),
          permits, constraintContext, resourceConstraintService.getRegistry());

      // TODO: Write unit test for this on removing FF
      if (featureFlagService.isEnabled(FeatureName.RESOURCE_CONSTRAINT_MAX_QUEUE, accountId)
          && state == Consumer.State.REJECTED) {
        return executionResponseBuilder.executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Limit exceeded. Only " + Constraint.MAX_CONSUMERS_WAITING_FOR_RESOURCE
                + " executions can be queued for acquiring the resource lock per target infrastructure at a time")
            .build();
      }

      if (state == Consumer.State.ACTIVE) {
        return executionResponseBuilder.executionStatus(ExecutionStatus.SUCCESS).build();
      }
    } catch (ConstraintException exception) {
      return executionResponseBuilder.executionStatus(ExecutionStatus.FAILED)
          .errorMessage(exception.getMessage())
          .build();
    }

    if (isNotEmpty(notificationEvents) && notificationEvents.contains(NotificationEvent.BLOCKED)) {
      sendNotification(accountId, context, resourceConstraint, RESOURCE_CONSTRAINT_BLOCKED_NOTIFICATION);
    }

    return executionResponseBuilder.async(true).correlationIds(asList(consumerId)).build();
  }

  private void sendNotification(
      String accountId, ExecutionContext context, ResourceConstraint resourceConstraint, NotificationMessageType type) {
    if (isEmpty(notificationGroups)) {
      return;
    }

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    String envId = workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();
    final String workflowUrl = workflowNotificationHelper.calculateWorkflowUrl(
        context.getWorkflowExecutionId(), context.getOrchestrationWorkflowType(), accountId, context.getAppId(), envId);

    final ResourceConstraintNotification notification = new ResourceConstraintNotification();
    notification.setNotificationTemplateId(type.name());

    final Application application = applicationService.get(context.getAppId());
    Map<String, String> variables = new HashMap();
    variables.put("RESOURCE_CONSTRAINT_NAME", resourceConstraint.getName());
    variables.put("WORKFLOW_NAME", context.getWorkflowExecutionName());
    variables.put("WORKFLOW_URL", workflowUrl);
    variables.put("APP_NAME", application.getName());

    if (RESOURCE_CONSTRAINT_BLOCKED_NOTIFICATION == type) {
      final List<ResourceConstraintUsage> usages =
          resourceConstraintService.usage(accountId, asList(resourceConstraint.getUuid()));

      // Note that there is a race between determining to block the state and the time we obtain the blocking items
      // At least in theory the usages being empty is possible.
      if (isNotEmpty(usages)) {
        final ResourceConstraintUsage resourceConstraintUsage = usages.iterator().next();
        final ActiveScope activeScope = resourceConstraintUsage.getActiveScopes().iterator().next();

        if (WORKFLOW.name().equals(activeScope.getReleaseEntityType())) {
          final WorkflowExecution workflowExecution =
              wingsPersistence.get(WorkflowExecution.class, activeScope.getReleaseEntityId());

          if (workflowExecution != null) {
            final String blockingWorkflowUrl = workflowNotificationHelper.calculateWorkflowUrl(
                workflowExecution.getUuid(), workflowExecution.getOrchestrationType(), accountId,
                workflowExecution.getAppId(), workflowExecution.getEnvId());

            variables.put("BLOCKING_WORKFLOW_NAME", activeScope.getReleaseEntityName());
            variables.put("BLOCKING_WORKFLOW_URL", blockingWorkflowUrl);
          }
        } else {
          unhandled(activeScope.getReleaseEntityType());
        }
      }
    }

    notification.setAccountId(accountId);
    notification.setAppId(context.getAppId());
    notification.setNotificationTemplateVariables(variables);

    final List<NotificationGroup> notificationGroupObjects =
        notificationSetupService.readNotificationGroups(accountId, notificationGroups);

    final NotificationRule notificationRule =
        aNotificationRule().withNotificationGroups(notificationGroupObjects).build();

    notificationService.sendNotificationAsync(notification, asList(notificationRule));
  }

  @VisibleForTesting
  ExecutionResponseBuilder executionResponseBuilder(ResourceConstraint resourceConstraint, String resourceUnit) {
    ResourceConstraintExecutionData stateExecutionData = new ResourceConstraintExecutionData();
    stateExecutionData.setResourceConstraintName(resourceConstraint.getName());
    stateExecutionData.setHarnessOwned(resourceConstraint.isHarnessOwned());
    stateExecutionData.setResourceConstraintCapacity(resourceConstraint.getCapacity());
    stateExecutionData.setUnit(resourceUnit);
    stateExecutionData.setUsage(permits);
    if (acquireMode == ENSURE) {
      stateExecutionData.setAlreadyAcquiredPermits(acquiredPermits);
    }
    return ExecutionResponse.builder().stateExecutionData(stateExecutionData);
  }

  private String getPipelineDeploymentIdIfExists(WorkflowStandardParams workflowStandardParams) {
    if (workflowStandardParams != null && workflowStandardParams.getWorkflowElement() != null) {
      return workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
    }
    return null;
  }

  int alreadyAcquiredPermits(String holdingScope, ExecutionContext executionContext) {
    int currentlyAcquiredPermits = 0;
    String releaseEntityId;
    String parentReleaseEntityId;
    String appId = executionContext.fetchRequiredApp().getUuid();
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    String pipelineDeploymentUuid = getPipelineDeploymentIdIfExists(workflowStandardParams);

    switch (HoldingScope.valueOf(holdingScope)) {
      case PIPELINE:
        if (featureFlagService.isEnabled(RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED, executionContext.getAccountId())) {
          releaseEntityId = ResourceConstraintService.releaseEntityId(pipelineDeploymentUuid);
          currentlyAcquiredPermits +=
              resourceConstraintService.getAllCurrentlyAcquiredPermits(holdingScope, releaseEntityId, appId);
          return currentlyAcquiredPermits;
        }
        throw new InvalidRequestException(String.format("Unhandled holding scope %s", holdingScope));
      case WORKFLOW:
        releaseEntityId = ResourceConstraintService.releaseEntityId(executionContext.getWorkflowExecutionId());
        currentlyAcquiredPermits +=
            resourceConstraintService.getAllCurrentlyAcquiredPermits(holdingScope, releaseEntityId, appId);
        return currentlyAcquiredPermits;
      case PHASE:
        int pipelineCurrentlyAcquiredPermits = 0;
        PhaseElement phaseElement =
            executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
        parentReleaseEntityId = ResourceConstraintService.releaseEntityId(executionContext.getWorkflowExecutionId());
        releaseEntityId = ResourceConstraintService.releaseEntityId(
            executionContext.getWorkflowExecutionId(), phaseElement.getPhaseName());
        if (pipelineDeploymentUuid != null) {
          pipelineCurrentlyAcquiredPermits =
              resourceConstraintService.getAllCurrentlyAcquiredPermits(PIPELINE.name(), pipelineDeploymentUuid, appId);
        }
        return resourceConstraintService.getAllCurrentlyAcquiredPermits(PHASE.name(), releaseEntityId, appId)
            + resourceConstraintService.getAllCurrentlyAcquiredPermits(WORKFLOW.name(), parentReleaseEntityId, appId)
            + pipelineCurrentlyAcquiredPermits;
      default:
        throw new InvalidRequestException(String.format("Unhandled holding scope %s", holdingScope));
    }
  }
}
