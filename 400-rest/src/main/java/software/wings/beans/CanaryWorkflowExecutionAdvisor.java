/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.ABORT_ALL;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_ON_APPROVAL;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.CONSIDER_ORIGINAL_STATE_VERSION;
import static io.harness.beans.FeatureName.ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES;
import static io.harness.beans.FeatureName.LOG_APP_DEFAULTS;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.beans.FeatureName.SPG_DISABLE_EXPIRING_TO_MANUAL_INTERVENTION_CANDIDATE;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.beans.RepairActionCode.MANUAL_INTERVENTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;
import static software.wings.common.WorkflowConstants.PHASE_NAME_PREFIX;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_PREFIX;
import static software.wings.sm.ExecutionEventAdvice.ExecutionEventAdviceBuilder.anExecutionEventAdvice;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.FORK;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.REPEAT;
import static software.wings.sm.StateType.SUB_WORKFLOW;
import static software.wings.sm.rollback.RollbackStateMachineGenerator.STAGING_PHASE_NAME;
import static software.wings.sm.rollback.RollbackStateMachineGenerator.WHITE_SPACE;

import static java.util.Collections.disjoint;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.RepairActionCode;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;

import software.wings.api.PhaseElement;
import software.wings.beans.FailureStrategy.FailureStrategyBuilder;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionEvent;
import software.wings.sm.ExecutionEventAdvice;
import software.wings.sm.ExecutionEventAdvisor;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.PhaseStepSubWorkflow;
import software.wings.sm.states.PhaseSubWorkflow;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;

