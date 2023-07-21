/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.STAGE_EXECUTION;
import static software.wings.sm.StateType.COLLECT_REMAINING_INSTANCES;
import static software.wings.sm.StateType.STAGING_ORIGINAL_EXECUTION;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.ff.FeatureFlagService;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.exception.InvalidRollbackException;
import software.wings.service.impl.workflow.queuing.WorkflowConcurrencyHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateMachine;
import software.wings.sm.states.StagingOriginalExecution.StagingOriginalExecutionKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class RollbackStateMachineGenerator {
  public static final String STAGING_PHASE_NAME = "Staging Execution";
  private static final String STAGING_PHASE_STEP_NAME = "Stage Rollback";
  private static final String STAGING_STEP_NAME = "Staging Original Execution";
  public static final String WHITE_SPACE = " ";
  @Inject private PipelineService pipelineService;

  @Inject private WorkflowService workflowService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowConcurrencyHelper workflowConcurrencyHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;

  public StateMachine generateForRollbackExecution(@NotNull String appId, @NotNull String successfulExecutionId)
      throws InvalidRollbackException {
    String[] fields = {
        WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.status, WorkflowExecutionKeys.workflowId};
    WorkflowExecution successfulExecution =
        workflowExecutionService.getWorkflowExecution(appId, successfulExecutionId, fields);
    if (!validForRollback(successfulExecution)) {
      throw new InvalidRollbackException("Execution Not Valid For Rollback", ErrorCode.INVALID_ROLLBACK);
    }
    return generateForRollback(appId, successfulExecution.getWorkflowId(), successfulExecutionId);
  }

  private StateMachine generateForRollback(
      @NotNull String appId, @NotNull String workflowId, @NotNull String successfulExecutionId) {
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    return getStateMachine(appId, workflow, successfulExecutionId);
  }

  private StateMachine getStateMachine(
      @NotNull String appId, @NotNull Workflow workflow, @NotNull String successfulExecutionId) {
    final OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    CanaryOrchestrationWorkflow modifiedOrchestrationWorkflow =
        modifyOrchestrationForRollback(appId, orchestrationWorkflow, successfulExecutionId);
    // We do not run post deployment steps during rollback so remove the transition link to Postdeployment steps from
    // graph
    Graph graph = modifiedOrchestrationWorkflow.generateGraph();
    graph.setLinks(graph.getLinks()
                       .stream()
                       .filter(link -> link.getTo() != modifiedOrchestrationWorkflow.getPostDeploymentSteps().getUuid())
                       .collect(toList()));
    modifiedOrchestrationWorkflow.setGraph(graph);

    return new StateMachine(workflow, workflow.getDefaultVersion(), modifiedOrchestrationWorkflow.getGraph(),
        workflowService.stencilMap(appId), false);
  }

  private CanaryOrchestrationWorkflow modifyOrchestrationForRollback(
      String appId, OrchestrationWorkflow orchestrationWorkflow, String successfulExecutionId) {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) orchestrationWorkflow.cloneInternal();

    // We do not want to run pre-deployment steps during rollback
    if (canaryOrchestrationWorkflow.getPreDeploymentSteps() != null) {
      canaryOrchestrationWorkflow.setPreDeploymentSteps(new PhaseStep(PRE_DEPLOYMENT));
    }

    for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
      phase.setName(STAGING_PHASE_NAME + WHITE_SPACE + phase.getName());
      phase.setPhaseSteps(Collections.singletonList(
          getRollbackStagingPhaseStep(appId, canaryOrchestrationWorkflow, successfulExecutionId)));
      WorkflowPhase rollbackPhase = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(phase.getUuid());
      if (isNotEmpty(rollbackPhase.getPhaseSteps())) {
        PhaseStep rollbackPhaseStep = rollbackPhase.getPhaseSteps().get(0);
        rollbackPhaseStep.getSteps().add(
            0, getResourceConstraintStep(appId, canaryOrchestrationWorkflow.getConcurrencyStrategy()));

        if (featureFlagService.isEnabled(FeatureName.WINRM_ASG_ROLLBACK, appService.getAccountIdByAppId(appId))
            && rollbackPhase.getDeploymentType() == DeploymentType.WINRM
            && infrastructureDefinitionService.get(appId, rollbackPhase.getInfraDefinitionId()).getCloudProviderType()
                == CloudProviderType.AWS) {
          addCollectRemainingInstancesPhase(rollbackPhase);
        }
      }
    }
    return canaryOrchestrationWorkflow;
  }

  private PhaseStep getRollbackStagingPhaseStep(
      String appId, CanaryOrchestrationWorkflow canaryOrchestrationWorkflow, String successfulExecutionId) {
    PhaseStep phaseStep =
        aPhaseStep(STAGE_EXECUTION, STAGING_PHASE_STEP_NAME)
            .addStep(getResourceConstraintStep(appId, canaryOrchestrationWorkflow.getConcurrencyStrategy()))
            .addStep(getStagingExecutionStep(successfulExecutionId))
            .build();
    phaseStep.setStepsIds(phaseStep.getSteps().stream().map(GraphNode::getId).collect(toList()));
    return phaseStep;
  }

  private GraphNode getStagingExecutionStep(String successfulExecutionId) {
    return GraphNode.builder()
        .id(generateUuid())
        .type(STAGING_ORIGINAL_EXECUTION.name())
        .name(STAGING_STEP_NAME)
        .properties(ImmutableMap.<String, Object>builder()
                        .put(StagingOriginalExecutionKeys.successfulExecutionId, successfulExecutionId)
                        .build())
        .build();
  }

  private GraphNode getResourceConstraintStep(String appId, ConcurrencyStrategy concurrencyStrategy) {
    return workflowConcurrencyHelper.getResourceConstraintStep(appId, concurrencyStrategy);
  }

  private boolean validForRollback(WorkflowExecution successfulExecution) {
    if (featureFlagService.isEnabled(FeatureName.SPG_PIPELINE_ROLLBACK, successfulExecution.getAccountId())) {
      if (successfulExecution.getPipelineSummary() != null) {
        Pipeline pipeline = pipelineService.getPipeline(
            successfulExecution.getAppId(), successfulExecution.getPipelineSummary().getPipelineId());
        if (pipeline.isRollbackPreviousStages()) {
          List<ExecutionStatus> validStatuses = List.of(ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);
          return successfulExecution != null && validStatuses.contains(successfulExecution.getStatus());
        }
      }
    }
    return successfulExecution != null && ExecutionStatus.SUCCESS == successfulExecution.getStatus();
  }

  private void addCollectRemainingInstancesPhase(WorkflowPhase rollbackPhase) {
    PhaseStep collectInstancesStep = new PhaseStep(PhaseStepType.COLLECT_INSTANCES, "Collect instances");
    collectInstancesStep.setUuid(generateUuid());
    collectInstancesStep.setRollback(true);
    collectInstancesStep.setSteps(Collections.singletonList(GraphNode.builder()
                                                                .type(COLLECT_REMAINING_INSTANCES.name())
                                                                .name(COLLECT_REMAINING_INSTANCES.getName())
                                                                .rollback(true)
                                                                .build()));

    rollbackPhase.getPhaseSteps().add(0, collectInstancesStep);
  }
}
