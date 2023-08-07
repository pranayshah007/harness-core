/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.api.InstanceElement;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.EntityType;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.NotificationRule;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.TrafficShiftMetadata;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowCategorySteps;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.Include;
import software.wings.beans.stats.CloneMetadata;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.manipulation.SettingsServiceManipulationObserver;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;
import software.wings.sm.StateTypeDescriptor;
import software.wings.sm.StateTypeScope;
import software.wings.stencils.Stencil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * The Interface WorkflowService.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface WorkflowService extends OwnedByApplication, SettingsServiceManipulationObserver {
  PageResponse<Workflow> listWorkflows(PageRequest<Workflow> pageRequest);

  PageResponse<Workflow> listWorkflowsWithoutOrchestration(PageRequest<Workflow> pageRequest);

  PageResponse<Workflow> listWorkflows(
      PageRequest<Workflow> pageRequest, Integer previousExecutionsCount, boolean withTags, String tagFilter);

  List<Workflow> listWorkflows(String artifactStreamId, String accountId);

  Workflow getWorkflow(@NotNull String appId, @NotNull String workflowId);

  Workflow readWorkflow(@NotNull String appId, @NotNull String workflowId, Integer version);

  Workflow readWorkflow(@NotNull String appId, @NotNull String workflowId);

  boolean workflowExists(String appId, String workflowId);

  Workflow readWorkflowWithoutServices(@NotNull String appId, @NotNull String workflowId);

  Workflow readWorkflowWithoutOrchestration(@NotNull String appId, @NotNull String workflowId);

  List<Workflow> listWorkflowsWithoutOrchestration(Collection<String> workflowIds);

  Workflow readWorkflowByName(String appId, String workflowName);

  @ValidationGroups(Create.class) Workflow createWorkflow(@Valid Workflow workflow);

  boolean ensureArtifactCheck(String appId, OrchestrationWorkflow orchestrationWorkflow);

  @ValidationGroups(Update.class) Workflow updateWorkflow(@Valid Workflow workflow, boolean migration);

  @ValidationGroups(Update.class)
  Workflow updateLinkedWorkflow(@Valid Workflow workflow, Workflow existingWorkflow, boolean fromYaml);

  @ValidationGroups(Update.class)
  Workflow updateWorkflow(
      Workflow workflow, OrchestrationWorkflow orchestrationWorkflow, boolean envChanged, boolean migration);

  @ValidationGroups(Update.class)
  Workflow updateWorkflow(@Valid Workflow workflow, OrchestrationWorkflow orchestrationWorkflow, boolean migration);

  @ValidationGroups(Update.class)
  Workflow updateWorkflow(@Valid Workflow workflow, OrchestrationWorkflow orchestrationWorkflow,
      boolean inframappingChanged, boolean envChanged, boolean cloned);

  boolean deleteWorkflow(String appId, String workflowId);

  StateMachine createStateMachine(@Valid StateMachine stateMachine);

  StateMachine readLatestStateMachine(String appId, String originId);

  PageResponse<StateMachine> listStateMachines(PageRequest<StateMachine> req);

  List<Workflow> list(String accountId, List<String> projectFields, String queryHint);

  Map<StateTypeScope, List<Stencil>> stencils(
      String appId, String workflowId, String phaseId, StateTypeScope... stateTypeScopes);

  StateMachine readStateMachine(String appId, String originId, Integer version);

  Map<String, StateTypeDescriptor> stencilMap(String appId);

  PhaseStep updatePreDeployment(String appId, String workflowId, PhaseStep phaseStep);

  PhaseStep updatePostDeployment(String appId, String workflowId, PhaseStep phaseStep);

  WorkflowPhase createWorkflowPhase(String appId, String workflowId, WorkflowPhase workflowPhase);

  @ValidationGroups(Update.class)
  WorkflowPhase updateWorkflowPhase(
      @NotEmpty String appId, @NotEmpty String workflowId, @Valid WorkflowPhase workflowPhase);

  WorkflowPhase updateWorkflowPhaseRollback(
      String appId, String workflowId, String phaseId, WorkflowPhase workflowPhase);

  void deleteWorkflowPhase(String appId, String workflowId, String phaseId);

  List<NotificationRule> updateNotificationRules(
      String appId, String workflowId, List<NotificationRule> notificationRules);

  ConcurrencyStrategy updateConcurrencyStrategy(
      String appId, String workflowId, @NotNull @Valid ConcurrencyStrategy concurrencyStrategy);

  List<FailureStrategy> updateFailureStrategies(
      String appId, String workflowId, @Valid List<FailureStrategy> failureStrategies);

  List<Variable> updateUserVariables(String appId, String workflowId, List<Variable> userVariables);

  GraphNode updateGraphNode(
      @NotNull String appId, @NotNull String workflowId, @NotNull String subworkflowId, GraphNode node);

  @ValidationGroups(Create.class)
  Workflow cloneWorkflow(String appId, Workflow originalWorkflow, @Valid Workflow workflow);

  Workflow updateWorkflow(String appId, String workflowId, Integer defaultVersion);

  Workflow cloneWorkflow(String appId, Workflow originalWorkflow, CloneMetadata cloneMetadata);

  WorkflowPhase cloneWorkflowPhase(String appId, String workflowId, WorkflowPhase workflowPhase);

  Map<String, String> getStateDefaults(@NotNull String appId, @NotNull String serviceId, @NotNull StateType stateType);

  void pruneDescendingEntities(@org.hibernate.validator.constraints.NotEmpty String appId,
      @org.hibernate.validator.constraints.NotEmpty String workflowId);

  boolean workflowHasSshDeploymentPhase(String appId, String workflowId);

  String getHPAYamlStringWithCustomMetric(
      Integer minAutoscaleInstances, Integer maxAutoscaleInstances, Integer targetCpuUtilizationPercentage);

  void loadOrchestrationWorkflow(Workflow workflow, Integer version);

  List<Service> getResolvedServices(Workflow workflow, Map<String, String> workflowVariables);

  List<String> getResolvedServiceIds(Workflow workflow, Map<String, String> workflowVariables);

  List<InfrastructureMapping> getResolvedInfraMappings(Workflow workflow, Map<String, String> workflowVariables);

  List<InfrastructureDefinition> getResolvedInfraDefinitions(
      Workflow workflow, Map<String, String> workflowVariables, String envId);

  /**
   * Resolves the inframappingIds from workflow variables
   * @param workflow
   * @param workflowVariables
   * @return
   */
  List<String> getResolvedInfraMappingIds(Workflow workflow, Map<String, String> workflowVariables);

  List<String> getResolvedInfraDefinitionIds(
      Workflow workflow, Map<String, String> workflowVariables, String resolveEnvId);

  String getResolvedServiceIdFromPhase(WorkflowPhase workflowPhase, Map<String, String> workflowVariables);

  String getResolvedInfraDefinitionIdFromPhase(WorkflowPhase workflowPhase, Map<String, String> workflowVariables);

  List<InstanceElement> getDeployedNodes(String appId, String workflowId);

  String obtainEnvIdWithoutOrchestration(Workflow workflow, Map<String, String> workflowVariables);

  String resolveEnvironmentId(Workflow workflow, Map<String, String> workflowVariables);

  String obtainTemplatedEnvironmentId(Workflow workflow, Map<String, String> workflowVariables);

  GraphNode readGraphNode(@NotEmpty String appId, @NotEmpty String workflowId, @NotEmpty String nodeId);

  List<EntityType> getRequiredEntities(@NotEmpty String appId, @NotEmpty String workflowId);

  DeploymentMetadata fetchDeploymentMetadata(String appId, Workflow workflow, Map<String, String> workflowVariables,
      List<String> artifactNeededServiceIds, List<String> envIds, Include... includeList);

  DeploymentMetadata fetchDeploymentMetadata(String appId, Workflow workflow, Map<String, String> workflowVariables,
      List<String> artifactRequiredServiceIds, List<String> envIds, boolean withDefaultArtifact,
      WorkflowExecution workflowExecution, Include... includes);

  void updateArtifactVariables(String appId, Workflow workflow, List<ArtifactVariable> artifactVariables,
      boolean withDefaultArtifact, WorkflowExecution workflowExecution);

  Artifact getArtifactVariableDefaultArtifact(ArtifactVariable artifactVariable, WorkflowExecution workflowExecution);

  Map<String, List<String>> getDisplayInfo(String appId, Workflow workflow, ArtifactVariable artifactVariable);

  Set<EntityType> fetchRequiredEntityTypes(String appId, Workflow workflow);

  boolean deleteByYamlGit(String appId, String workflowId, boolean syncFromGit);

  List<String> getLastSuccessfulWorkflowExecutionIds(String appId, String workflowId, String serviceId);

  WorkflowExecution getLastSuccessfulWorkflowExecution(String accountId, String appId, String serviceId);

  boolean isStateValid(String appId, String stateExecutionId);

  ExecutionStatus getExecutionStatus(String appId, String stateExecutionId);

  WorkflowExecution getWorkflowExecutionForStateExecutionId(String appId, String stateExecutionId);

  String fetchWorkflowName(@NotEmpty String appId, @NotEmpty String workflowId);
  List<String> obtainWorkflowNamesReferencedByEnvironment(String appId, @NotEmpty String envId);

  List<String> obtainWorkflowNamesReferencedByService(String appId, @NotEmpty String serviceId);

  List<String> obtainWorkflowNamesReferencedByServiceInfrastructure(String appId, @NotEmpty String infraMappingId);

  List<String> obtainWorkflowNamesReferencedByInfrastructureDefinition(String appId, String infraDefinitionId);

  WorkflowCategorySteps calculateCategorySteps(
      Workflow workflow, String userId, String phaseId, String sectionId, int position, boolean rollbackSection);

  void resolveArtifactStreamMetadata(
      String appId, List<ArtifactVariable> artifactVariables, WorkflowExecution workflowExecution);

  TrafficShiftMetadata readWorkflowTrafficShiftMetadata(@NotNull String appId, @NotNull String workflowId);

  PhaseStep generateRollbackProvisioners(
      PhaseStep preDeploymentSteps, PhaseStepType phaseStepType, String phaseStepName);

  PhaseStep generateRollbackProvisionersReverse(
      PhaseStep preDeploymentSteps, PhaseStepType phaseStepType, String phaseStepName);
}