/**
 * Created by rishi on 1/24/17.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.impl.instance.InstanceHelper")
public class CanaryWorkflowExecutionAdvisor implements ExecutionEventAdvisor {
  public static final String ROLLBACK_PROVISIONERS = "Rollback Provisioners";
  public static final String ROLLBACK_PROVISIONERS_REVERSE = "Rollback Provisioners Reverse";
  private static final String ROLLING_PHASE_PREFIX = "Rolling Phase ";
  public static final ExecutionInterruptType DEFAULT_ACTION_AFTER_TIMEOUT = ExecutionInterruptType.END_EXECUTION;
  public static final long DEFAULT_TIMEOUT = 1209600000L; // 14days
  private static final String DEBUG_APP_DEFAULTS = "DEBUG_APP_DEFAULTS";
  @Inject @Transient private transient PipelineService pipelineService;

  @Inject @Transient private transient WorkflowExecutionService workflowExecutionService;

  @Inject @Transient private transient WorkflowService workflowService;

  @Inject @Transient private transient WorkflowNotificationHelper workflowNotificationHelper;

  @Inject @Transient private transient WorkflowServiceHelper workflowServiceHelper;

  @Inject @Transient private transient ExecutionInterruptManager executionInterruptManager;

  @Inject @Transient private transient InstanceHelper instanceHelper;

  @Inject @Transient private transient InfrastructureMappingService infrastructureMappingService;

  @Inject @Transient private transient StateExecutionService stateExecutionService;

  @Inject @Transient private transient FeatureFlagService featureFlagService;

  @Inject @Transient private transient WingsPersistence wingsPersistence;

  @Override
  @SuppressWarnings("PMD")
  public ExecutionEventAdvice onExecutionEvent(ExecutionEvent executionEvent) {
    State state = executionEvent.getState();
    ExecutionContextImpl context = executionEvent.getContext();
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    boolean isTimeoutSupportOnWFLevelEnabled =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());
    try (AutoLogContext ignore = context.autoLogContext()) {
      log.info("Calculating execution advice for workflow");
      List<ExecutionInterrupt> executionInterrupts =
          executionInterruptManager.checkForExecutionInterrupt(context.getAppId(), context.getWorkflowExecutionId());
      if (executionInterrupts != null
          && executionInterrupts.stream().anyMatch(ex -> ex.getExecutionInterruptType() == ABORT_ALL)) {
        log.info("Returning advise for ABORT_ALL");
        return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.END_EXECUTION).build();
      }

      Workflow workflow;
      if (featureFlagService.isEnabled(CONSIDER_ORIGINAL_STATE_VERSION, context.getAccountId())) {
        Integer originalStateVersion = context.getStateMachine().getOriginVersion();
        if (originalStateVersion == null) {
          workflow = workflowService.readWorkflow(workflowExecution.getAppId(), workflowExecution.getWorkflowId());
        } else {
          workflow = workflowService.readWorkflow(
              workflowExecution.getAppId(), workflowExecution.getWorkflowId(), originalStateVersion);
        }
      } else {
        workflow = workflowService.readWorkflow(workflowExecution.getAppId(), workflowExecution.getWorkflowId());
      }

      CanaryOrchestrationWorkflow orchestrationWorkflow =
          (CanaryOrchestrationWorkflow) findOrchestrationWorkflow(workflow, workflowExecution);

      boolean rolling = false;
      if (stateExecutionInstance != null && stateExecutionInstance.getOrchestrationWorkflowType() == ROLLING
          && !workflowServiceHelper.isExecutionForK8sV2Service(workflowExecution)) {
        rolling = true;
      }

      if (rolling && state.getStateType().equals(StateType.PHASE_STEP.name())
          && state.getName().equals(PRE_DEPLOYMENT.getDefaultName())
          && executionEvent.getExecutionStatus() == SUCCESS) {
        // ready for rolling deploy

        if (orchestrationWorkflow == null || isEmpty(orchestrationWorkflow.getWorkflowPhases())) {
          return null;
        }
        WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
        String phaseName = workflowPhase.getName();
        String displayName = ROLLING_PHASE_PREFIX + 1;
        if (workflowExecutionService.checkIfOnDemand(context.getAppId(), context.getWorkflowExecutionId())) {
          phaseName = STAGING_PHASE_NAME + WHITE_SPACE + phaseName;
          displayName = STAGING_PHASE_NAME + WHITE_SPACE + displayName;
        }
        return anExecutionEventAdvice()
            .withExecutionInterruptType(ExecutionInterruptType.NEXT_STEP)
            .withNextStateName(phaseName)
            .withNextChildStateMachineId(stateExecutionInstance.getChildStateMachineId())
            .withNextStateDisplayName(displayName)
            .build();
      }

      PhaseSubWorkflow phaseSubWorkflow = null;
      PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);

      boolean rollbackProvisioners = false;
      if (state.getStateType().equals(StateType.PHASE.name()) && state instanceof PhaseSubWorkflow) {
        phaseSubWorkflow = (PhaseSubWorkflow) state;

        workflowNotificationHelper.sendWorkflowPhaseStatusChangeNotification(
            context, executionEvent.getExecutionStatus(), phaseSubWorkflow);

        if (!phaseSubWorkflow.isRollback() && executionEvent.getExecutionStatus() == SUCCESS) {
          if (phaseSubWorkflow.getName().startsWith(STAGING_PHASE_NAME)
              && executionEvent.getExecutionStatus() == SUCCESS) {
            return phaseSubWorkflowOnDemandRollbackAdvice(
                orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance, rolling);
          }

          if (!rolling) {
            return null;
          }

          if (isExecutionHostsPresent(context)) {
            return null;
          }

          List<ServiceInstance> hostExclusionList = stateExecutionService.getHostExclusionList(
              context.getStateExecutionInstance(), phaseElement, context.fetchInfraMappingId());

          String infraMappingId;
          if (context.fetchInfraMappingId() == null) {
            List<InfrastructureMapping> resolvedInfraMappings;
            resolvedInfraMappings = infrastructureMappingService.getInfraStructureMappingsByUuids(
                workflow.getAppId(), workflowExecution.getInfraMappingIds());
            if (isEmpty(resolvedInfraMappings)) {
              return anExecutionEventAdvice()
                  .withExecutionInterruptType(ExecutionInterruptType.NEXT_STEP)
                  .withNextStateName(((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow())
                                         .getPostDeploymentSteps()
                                         .getName())
                  .withNextChildStateMachineId(stateExecutionInstance.getChildStateMachineId())
                  .build();
            }
            infraMappingId = resolvedInfraMappings.get(0).getUuid();
          } else {
            infraMappingId = context.fetchInfraMappingId();
          }
          ServiceInstanceSelectionParams.Builder selectionParams =
              aServiceInstanceSelectionParams().withExcludedServiceInstanceIds(
                  hostExclusionList.stream().map(ServiceInstance::getUuid).distinct().collect(toList()));
          List<ServiceInstance> serviceInstances =
              infrastructureMappingService.selectServiceInstances(context.getAppId(), infraMappingId,
                  context.getWorkflowExecutionId(), selectionParams.withCount(1).build());

          if (isEmpty(serviceInstances)) {
            return null;
          }
          return anExecutionEventAdvice()
              .withExecutionInterruptType(ExecutionInterruptType.NEXT_STEP)
              .withNextStateName(stateExecutionInstance.getStateName())
              .withNextChildStateMachineId(stateExecutionInstance.getChildStateMachineId())
              .withNextStateDisplayName(computeDisplayName(stateExecutionInstance))
              .build();
        }

        // nothing to do for regular phase with non-error
        if (!phaseSubWorkflow.isRollback() && !ExecutionStatus.isNegativeStatus(executionEvent.getExecutionStatus())) {
          return null;
        }

        // nothing to do for rollback phase that got some error
        if (phaseSubWorkflow.isRollback() && executionEvent.getExecutionStatus() != SUCCESS) {
          return null;
        }

      } else if (state.getStateType().equals(StateType.PHASE_STEP.name()) && state instanceof PhaseStepSubWorkflow
          && ((PhaseStepSubWorkflow) state).getPhaseStepType() == PhaseStepType.ROLLBACK_PROVISIONERS
          && executionEvent.getExecutionStatus() == SUCCESS) {
        rollbackProvisioners = true;

      } else if (state.getStateType().equals(StateType.PHASE_STEP.name()) && state instanceof PhaseStepSubWorkflow
          && ((PhaseStepSubWorkflow) state).getPhaseStepType() == PRE_DEPLOYMENT
          && executionEvent.getExecutionStatus() == FAILED) {
        if (featureFlagService.isEnabled(
                FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, executionEvent.getContext().getAccountId())) {
          if (workflowExecution.isRollbackProvisionerAfterPhases()) {
            return getRollbackProvisionerAdviceIfNeeded(
                orchestrationWorkflow.getPreDeploymentSteps(), ROLLBACK_PROVISIONERS_REVERSE);
          }
        }
        return getRollbackProvisionerAdviceIfNeeded(
            orchestrationWorkflow.getPreDeploymentSteps(), ROLLBACK_PROVISIONERS);
      } else if (executionEvent.getExecutionStatus() == STARTING) {
        PhaseStep phaseStep = findPhaseStep(orchestrationWorkflow, phaseElement, state);
        if (featureFlagService.isEnabled(
                SPG_DISABLE_EXPIRING_TO_MANUAL_INTERVENTION_CANDIDATE, context.getAccountId())) {
          if (state.getParentId() != null && isNotEmpty(phaseStep.getFailureStrategies())) {
            for (FailureStrategy failureStrategy : phaseStep.getFailureStrategies()) {
              if (failureStrategy != null && failureStrategy.getRepairActionCode() == MANUAL_INTERVENTION
                  && !state.getStateType().equals(APPROVAL.getType())
                  && failureStrategy.getFailureTypes().contains(FailureType.TIMEOUT_ERROR)) {
                wingsPersistence.updateField(StateExecutionInstance.class, stateExecutionInstance.getUuid(),
                    StateExecutionInstanceKeys.manualInterventionCandidate, true);
              }
            }
          }
        }

        return shouldSkipStep(context, phaseStep, state, featureFlagService);
      } else if (!(executionEvent.getExecutionStatus() == FAILED || executionEvent.getExecutionStatus() == ERROR
                     || (featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, context.getAccountId())
                         && executionEvent.getExecutionStatus() == EXPIRED))) {
        return null;
      }

      if (phaseSubWorkflow == null && executionInterrupts != null
          && executionInterrupts.stream().anyMatch(ex
              -> ex.getExecutionInterruptType().equals(ROLLBACK)
                  || ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES)
                  || ex.getExecutionInterruptType().equals(ROLLBACK_ON_APPROVAL)
                  || ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL))
          && !rollbackProvisioners) {
        return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.END_EXECUTION).build();
      }

      if (workflowExecution.getWorkflowType() != WorkflowType.ORCHESTRATION) {
        return null;
      }

      if (orchestrationWorkflow == null || orchestrationWorkflow.getRollbackWorkflowPhaseIdMap() == null) {
        return null;
      }
      if (phaseSubWorkflow != null && executionInterrupts != null
          && executionInterrupts.stream().anyMatch(ex
              -> ex.getExecutionInterruptType() == ROLLBACK
                  || ex.getExecutionInterruptType().equals(ROLLBACK_ON_APPROVAL))) {
        return phaseSubWorkflowAdvice(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
      } else if (phaseSubWorkflow != null && executionInterrupts != null
          && executionInterrupts.stream().anyMatch(ex
              -> ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES)
                  || ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL))) {
        /*
        Handle execution interrupt when failure strategy is configured as
        <ROLLBACK_PROVISIONER_AFTER_PHASES> action after timeout in Manual Intervention failure strategy
         */
        if (featureFlagService.isEnabled(FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, context.getAccountId())) {
          return phaseSubWorkflowAdviceWhenRollbackProvisionersAfterPhases(
              orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
        } else {
          /*
          We need to fail the workflow execution when failure strategy is ROLLBACK_PROVISIONER_AFTER_PHASES but Feature
          Flag is not enabled. This can happen if customer disables the feature after configuring the workflow It is
          better to fail than fallback to any other default failur strategy to minimize ambiguity/random workflow
          behaviors.
           */
          throw new InvalidRequestException(
              "Rollback Provisioner after Phases not supported as ROLLBACK_PROVISIONER_AFTER_PHASES feature flag is not enabled",
              WingsException.USER);
        }
      } else if (rollbackProvisioners) {
        /*
        isRollbackProvisionerAfterPhases flag in stateExecutionInstance confirms that rollback has happened under the
        failure strategy <ROLLBACK_PROVISIONER_AFTER_PHASES> Execution Interrupt <ROLLBACK_PROVISIONER_AFTER_PHASES>
        here handles the action after retry scenario for Retry failure strategy
         */
        if (stateExecutionInstance.isRollbackProvisionerAfterPhases()
            || (executionInterrupts != null
                && executionInterrupts.stream().anyMatch(ex
                    -> ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES)
                        || ex.getExecutionInterruptType().equals(ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL)))) {
          if (featureFlagService.isEnabled(FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, context.getAccountId())) {
            // All Done
            return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
          } else {
            // We need to fail the workflow execution when failure strategy is ROLLBACK_PROVISIONER_AFTER_PHASES but
            // Feature Flag is not enabled.
            throw new InvalidRequestException(
                "Rollback Provisioner after Phases not supported as ROLLBACK_PROVISIONER_AFTER_PHASES feature flag is not enabled",
                WingsException.USER);
          }
        }

        List<String> phaseNames =
            orchestrationWorkflow.getWorkflowPhases().stream().map(WorkflowPhase::getName).collect(toList());

        String lastExecutedPhaseName = null;
        for (String phaseName : phaseNames) {
          if (stateExecutionInstance.getStateExecutionMap().containsKey(phaseName)) {
            lastExecutedPhaseName = phaseName;
          } else {
            break;
          }
        }

        if (lastExecutedPhaseName == null) {
          return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
        }

        String finalLastExecutedPhaseName = lastExecutedPhaseName;
        Optional<WorkflowPhase> lastExecutedPhase =
            orchestrationWorkflow.getWorkflowPhases()
                .stream()
                .filter(phase -> phase.getName().equals(finalLastExecutedPhaseName))
                .findFirst();

        if (!lastExecutedPhase.isPresent()
            || !orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().containsKey(lastExecutedPhase.get().getUuid())) {
          return null;
        }
        return anExecutionEventAdvice()
            .withNextStateName(
                orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(lastExecutedPhase.get().getUuid()).getName())
            .withExecutionInterruptType(ROLLBACK)
            .build();
      }

      List<FailureStrategy> workflowFailureStrategies = orchestrationWorkflow.getFailureStrategies();

      if (state.getParentId() != null) {
        PhaseStep phaseStep = findPhaseStep(orchestrationWorkflow, phaseElement, state);

        if (phaseStep != null && isNotEmpty(phaseStep.getFailureStrategies())) {
          FailureStrategy failureStrategy =
              selectTopMatchingStrategy(phaseStep.getFailureStrategies(), executionEvent.getFailureTypes(),
                  state.getName(), phaseElement, FailureStrategyLevel.STEP, isTimeoutSupportOnWFLevelEnabled);

          if (failureStrategy == null) {
            failureStrategy = selectTopMatchingStrategy(workflowFailureStrategies, executionEvent.getFailureTypes(),
                state.getName(), phaseElement, FailureStrategyLevel.WORKFLOW, isTimeoutSupportOnWFLevelEnabled);
          }

          return computeExecutionEventAdvice(
              orchestrationWorkflow, failureStrategy, executionEvent, null, stateExecutionInstance, null);
        }
      }

      FailureStrategy failureStrategy =
          selectTopMatchingStrategy(workflowFailureStrategies, executionEvent.getFailureTypes(), state.getName(),
              phaseElement, FailureStrategyLevel.WORKFLOW, isTimeoutSupportOnWFLevelEnabled);

      String pipelineId = null;
      if (workflowExecution.getPipelineSummary() != null) {
        pipelineId = workflowExecution.getPipelineSummary().getPipelineId();
      }

      return computeExecutionEventAdvice(
          orchestrationWorkflow, failureStrategy, executionEvent, phaseSubWorkflow, stateExecutionInstance, pipelineId);

    } catch (Exception ex) {
      log.error("Error Occurred while calculating advise. This is really bad", ex);
      return null;
    } catch (Throwable t) {
      log.error("Encountered a throwable while calculating execution advice: {}", t);
      return null;
    } finally {
      try {
        if (state.getStateType().equals(StateType.PHASE_STEP.name()) && state instanceof PhaseStepSubWorkflow) {
          PhaseStepSubWorkflow phaseStepSubWorkflow = (PhaseStepSubWorkflow) state;
          instanceHelper.extractInstance(
              phaseStepSubWorkflow, executionEvent, workflowExecution, context, stateExecutionInstance);
        }
      } catch (Exception ex) {
        log.warn("Error while getting workflow execution data for instance sync for execution: {}",
            workflowExecution.getUuid(), ex);
      } catch (Throwable t) {
        log.error("Encountered a throwable while extracting instance", t);
        return null;
      }
    }
  }

  @VisibleForTesting
  public static PhaseStep findPhaseStep(
      CanaryOrchestrationWorkflow orchestrationWorkflow, PhaseElement phaseElement, State state) {
    if (orchestrationWorkflow == null || state.getParentId() == null || REPEAT.name().equals(state.getStateType())
        || FORK.name().equals(state.getStateType())) {
      return null;
    }

    if (state.getParentId().equals(orchestrationWorkflow.getPreDeploymentSteps().getUuid())) {
      return orchestrationWorkflow.getPreDeploymentSteps();
    } else if (orchestrationWorkflow.getRollbackProvisioners() != null
        && state.getParentId().equals(orchestrationWorkflow.getRollbackProvisioners().getUuid())) {
      return orchestrationWorkflow.getRollbackProvisioners();
    } else if (state.getParentId().equals(orchestrationWorkflow.getPostDeploymentSteps().getUuid())) {
      return orchestrationWorkflow.getPostDeploymentSteps();
    } else {
      if (phaseElement == null) {
        return null;
      }

      WorkflowPhase phase;
      if (phaseElement.isRollback()) {
        phase = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap() == null
            ? null
            : orchestrationWorkflow.getRollbackWorkflowPhaseIdMap()
                  .values()
                  .stream()
                  .filter(phase1 -> phase1 != null && phase1.getUuid().equals(phaseElement.getUuid()))
                  .findFirst()
                  .orElse(null);
      } else {
        phase = orchestrationWorkflow.getWorkflowPhaseIdMap() == null
            ? null
            : orchestrationWorkflow.getWorkflowPhaseIdMap().get(phaseElement.getUuid());
      }

      if (phase == null || isEmpty(phase.getPhaseSteps())) {
        return null;
      }

      Optional<PhaseStep> phaseStepOptional = phase.getPhaseSteps()
                                                  .stream()
                                                  .filter(ps -> ps != null && state.getParentId().equals(ps.getUuid()))
                                                  .findFirst();
      if (phaseStepOptional.isPresent()) {
        return phaseStepOptional.get();
      }
    }

    return null;
  }

  @VisibleForTesting
  public static ExecutionEventAdvice shouldSkipStep(
      ExecutionContextImpl context, PhaseStep phaseStep, State state, FeatureFlagService featureFlagService) {
    if (phaseStep == null || isEmpty(phaseStep.getStepSkipStrategies())) {
      return null;
    }

    List<StepSkipStrategy> stepSkipStrategies = phaseStep.getStepSkipStrategies();
    for (StepSkipStrategy strategy : stepSkipStrategies) {
      String assertionExpression = strategy.getAssertionExpression();
      if (!strategy.containsStepId(state.getId())) {
        continue;
      }

      try {
        logAppDefaults(context, featureFlagService);
        context.renderExpression(assertionExpression);
        Object resultObj = context.evaluateExpression(assertionExpression);
        if (!(resultObj instanceof Boolean)) {
          return anExecutionEventAdvice()
              .withSkipState(true)
              .withSkipExpression(assertionExpression)
              .withSkipError("Error evaluating skip condition: Expression '" + assertionExpression
                  + "' did not return a boolean value")
              .build();
        }

        boolean assertionResult = (boolean) resultObj;
        if (assertionResult) {
          return anExecutionEventAdvice().withSkipState(true).withSkipExpression(assertionExpression).build();
        }
      } catch (Exception ex) {
        log.error("Error while evaluating assertion expression", ex);
        return anExecutionEventAdvice()
            .withSkipState(true)
            .withSkipExpression(assertionExpression)
            .withSkipError(processErrorMessage(assertionExpression, ex))
            .build();
      }
    }

    return null;
  }

  private static void logAppDefaults(ExecutionContextImpl context, FeatureFlagService featureFlagService) {
    if (featureFlagService.isEnabled(LOG_APP_DEFAULTS, context.getAccountId())) {
      Application app = context.getApp();
      if (app != null) {
        Map<String, String> appDefaults = app.getDefaults();
        if (isEmpty(appDefaults)) {
          log.info(DEBUG_APP_DEFAULTS + " - There no defaults found");
        } else {
          log.info(DEBUG_APP_DEFAULTS + " : " + appDefaults);
        }
      }
    }
  }

  private static String processErrorMessage(String assertionExpression, Exception ex) {
    if (ex instanceof JexlException.Variable
        && ((JexlException.Variable) ex).getVariable().equals("sweepingOutputSecrets")) {
      return "Error evaluating skip condition: " + assertionExpression
          + ": Secret Variables defined in Script output of shell scripts cannot be used in skip assertions";
    }

    return "Error evaluating skip condition: " + assertionExpression + ": " + ExceptionUtils.getMessage(ex);
  }

  boolean isExecutionHostsPresent(ExecutionContextImpl context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    if (workflowStandardParams != null && isNotEmpty(workflowStandardParams.getExecutionHosts())) {
      log.info("Not generating rolling  phases when execution hosts are present");
      return true;
    }
    return false;
  }

  ExecutionEventAdvice computeExecutionEventAdvice(CanaryOrchestrationWorkflow orchestrationWorkflow,
      FailureStrategy failureStrategy, ExecutionEvent executionEvent, PhaseSubWorkflow phaseSubWorkflow,
      StateExecutionInstance stateExecutionInstance, String pipelineId) {
    if (failureStrategy == null
        && workflowExecutionService.checkIfOnDemand(
            stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid())) {
      if (phaseSubWorkflow == null) {
        return null;
      }
      return phaseSubWorkflowAdvice(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
    }
    if (failureStrategy == null) {
      return null;
    }

    RepairActionCode repairActionCode = failureStrategy.getRepairActionCode();
    if (repairActionCode == null) {
      return null;
    }

    switch (repairActionCode) {
      case IGNORE: {
        return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.IGNORE).build();
      }
      case END_EXECUTION: {
        return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.END_EXECUTION).build();
      }

      case ABORT_WORKFLOW_EXECUTION: {
        ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                    .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                    .executionUuid(executionEvent.getContext().getWorkflowExecutionId())
                                                    .appId(executionEvent.getContext().getAppId())
                                                    .build();
        workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
        return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.END_EXECUTION).build();
      }

      case MANUAL_INTERVENTION: {
        State state = executionEvent.getState();
        if (REPEAT.name().equals(state.getStateType()) || FORK.name().equals(state.getStateType())
            || PHASE.name().equals(state.getStateType()) || PHASE_STEP.name().equals(state.getStateType())
            || SUB_WORKFLOW.name().equals(state.getStateType())) {
          return null;
        }

        Map<String, Object> stateParams = fetchStateParams(orchestrationWorkflow, state, executionEvent);
        return issueManualInterventionAdvice(failureStrategy, stateParams);
      }

      case ROLLBACK_PHASE: {
        if (phaseSubWorkflow == null) {
          return null;
        }
        if (phaseSubWorkflow.isRollback()) {
          return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
        }

        if (!orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().containsKey(phaseSubWorkflow.getId())) {
          return null;
        }

        return anExecutionEventAdvice()
            .withNextStateName(
                orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phaseSubWorkflow.getId()).getName())
            .withExecutionInterruptType(ROLLBACK)
            .build();
      }

      case ROLLBACK_PROVISIONER_AFTER_PHASES: {
        if (featureFlagService.isEnabled(
                FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, executionEvent.getContext().getAccountId())) {
          /*
          We need to set this flag here. Using this flag later we mark rollback as completed once
          the Provisioners are rolled back
           */
          stateExecutionInstance.setRollbackProvisionerAfterPhases(true);

          WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
              executionEvent.getContext().getAppId(), executionEvent.getContext().getWorkflowExecutionId());
          workflowExecution.setRollbackProvisionerAfterPhases(true);
          wingsPersistence.save(workflowExecution);

          if (phaseSubWorkflow == null) {
            return null;
          }
          return phaseSubWorkflowAdviceWhenRollbackProvisionersAfterPhases(
              orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
        } else {
          throw new InvalidRequestException(
              "Rollback Provisioner after Phases not supported as ROLLBACK_PROVISIONER_AFTER_PHASES feature flag is not enabled",
              WingsException.USER);
        }
      }

      case ROLLBACK_WORKFLOW: {
        if (phaseSubWorkflow == null) {
          log.info(
              "Customer setup rollback workflow on step failure strategy: " + stateExecutionInstance.getAccountId());

          if (featureFlagService.isEnabled(
                  ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES, stateExecutionInstance.getAccountId())) {
            ExecutionInterrupt executionInterrupt =
                anExecutionInterrupt()
                    .executionInterruptType(ROLLBACK)
                    .executionUuid(executionEvent.getContext().getWorkflowExecutionId())
                    .appId(executionEvent.getContext().getAppId())
                    .build();
            workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
            return anExecutionEventAdvice().withExecutionInterruptType(ROLLBACK).build();
          }
          return null;
        }

        if (featureFlagService.isEnabled(FeatureName.SPG_PIPELINE_ROLLBACK, stateExecutionInstance.getAccountId())) {
          Pipeline pipeline = pipelineService.getPipeline(stateExecutionInstance.getAppId(), pipelineId);
          if (pipeline != null && pipeline.isRollbackPreviousStages()) {
            return null;
          }
        }

        return phaseSubWorkflowAdvice(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
      }

      case RETRY: {
        String stateType = executionEvent.getState().getStateType();
        if (stateType.equals(StateType.PHASE.name()) || stateType.equals(StateType.PHASE_STEP.name())
            || stateType.equals(StateType.SUB_WORKFLOW.name()) || stateType.equals(StateType.FORK.name())
            || stateType.equals(REPEAT.name())) {
          // Retry is only at the leaf node
          FailureStrategy failureStrategyAfterRetry = getFailureStrategyAfterRetry(failureStrategy);
          return computeExecutionEventAdvice(orchestrationWorkflow, failureStrategyAfterRetry, executionEvent,
              phaseSubWorkflow, stateExecutionInstance, null);
        }

        List<StateExecutionData> stateExecutionDataHistory = ((ExecutionContextImpl) executionEvent.getContext())
                                                                 .getStateExecutionInstance()
                                                                 .getStateExecutionDataHistory();
        if (stateExecutionDataHistory == null || stateExecutionDataHistory.size() < failureStrategy.getRetryCount()) {
          int waitInterval = 0;
          if (failureStrategy.getRetryIntervals() != null) {
            List<Integer> retryIntervals =
                failureStrategy.getRetryIntervals().stream().filter(Objects::nonNull).collect(toList());
            if (isNotEmpty(retryIntervals)) {
              if (isEmpty(stateExecutionDataHistory)) {
                waitInterval = retryIntervals.get(0);
              } else if (stateExecutionDataHistory.size() > retryIntervals.size() - 1) {
                waitInterval = retryIntervals.get(retryIntervals.size() - 1);
              } else {
                waitInterval = retryIntervals.get(stateExecutionDataHistory.size());
              }
            }
          }
          return anExecutionEventAdvice()
              .withExecutionInterruptType(ExecutionInterruptType.RETRY)
              .withWaitInterval(waitInterval)
              .build();
        } else {
          FailureStrategy failureStrategyAfterRetry = getFailureStrategyAfterRetry(failureStrategy);
          return computeExecutionEventAdvice(orchestrationWorkflow, failureStrategyAfterRetry, executionEvent,
              phaseSubWorkflow, stateExecutionInstance, null);
        }
      }
      default:
        return null;
    }
  }

  @VisibleForTesting
  ExecutionEventAdvice issueManualInterventionAdvice(FailureStrategy failureStrategy, Map<String, Object> stateParams) {
    Long manualInterventionTimeout = failureStrategy.getManualInterventionTimeout();
    ExecutionInterruptType actionAfterTimeout = failureStrategy.getActionAfterTimeout();
    return anExecutionEventAdvice()
        .withTimeout(isValidTimeOut(manualInterventionTimeout) ? manualInterventionTimeout : DEFAULT_TIMEOUT)
        .withActionAfterManualInterventionTimeout(
            isValidAction(actionAfterTimeout) ? actionAfterTimeout : DEFAULT_ACTION_AFTER_TIMEOUT)
        .withExecutionInterruptType(ExecutionInterruptType.WAITING_FOR_MANUAL_INTERVENTION)
        .withStateParams(stateParams)
        .build();
  }

  private boolean isValidTimeOut(Long manualInterventionTimeout) {
    return manualInterventionTimeout != null && manualInterventionTimeout >= 60000L;
  }

  private boolean isValidAction(ExecutionInterruptType actionAfterTimeout) {
    return Arrays.asList(ExecutionInterruptType.values()).contains(actionAfterTimeout);
  }

  @VisibleForTesting
  FailureStrategy getFailureStrategyAfterRetry(FailureStrategy failureStrategy) {
    FailureStrategyBuilder failureStrategyBuilder = FailureStrategy.builder();
    RepairActionCode repairActionCodeAfterRetry = failureStrategy.getRepairActionCodeAfterRetry();
    Long timeout;
    ExecutionInterruptType actionAfterTimeout;
    if (MANUAL_INTERVENTION == repairActionCodeAfterRetry) {
      timeout = isValidTimeOut(failureStrategy.getManualInterventionTimeout())
          ? failureStrategy.getManualInterventionTimeout()
          : DEFAULT_TIMEOUT;
      actionAfterTimeout = isValidAction(failureStrategy.getActionAfterTimeout())
          ? failureStrategy.getActionAfterTimeout()
          : DEFAULT_ACTION_AFTER_TIMEOUT;
      failureStrategyBuilder.manualInterventionTimeout(timeout).actionAfterTimeout(actionAfterTimeout);
    }
    return failureStrategyBuilder.repairActionCode(repairActionCodeAfterRetry).build();
  }

  private OrchestrationWorkflow findOrchestrationWorkflow(Workflow workflow, WorkflowExecution workflowExecution) {
    if (workflow == null || workflow.getOrchestrationWorkflow() == null
        || !(workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow)) {
      return null;
    }

    return workflow.getOrchestrationWorkflow();
  }

  private ExecutionEventAdvice computeRollingPhase(StateExecutionInstance stateExecutionInstance) {
    return anExecutionEventAdvice()
        .withExecutionInterruptType(ExecutionInterruptType.NEXT_STEP)
        .withNextStateName(stateExecutionInstance.getStateName())
        .withNextChildStateMachineId(stateExecutionInstance.getChildStateMachineId())
        .withNextStateDisplayName(computeDisplayName(stateExecutionInstance))
        .build();
  }

  public String computeDisplayName(StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance.getOrchestrationWorkflowType() == ROLLING
        && PHASE.name().equals(stateExecutionInstance.getStateType()) && !stateExecutionInstance.isRollback()) {
      List<String> phaseNamesAPI = stateExecutionService.phaseNames(
          stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid());

      final long count = phaseNamesAPI.stream().filter(key -> key.startsWith(ROLLING_PHASE_PREFIX)).count();
      return ROLLING_PHASE_PREFIX + (count + 1);
    }
    return null;
  }

  private Map<String, Object> fetchStateParams(
      CanaryOrchestrationWorkflow orchestrationWorkflow, State state, ExecutionEvent executionEvent) {
    if (orchestrationWorkflow == null || orchestrationWorkflow.getGraph() == null || state == null
        || state.getId() == null) {
      return null;
    }
    if (state.getParentId() != null && orchestrationWorkflow.getGraph().getSubworkflows() == null
        || orchestrationWorkflow.getGraph().getSubworkflows().get(state.getParentId()) == null) {
      return null;
    }
    Graph graph = orchestrationWorkflow.getGraph().getSubworkflows().get(state.getParentId());
    GraphNode node1 =
        graph.getNodes().stream().filter(node -> node.getId().equals(state.getId())).findFirst().orElse(null);
    if (node1 == null) {
      return null;
    }

    Map<String, Object> properties = node1.getProperties();
    if (isNotEmpty(state.getTemplateVariables())) {
      properties.put("templateVariables", state.getTemplateVariables());
    }
    if (isNotEmpty(state.getTemplateUuid())) {
      properties.put("templateUuid", state.getTemplateUuid());
    }
    if (isNotEmpty(state.getTemplateVersion())) {
      properties.put("templateVersion", state.getTemplateVersion());
    }
    if (executionEvent != null && executionEvent.getContext() != null
        && isNotEmpty(executionEvent.getContext().getAccountId())) {
      properties.put("accountId", executionEvent.getContext().getAccountId());
    }
    return properties;
  }

  private ExecutionEventAdvice phaseSubWorkflowAdvice(CanaryOrchestrationWorkflow orchestrationWorkflow,
      PhaseSubWorkflow phaseSubWorkflow, StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance.getOrchestrationWorkflowType() == ROLLING
        && !workflowServiceHelper.isOrchestrationWorkflowForK8sV2Service(
            stateExecutionInstance.getAppId(), orchestrationWorkflow)) {
      return phaseSubWorkflowAdviceForRolling(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance, false);
    } else {
      return phaseSubWorkflowAdviceForOthers(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
    }
  }

  private ExecutionEventAdvice phaseSubWorkflowAdviceWhenRollbackProvisionersAfterPhases(
      CanaryOrchestrationWorkflow orchestrationWorkflow, PhaseSubWorkflow phaseSubWorkflow,
      StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance.getOrchestrationWorkflowType() == ROLLING
        && !workflowServiceHelper.isOrchestrationWorkflowForK8sV2Service(
            stateExecutionInstance.getAppId(), orchestrationWorkflow)) {
      // This is an invalid case. Should never happen. Rolling workflows do not support
      // ROLLBACK_PROVISIONER_AFTER_PHASES failure strategy
      throw new InvalidRequestException(
          "Rollback Provisioner after Phases not applicable for Rolling workflow", WingsException.USER);
    } else {
      return phaseSubWorkflowAdviceForOthersWhenRollbackProvisionersAfterPhases(
          orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
    }
  }

  private ExecutionEventAdvice phaseSubWorkflowOnDemandRollbackAdvice(CanaryOrchestrationWorkflow orchestrationWorkflow,
      PhaseSubWorkflow phaseSubWorkflow, StateExecutionInstance stateExecutionInstance, boolean rolling) {
    if (orchestrationWorkflow.checkLastPhaseForOnDemandRollback(phaseSubWorkflow.getName())) {
      if (rolling) {
        return phaseSubWorkflowAdviceForRolling(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance, true);
      }
      return phaseSubWorkflowAdviceForOthers(orchestrationWorkflow, phaseSubWorkflow, stateExecutionInstance);
    }
    return null;
  }

  private ExecutionEventAdvice phaseSubWorkflowAdviceForRolling(CanaryOrchestrationWorkflow orchestrationWorkflow,
      PhaseSubWorkflow phaseSubWorkflow, StateExecutionInstance stateExecutionInstance, boolean onDemandRollback) {
    int rollingIndex;
    if (!phaseSubWorkflow.isRollback() && !onDemandRollback) {
      rollingIndex = Integer.parseInt(stateExecutionInstance.getDisplayName().substring(ROLLING_PHASE_PREFIX.length()));
    } else if (!phaseSubWorkflow.isRollback() && onDemandRollback) {
      rollingIndex = stateExecutionService.getRollingPhaseCount(
          stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid());
    } else {
      rollingIndex = Integer.parseInt(
          stateExecutionInstance.getDisplayName().substring(ROLLBACK_PREFIX.length() + PHASE_NAME_PREFIX.length()));
      rollingIndex--;
    }
    if (rollingIndex < 1) {
      return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
    }
    return anExecutionEventAdvice()
        .withNextStateName(orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().values().iterator().next().getName())
        .withNextStateDisplayName(ROLLBACK_PREFIX + PHASE_NAME_PREFIX + rollingIndex)
        .withRollbackPhaseName(ROLLING_PHASE_PREFIX + rollingIndex)
        .withExecutionInterruptType(ROLLBACK)
        .build();
  }

  private ExecutionEventAdvice getRollbackProvisionerAdviceIfNeeded(
      PhaseStep preDeploymentSteps, String nextStateName) {
    if (preDeploymentSteps != null && preDeploymentSteps.getSteps() != null
        && preDeploymentSteps.getSteps().stream().anyMatch(step
            -> step.getType().equals(StateType.CLOUD_FORMATION_CREATE_STACK.name())
                || step.getType().equals(StateType.TERRAFORM_PROVISION.getType())
                || step.getType().equals(StateType.TERRAGRUNT_PROVISION.getType())
                || step.getType().equals(StateType.ARM_CREATE_RESOURCE.getType()))) {
      return anExecutionEventAdvice().withNextStateName(nextStateName).withExecutionInterruptType(ROLLBACK).build();
    }
    return null;
  }

  private ExecutionEventAdvice phaseSubWorkflowAdviceForOthers(CanaryOrchestrationWorkflow orchestrationWorkflow,
      PhaseSubWorkflow phaseSubWorkflow, StateExecutionInstance stateExecutionInstance) {
    if (!phaseSubWorkflow.isRollback()) {
      ExecutionEventAdvice rollbackProvisionerAdvice =
          getRollbackProvisionerAdviceIfNeeded(orchestrationWorkflow.getPreDeploymentSteps(), ROLLBACK_PROVISIONERS);
      if (rollbackProvisionerAdvice != null) {
        return rollbackProvisionerAdvice;
      }

      if (!orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().containsKey(phaseSubWorkflow.getId())) {
        return null;
      }

      return anExecutionEventAdvice()
          .withNextStateName(
              orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phaseSubWorkflow.getId()).getName())
          .withExecutionInterruptType(ROLLBACK)
          .build();
    }

    List<String> phaseNames =
        orchestrationWorkflow.getWorkflowPhases().stream().map(WorkflowPhase::getName).collect(toList());
    int index = phaseNames.indexOf(phaseSubWorkflow.getPhaseNameForRollback());
    if (index == 0) {
      // All Done
      return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
    }

    String phaseId = orchestrationWorkflow.getWorkflowPhases().get(index - 1).getUuid();
    WorkflowPhase rollbackPhase = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phaseId);
    if (rollbackPhase == null) {
      log.warn("Missing rollback phase to phase {}", phaseId);
      return null;
    }
    return anExecutionEventAdvice()
        .withExecutionInterruptType(ROLLBACK)
        .withNextStateName(rollbackPhase.getName())
        .build();
  }

  /*
  Get execution advice when ROLLBACK_PROVISIONER_AFTER_PHASES failure strategy is chosen
  When rollback happens it returns deployment phase rollback before rollback provisioners
   */
  private ExecutionEventAdvice phaseSubWorkflowAdviceForOthersWhenRollbackProvisionersAfterPhases(
      CanaryOrchestrationWorkflow orchestrationWorkflow, PhaseSubWorkflow phaseSubWorkflow,
      StateExecutionInstance stateExecutionInstance) {
    if (!phaseSubWorkflow.isRollback()) {
      if (!orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().containsKey(phaseSubWorkflow.getId())) {
        return null;
      }

      return anExecutionEventAdvice()
          .withNextStateName(
              orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phaseSubWorkflow.getId()).getName())
          .withExecutionInterruptType(ROLLBACK)
          .build();
    }

    List<String> phaseNames =
        orchestrationWorkflow.getWorkflowPhases().stream().map(WorkflowPhase::getName).collect(toList());
    int index = phaseNames.indexOf(phaseSubWorkflow.getPhaseNameForRollback());
    if (index == 0) {
      // Rollback Provisioners in reverse manner when ROLLBACK_PROVISIONER_AFTER_PHASES failure strategy is chosen
      ExecutionEventAdvice rollbackProvisionerAdvice = getRollbackProvisionerAdviceIfNeeded(
          orchestrationWorkflow.getPreDeploymentSteps(), ROLLBACK_PROVISIONERS_REVERSE);
      if (rollbackProvisionerAdvice != null) {
        return rollbackProvisionerAdvice;
      }

      // Mark rollback of workflow as done as there is no provisioner to rollback
      return anExecutionEventAdvice().withExecutionInterruptType(ExecutionInterruptType.ROLLBACK_DONE).build();
    }

    String phaseId = orchestrationWorkflow.getWorkflowPhases().get(index - 1).getUuid();
    WorkflowPhase rollbackPhase = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phaseId);
    if (rollbackPhase == null) {
      return null;
    }
    return anExecutionEventAdvice()
        .withExecutionInterruptType(ROLLBACK)
        .withNextStateName(rollbackPhase.getName())
        .build();
  }

  public static FailureStrategy selectTopMatchingStrategy(List<FailureStrategy> failureStrategies,
      EnumSet<FailureType> failureTypes, String stateName, PhaseElement phaseElement, FailureStrategyLevel level,
      boolean isTimeoutSupportOnWFLevelEnabled) {
    final FailureStrategy failureStrategy = selectTopMatchingStrategyInternal(
        failureStrategies, failureTypes, stateName, phaseElement, level, isTimeoutSupportOnWFLevelEnabled);

    if (failureStrategy != null && isNotEmpty(failureStrategy.getFailureTypes()) && isEmpty(failureTypes)) {
      log.warn("Defaulting to accepting the action. "
              + "the propagated failure types for state {} are unknown. ",
          stateName);
    }

    return failureStrategy;
  }

  private static FailureStrategy selectTopMatchingStrategyInternal(List<FailureStrategy> failureStrategies,
      EnumSet<FailureType> failureTypes, String stateName, PhaseElement phaseElement, FailureStrategyLevel level,
      boolean isTimeoutSupportOnWFLevelEnabled) {
    if (isEmpty(failureStrategies)) {
      return null;
    }

    List<FailureStrategy> filteredFailureStrategies =
        failureStrategies.stream()
            .filter(failureStrategy -> {
              // we need at least one specific failure else we assume that we should apply in every case
              if (isEmpty(failureStrategy.getFailureTypes())) {
                return true;
              }
              // we need at least one failure type returned from the error to filter out
              if (isEmpty(failureTypes)) {
                return true;
              }
              return !disjoint(failureTypes, failureStrategy.getFailureTypes());
            })
            .collect(toList());

    filteredFailureStrategies = filteredFailureStrategies.stream()
                                    .filter(failureStrategy
                                        -> isEmpty(failureStrategy.getSpecificSteps())
                                            || failureStrategy.getSpecificSteps().contains(stateName))
                                    .collect(toList());

    if (filteredFailureStrategies.isEmpty()) {
      return null;
    }

    if (isTimeoutFailure(level, failureTypes, isTimeoutSupportOnWFLevelEnabled)) {
      Optional<FailureStrategy> timeoutStrategy =
          filteredFailureStrategies.stream()
              .filter(failureStrategy -> isNotEmpty(failureStrategy.getFailureTypes()))
              .filter(failureStrategy -> failureStrategy.getFailureTypes().contains(FailureType.TIMEOUT_ERROR))
              .findFirst();
      if (timeoutStrategy.isPresent()) {
        return timeoutStrategy.get();
      }
    } else {
      filteredFailureStrategies = filterOutExplicitTimeoutStrategies(filteredFailureStrategies);
    }

    Optional<FailureStrategy> rollbackStrategy =
        filteredFailureStrategies.stream()
            .filter(f -> f.getRepairActionCode() == RepairActionCode.ROLLBACK_WORKFLOW)
            .findFirst();

    if (rollbackStrategy.isPresent()) {
      return rollbackStrategy.get();
    }

    rollbackStrategy = filteredFailureStrategies.stream()
                           .filter(f -> f.getRepairActionCode() == RepairActionCode.ROLLBACK_PHASE)
                           .findFirst();

    if (rollbackStrategy.isPresent()) {
      return rollbackStrategy.get();
    }

    if (level == FailureStrategyLevel.WORKFLOW) {
      return getResolveWorkflowLevelFailureStrategy(filteredFailureStrategies, phaseElement);
    } else {
      return filteredFailureStrategies.get(0);
    }
  }

  private static List<FailureStrategy> filterOutExplicitTimeoutStrategies(
      List<FailureStrategy> filteredFailureStrategies) {
    return filteredFailureStrategies.stream()
        .filter(failureStrategy
            -> !(isNotEmpty(failureStrategy.getFailureTypes()) && failureStrategy.getFailureTypes().size() == 1
                && failureStrategy.getFailureTypes().contains(FailureType.TIMEOUT_ERROR)))
        .collect(toList());
  }

  private static boolean isTimeoutFailure(
      FailureStrategyLevel level, EnumSet<FailureType> failureTypes, boolean ffTimeoutWFWLevel) {
    return (ffTimeoutWFWLevel || level == FailureStrategyLevel.STEP) && isNotEmpty(failureTypes)
        && failureTypes.contains(FailureType.TIMEOUT_ERROR);
  }

  private static FailureStrategy getResolveWorkflowLevelFailureStrategy(
      List<FailureStrategy> filteredFailureStrategies, PhaseElement phaseElement) {
    FailureStrategy workflowFailureStrategy =
        getWorkflowFailureStrategyByScope(filteredFailureStrategies, ExecutionScope.WORKFLOW);
    FailureStrategy workflowPhaseFailureStrategy =
        getWorkflowFailureStrategyByScope(filteredFailureStrategies, ExecutionScope.WORKFLOW_PHASE);

    // Check if step is within the WF Phase and apply correct strategy
    if (phaseElement != null) {
      if (workflowPhaseFailureStrategy != null) {
        return workflowPhaseFailureStrategy;
      } else if (workflowFailureStrategy != null) {
        return workflowFailureStrategy;
      } else {
        return null;
      }
    } else {
      if (workflowFailureStrategy != null) {
        return workflowFailureStrategy;
      } else {
        return null;
      }
    }
  }

  private static FailureStrategy getWorkflowFailureStrategyByScope(
      List<FailureStrategy> filteredFailureStrategies, ExecutionScope scope) {
    return filteredFailureStrategies.stream()
        .filter(strategy -> strategy.getExecutionScope() == scope)
        .findFirst()
        .orElse(null);
  }
}
