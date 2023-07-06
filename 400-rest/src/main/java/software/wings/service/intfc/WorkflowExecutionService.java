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
import io.harness.beans.WorkflowType;
import io.harness.persistence.HIterator;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.WorkflowElement;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApprovalAuthorization;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.AwsLambdaExecutionSummary;
import software.wings.beans.BuildExecutionSummary;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.EnvSummary;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.NameValuePair;
import software.wings.beans.PipelineStageGroupedInfo;
import software.wings.beans.RequiredExecutionArgs;
import software.wings.beans.StateExecutionElement;
import software.wings.beans.StateExecutionInterrupt;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.approval.PreviousApprovalDetails;
import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.beans.concurrency.ConcurrentExecutionResponse;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.beans.trigger.Trigger;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.intfc.deployment.PreDeploymentChecker;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.PhaseExecutionSummary;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.RollbackConfirmation;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachine;
import software.wings.sm.StateStatusUpdate;
import software.wings.sm.StateType;

import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface WorkflowExecutionService extends StateStatusUpdate {
  void refreshPipelineExecution(WorkflowExecution workflowExecution);

  void refreshStatus(WorkflowExecution workflowExecution);

  HIterator<WorkflowExecution> executions(String appId, long startedFrom, long statedTo, Set<String> includeOnlyFields);

  PageResponse<WorkflowExecution> listExecutions(PageRequest<WorkflowExecution> pageRequest, boolean includeGraph);

  List<WorkflowExecution> listExecutionsUsingQuery(
      Query<WorkflowExecution> query, FindOptions findOptions, boolean includeGraph);

  PageResponse<WorkflowExecution> listExecutions(PageRequest<WorkflowExecution> pageRequest, boolean includeGraph,
      boolean runningOnly, boolean withBreakdownAndSummary, boolean includeStatus, boolean withFailureDetails,
      boolean fromUi);

  WorkflowExecution triggerPipelineExecution(
      @NotNull String appId, @NotNull String pipelineId, ExecutionArgs executionArgs, Trigger trigger);

  WorkflowExecution triggerOrchestrationExecution(@NotNull String appId, String envId, @NotNull String orchestrationId,
      @NotNull ExecutionArgs executionArgs, Trigger trigger);

  int getActiveServiceCount(String accountId);

  WorkflowExecution triggerOrchestrationExecution(@NotNull String appId, String envId, @NotNull String orchestrationId,
      String pipelineExecutionId, @NotNull ExecutionArgs executionArgs, Trigger trigger);

  WorkflowExecution triggerOrchestrationWorkflowExecution(String appId, String envId, String orchestrationId,
      String pipelineExecutionId, @NotNull ExecutionArgs executionArgs, WorkflowExecutionUpdate workflowExecutionUpdate,
      Trigger trigger);

  WorkflowExecution getExecutionDetails(
      @NotNull String appId, @NotNull String workflowExecutionId, boolean upToDate, boolean withFailureDetails);

  WorkflowExecution getExecutionWithoutSummary(@NotNull String appId, @NotNull String workflowExecutionId);

  WorkflowExecution getWorkflowExecution(@NotNull String appId, @NotNull String workflowExecutionId);

  WorkflowExecution getUpdatedWorkflowExecution(@NotNull String appId, @NotNull String workflowExecutionId);

  String getPipelineExecutionId(@NotNull String appId, @NotNull String workflowExecutionId);

  WorkflowExecution getExecutionDetailsWithoutGraph(String appId, String workflowExecutionId);

  WorkflowExecution triggerEnvExecution(String appId, String envId, ExecutionArgs executionArgs, Trigger trigger);

  WorkflowExecution triggerPipelineResumeExecution(
      String appId, int parallelIndexToResume, WorkflowExecution workflowExecution);

  WorkflowExecution triggerPipelineResumeExecution(String appId, String stageName, WorkflowExecution workflowExecution);

  List<PipelineStageGroupedInfo> getResumeStages(String appId, WorkflowExecution workflowExecution);

  List<WorkflowExecution> getResumeHistory(String appId, WorkflowExecution workflowExecution);

  WorkflowExecution triggerRollbackExecutionWorkflow(
      String appId, WorkflowExecution workflowExecution, boolean fromPipe);

  RollbackConfirmation getOnDemandRollbackConfirmation(String appId, WorkflowExecution workflowExecution);

  ExecutionInterrupt triggerExecutionInterrupt(@Valid ExecutionInterrupt executionInterrupt);

  void incrementInProgressCount(String appId, String workflowExecutionId, int inc);

  void incrementSuccess(String appId, String workflowExecutionId, int inc);

  void incrementFailed(String appId, String workflowExecutionId, Integer inc);

  RequiredExecutionArgs getRequiredExecutionArgs(String appId, String envId, ExecutionArgs executionArgs);

  WorkflowVariablesMetadata fetchWorkflowVariables(
      String appId, ExecutionArgs executionArgs, String workflowExecutionId, String pipelineStageElementId);

  DeploymentMetadata fetchDeploymentMetadataRunningPipeline(String appId, Map<String, String> workflowVariables,
      boolean withDefaultArtifact, String workflowExecutionId, String pipelineStageElementId);

  DeploymentMetadata fetchDeploymentMetadata(String appId, ExecutionArgs executionArgs, boolean withDefaultArtifact,
      String workflowExecutionId, boolean withLastDeployedInfo);

  DeploymentMetadata fetchDeploymentMetadata(@NotEmpty String appId, @NotNull ExecutionArgs executionArgs);

  CountsByStatuses getBreakdown(String appId, String workflowExecutionId);

  GraphNode getExecutionDetailsForNode(String appId, String workflowExecutionId, String stateExecutionInstanceId);

  List<StateExecutionData> getExecutionHistory(
      String appId, String workflowExecutionId, String stateExecutionInstanceId);

  List<StateExecutionInterrupt> getExecutionInterrupts(String appId, String stateExecutionInstanceId);

  List<StateExecutionElement> getExecutionElements(String appId, String stateExecutionInstanceId);

  Map<String, GraphGroup> getNodeSubGraphs(
      String appId, String workflowExecutionId, Map<String, List<String>> selectedNodes);

  int getExecutionInterruptCount(String stateExecutionInstanceId);

  StateExecutionInstance getStateExecutionData(String appId, String stateExecutionInstanceId);

  List<StateExecutionInstance> getStateExecutionData(String appId, String executionUuid, String serviceId,
      String infraMappingId, Optional<String> infrastructureDefinitionId, StateType stateType, String stateName);

  List<InfrastructureMapping> getResolvedInfraMappings(Workflow workflow, WorkflowExecution workflowExecution);

  List<InfrastructureDefinition> getResolvedInfraDefinitions(
      Workflow workflow, WorkflowExecution workflowExecution, String envId);

  List<ElementExecutionSummary> getElementsSummary(
      String appId, String executionUuid, String parentStateExecutionInstanceId);

  PhaseExecutionSummary getPhaseExecutionSummary(String appId, String executionUuid, String stateExecutionInstanceId);

  PhaseStepExecutionSummary getPhaseStepExecutionSummary(
      String appId, String executionUuid, String stateExecutionInstanceId);

  boolean workflowExecutionsRunning(WorkflowType workflowType, String appId, String workflowId);

  boolean runningExecutionsPresent(String appId, String entityId);

  void updateStartStatus(String appId, String workflowExecutionId, ExecutionStatus status, boolean shouldUpdateStartTs);

  void updateWorkflowElementWithLastGoodReleaseInfo(
      String appId, WorkflowElement workflowElement, String workflowExecutionId);

  boolean updateNotes(String appId, String workflowExecutionId, ExecutionArgs executionArgs);

  boolean approveOrRejectExecution(
      String appId, List<String> userGroupIds, ApprovalDetails approvalDetails, String executionUuid);

  boolean approveOrRejectExecution(
      String appId, List<String> userGroupIds, ApprovalDetails approvalDetails, ApiKeyEntry apiEntryKey);

  ApprovalStateExecutionData fetchApprovalStateExecutionDataFromWorkflowExecution(
      String appId, String workflowExecutionId, String stateExecutionId, ApprovalDetails approvalDetails);

  List<ApprovalStateExecutionData> fetchApprovalStateExecutionsDataFromWorkflowExecution(
      String appId, String workflowExecutionId);

  List<HelmChart> getManifestsCollected(String appId, String executionUuid);

  List<Artifact> getArtifactsCollected(String appId, String executionUuid);

  List<StateExecutionInstance> getStateExecutionInstances(String appId, String executionUuid);

  List<StateExecutionInstance> getStateExecutionInstancesForPhases(String executionUuid);

  void refreshBuildExecutionSummary(String workflowExecutionId, BuildExecutionSummary buildExecutionSummary);

  Set<WorkflowExecutionBaseline> markBaseline(String appId, String workflowExecutionId, boolean isBaseline);

  WorkflowExecutionBaseline getBaselineDetails(
      String appId, String workflowExecutionId, String stateExecutionId, String currentExecId);

  List<WorkflowExecution> obtainWorkflowExecutions(
      String accountId, List<String> appIds, long fromDateEpochMilli, String[] projectedKeys);

  List<WorkflowExecution> obtainWorkflowExecutions(String accountId, long fromDateEpochMilli, String[] projectedKeys);

  HIterator<WorkflowExecution> obtainWorkflowExecutionIterator(
      String accountId, List<String> appIds, long epochMilli, String[] projectedKeys);

  List<Artifact> obtainLastGoodDeployedArtifacts(@NotEmpty String appId, @NotEmpty String workflowId);

  List<Artifact> obtainLastGoodDeployedArtifacts(@NotEmpty String appId, @NotEmpty String workflowId, String serviceId);

  List<Artifact> obtainLastGoodDeployedArtifacts(
      WorkflowExecution workflowExecution, List<String> infraMappingList, boolean useInfraMappingBasedRollbackArtifact);

  List<ArtifactVariable> obtainLastGoodDeployedArtifactsVariables(String appId, String workflowId);

  WorkflowExecution fetchWorkflowExecution(
      String appId, List<String> serviceIds, List<String> envIds, String workflowId);

  boolean verifyAuthorizedToAcceptOrReject(List<String> userGroupIds, String appId, String workflowId);

  boolean verifyAuthorizedToAcceptOrReject(
      List<String> userGroupIds, List<String> apiKeysUserGroupIds, String appId, String workflowId);

  List<WorkflowExecution> listWaitingOnDeployments(String appId, String workflowExecutionId);

  Long fetchWorkflowExecutionStartTs(String appId, String workflowExecutionId);

  ApprovalAuthorization getApprovalAuthorization(String appId, List<String> userGroupIds);

  WorkflowExecution getWorkflowExecutionSummary(String appId, String workflowExecutionId);
  WorkflowExecution getWorkflowExecutionForVerificationService(String appId, String workflowExecutionId);

  void refreshCollectedArtifacts(String appId, String pipelineExecutionId, String workflowExecutionId);

  StateMachine obtainStateMachine(WorkflowExecution workflowExecution);

  WorkflowExecution fetchLastWorkflowExecution(
      @NotNull String appId, @NotNull String workflowId, String serviceId, String envId);

  PageResponse<WorkflowExecution> fetchWorkflowExecutionList(
      @NotNull String appId, @NotNull String workflowId, @NotNull String envId, int pageOffset, int pageLimit);

  String getApplicationIdByExecutionId(@NotNull String executionId);

  List<WorkflowExecution> getLastSuccessfulWorkflowExecutions(String appId, String workflowId, String serviceId);

  boolean appendInfraMappingId(String appId, String workflowExecutionId, String infraMappingId);

  boolean isTriggerBasedDeployment(ExecutionContext context);

  List<WorkflowExecution> getLatestExecutionsFor(
      String appId, String infraMappingId, int limit, List<String> fieldList, boolean forInclusion);

  void refreshHelmExecutionSummary(String workflowExecutionId, HelmExecutionSummary helmExecutionSummary);

  void refreshAwsLambdaExecutionSummary(
      String workflowExecutionId, List<AwsLambdaExecutionSummary> awsLambdaExecutionSummaries);

  ConcurrentExecutionResponse fetchConcurrentExecutions(
      String appId, String workflowExecutionId, String resourceConstraintName, String unit);

  Map<String, Object> extractServiceInfrastructureDetails(String appId, WorkflowExecution execution);

  int getInstancesDeployedFromExecution(WorkflowExecution workflowExecution);

  List<EnvSummary> getEnvironmentsForExecution(WorkflowExecution workflowExecution);

  List<String> getServiceIdsForExecution(WorkflowExecution workflowExecution);

  List<String> getCloudProviderIdsForExecution(WorkflowExecution workflowExecution);

  List<WorkflowExecution> fetchWorkflowExecutionsForResourceConstraint(List<String> entityIds);

  boolean getOnDemandRollbackAvailable(String appId, WorkflowExecution lastSuccessfulWE, boolean fromPipe);

  boolean checkIfOnDemand(String appId, String workflowExecutionId);

  WorkflowExecutionInfo getWorkflowExecutionInfo(String workflowExecutionId);

  boolean isMultiService(String appId, String workflowExecutionId);

  void addTagFilterToPageRequest(PageRequest<WorkflowExecution> pageRequest, String tagFilter);

  Map<String, String> getDeploymentTags(String accountId, List<NameValuePair> tags);

  Set<String> getWorkflowExecutionsWithTag(String accountId, String key, String value);

  List<String> runningExecutionsForEnvironment(String appId, String environmentId);

  List<String> runningExecutionsForApplication(String appId);

  List<String> runningExecutionsForService(String appId, String serviceId);

  List<WorkflowExecution> getRunningExecutionsForInfraDef(@NotEmpty String appId, @NotEmpty String infraDefinitionId);

  List<HelmChart> obtainLastGoodDeployedHelmCharts(String appId, String workflowId);

  boolean continuePipelineStage(
      String appId, String pipelineExecutionId, String pipelineStageElementId, ExecutionArgs executionArgs);

  StateExecutionInstance getStateExecutionInstancePipelineStage(
      String appId, String pipelineExecutionId, String pipelineStageElementId);

  boolean checkWorkflowExecutionInFinalStatus(String appId, String workflowExecutionId);

  ExecutionStatus fetchWorkflowExecutionStatus(String appId, String workflowExecutionId);

  WorkflowExecution fetchWorkflowExecution(String appId, String workflowExecutionId, String... projectedFields);

  String fetchFailureDetails(String appId, String workflowExecutionId);

  void populateFailureDetails(WorkflowExecution workflowExecution);

  List<WorkflowExecution> getLatestSuccessWorkflowExecutions(String appId, String workflowId, List<String> serviceIds,
      int executionsToSkip, int executionsToIncludeInResponse);

  WorkflowExecution getLastWorkflowExecution(
      String accountId, String appId, String workflowId, String envId, String serviceId, String infraMappingId);

  PreviousApprovalDetails getPreviousApprovalDetails(
      String appId, String workflowExecutionId, String pipelineId, String approvalId);

  Boolean approveAndRejectPreviousExecutions(String accountId, String appId, String workflowExecutionId,
      String stateExecutionId, ApprovalDetails approvalDetails, PreviousApprovalDetails previousApprovalIds);

  void rejectPreviousDeployments(String appId, String workflowExecutionId, ApprovalDetails approvalDetails);

  WorkflowExecution getLastSuccessfulWorkflowExecution(
      String accountId, String appId, String workflowId, String envId, String serviceId, String infraMappingId);

  WorkflowExecutionInfo getWorkflowExecutionInfo(String appId, String workflowExecutionId);

  WorkflowExecution getWorkflowExecutionWithFailureDetails(@NotNull String appId, @NotNull String workflowExecutionId);

  List<WorkflowExecution> getWorkflowExecutionsWithFailureDetails(
      String appId, List<WorkflowExecution> workflowExecutions);

  void checkDeploymentFreezeRejectedExecution(
      String accountId, PreDeploymentChecker deploymentFreezeChecker, WorkflowExecution workflowExecution);
}
