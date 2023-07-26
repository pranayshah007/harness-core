/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_WFE_PROJECTIONS_DEPLOYMENTS_PAGE;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.common.InfrastructureConstants.QUEUING_RC_NAME;
import static software.wings.security.PermissionAttribute.Action.EXECUTE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_PIPELINE;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.WorkflowType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.state.inspection.StateInspection;
import io.harness.state.inspection.StateInspectionService;
import io.harness.time.EpochUtils;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.ApprovalAuthorization;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.beans.PipelineStageGroupedInfo;
import software.wings.beans.RequiredExecutionArgs;
import software.wings.beans.StateExecutionElement;
import software.wings.beans.StateExecutionInterrupt;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.approval.ApproveAndRejectPreviousDeploymentsBody;
import software.wings.beans.approval.PreviousApprovalDetails;
import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.beans.concurrency.ConcurrentExecutionResponse;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.features.DeploymentHistoryFeature;
import software.wings.features.api.RestrictedFeature;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.WorkflowExecutionOptimizationHelper;
import software.wings.service.impl.WorkflowExecutionTimeFilterHelper;
import software.wings.service.impl.pipeline.resume.PipelineResumeUtils;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.RollbackConfirmation;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * The Class ExecutionResource.
 */
@OwnedBy(CDC)
@Api("executions")
@Path("/executions")
@Scope(ResourceType.APPLICATION)
@Produces("application/json")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ExecutionResource {
  @Inject private AppService appService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private StateInspectionService stateInspectionService;
  @Inject private DeploymentAuthHandler deploymentAuthHandler;
  @Inject private AuthService authService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject @Named(DeploymentHistoryFeature.FEATURE_NAME) private RestrictedFeature deploymentHistoryFeature;
  @Inject private WorkflowExecutionTimeFilterHelper workflowExecutionTimeFilterHelper;
  @Inject private WorkflowExecutionOptimizationHelper workflowExecutionOptimizationHelper;
  private static final String EXECUTION_DOES_NOT_EXIST = "No workflow execution exists for id: ";
  public static List<String> nonRequiredFields = ImmutableList.of(WorkflowExecutionKeys.awsLambdaExecutionSummaries,
      WorkflowExecutionKeys.buildExecutionSummaries, WorkflowExecutionKeys.deployedCloudProviders,
      WorkflowExecutionKeys.helmExecutionSummary, WorkflowExecutionKeys.keywords, WorkflowExecutionKeys.isBaseline,
      WorkflowExecutionKeys.message, WorkflowExecutionKeys.cdPageCandidate);

  /**
   * List.
   *
   * @param appIds           the app ids
   * @param envId           the env id
   * @param orchestrationId the orchestration id
   * @param pageRequest     the page request
   * @param includeGraph    the include graph\
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, parameterName = "orchestrationId", dbFieldName = "workflowId",
      dbCollectionName = "software.wings.beans.WorkflowExecution", skipAuth = true)
  public RestResponse<PageResponse<WorkflowExecution>>
  listExecutions(@QueryParam("accountId") String accountId, @QueryParam("appId") List<String> appIds,
      @QueryParam("envId") String envId, @QueryParam("orchestrationId") String orchestrationId,
      @BeanParam PageRequest<WorkflowExecution> pageRequest,
      @DefaultValue("false") @QueryParam("includeGraph") boolean includeGraph,
      @QueryParam("workflowType") List<String> workflowTypes,
      @DefaultValue("false") @QueryParam("includeIndirectExecutions") boolean includeIndirectExecutions,
      @QueryParam("tagFilter") String tagFilter, @QueryParam("withTags") @DefaultValue("false") boolean withTags) {
    // NOTE: Any new filters added here should also be added in ExportExecutionsResource.
    workflowExecutionOptimizationHelper.enforceAppIdFromChildrenEntities(pageRequest, accountId);
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest, accountId);

    List<String> authorizedAppIds;
    if (isNotEmpty(appIds)) {
      authorizedAppIds = appIds;
      pageRequest.addFilter("appId", Operator.IN, authorizedAppIds.toArray());
    }

    if (pageRequest.getPageSize() > ResourceConstants.DEFAULT_RUNTIME_ENTITY_PAGESIZE) {
      pageRequest.setLimit(ResourceConstants.DEFAULT_RUNTIME_ENTITY_PAGESIZE_STR);
    }

    if (isNotEmpty(workflowTypes)) {
      pageRequest.addFilter("workflowType", Operator.IN, workflowTypes.toArray());
    }

    // No need to show child executions unless includeIndirectExecutions is true
    if (!includeIndirectExecutions) {
      pageRequest.addFilter(WorkflowExecutionKeys.cdPageCandidate, Operator.EQ, Boolean.TRUE);
    } else {
      PipelineResumeUtils.addLatestPipelineResumeFilter(pageRequest);
    }

    if (isNotBlank(orchestrationId)) {
      pageRequest.addFilter("workflowId", Operator.EQ, orchestrationId);
    }

    if (withTags && isNotBlank(tagFilter)) {
      workflowExecutionService.addTagFilterToPageRequest(pageRequest, tagFilter);
    }

    Optional<Integer> retentionPeriodInDays =
        ((DeploymentHistoryFeature) deploymentHistoryFeature).getRetentionPeriodInDays(accountId);
    retentionPeriodInDays.ifPresent(val
        -> pageRequest.addFilter(WorkflowExecutionKeys.startTs, GE,
            EpochUtils.calculateEpochMilliOfStartOfDayForXDaysInPastFromNow(val, "UTC")));

    // With skipcount option, we won't have count query while fetching the executions.
    List<PageRequest.Option> options = pageRequest.getOptions();
    if (options == null) {
      options = new ArrayList<>();
    }
    options.add(PageRequest.Option.SKIPCOUNT);
    pageRequest.setOptions(options);
    // We will ask for one more than limit, and if its not exactly one more, we know we are at the end of the list.
    pageRequest.setLimit(Integer.toString(Integer.parseInt(pageRequest.getLimit()) + 1));

    if (featureFlagService.isEnabled(SPG_WFE_PROJECTIONS_DEPLOYMENTS_PAGE, accountId)) {
      pageRequest.setFieldsExcluded(nonRequiredFields);
    }

    PageResponse<WorkflowExecution> workflowExecutions =
        workflowExecutionService.listExecutions(pageRequest, includeGraph, true, true, false, true, true);

    int offset = Integer.parseInt(pageRequest.getOffset());
    int limit = Integer.parseInt(pageRequest.getLimit()) - 1;
    workflowExecutions.setTotal(offset + (long) workflowExecutions.size());
    if (workflowExecutions.size() == limit + 1) {
      workflowExecutions.remove(workflowExecutions.size() - 1);
    }
    workflowExecutions.setLimit(Integer.toString(limit));

    workflowExecutions.forEach(we -> we.setStateMachine(null));
    return new RestResponse<>(workflowExecutions);
  }

  /**
   * Gets execution details.
   *
   * @param appId               the app id
   * @param envId               the env id
   * @param workflowExecutionId the workflow execution id
   * @return the execution details
   */
  @GET
  @Path("{workflowExecutionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<WorkflowExecution> getExecutionDetails(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @PathParam("workflowExecutionId") String workflowExecutionId) {
    final WorkflowExecution workflowExecution =
        workflowExecutionService.getExecutionDetails(appId, workflowExecutionId, false, true);
    workflowExecution.setStateMachine(null);
    authService.authorizeAppAccess(
        workflowExecution.getAccountId(), workflowExecution.getAppId(), UserThreadLocal.get(), Action.READ);
    return new RestResponse<>(workflowExecution);
  }

  /**
   * Save.
   *
   * @param appId         the app id
   * @param envId         the env id
   * @param executionArgs the execution args
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  // We are handling the check programmatically for now, since we don't have enough info in the query / path parameters
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<WorkflowExecution> triggerExecution(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @QueryParam("pipelineId") String pipelineId, ExecutionArgs executionArgs) {
    if (pipelineId != null && executionArgs != null && executionArgs.getWorkflowType() == WorkflowType.PIPELINE) {
      executionArgs.setPipelineId(pipelineId);
    }
    // Moved Authorization checks to WorkflowExecutionServiceImpl where pipeline and workflow is already read and we
    // have env resolved to validate RBAC.
    if (executionArgs != null) {
      if (isNotEmpty(executionArgs.getArtifactVariables())) {
        for (ArtifactVariable artifactVariable : executionArgs.getArtifactVariables()) {
          if (isEmpty(artifactVariable.getValue()) && artifactVariable.getArtifactStreamMetadata() == null
              && artifactVariable.getArtifactInput() == null) {
            throw new InvalidRequestException(
                format("No value provided for artifact variable: [%s] ", artifactVariable.getName()), USER);
          }
        }
      }
      executionArgs.setCreatedByType(CreatedByType.USER);
    }

    final WorkflowExecution workflowExecution =
        workflowExecutionService.triggerEnvExecution(appId, envId, executionArgs, null);
    workflowExecution.setStateMachine(null);
    return new RestResponse<>(workflowExecution);
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("continueExecution")
  // We are handling the check programmatically for now, since we don't have enough info in the query / path parameters
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE_PIPELINE, skipAuth = true)
  public RestResponse<Boolean> continueExecution(@QueryParam("appId") String appId,
      @QueryParam("pipelineStageElementId") String pipelineStageElementId,
      @QueryParam("pipelineExecutionId") String pipelineExecutionId, ExecutionArgs executionArgs) {
    // add auth
    if (executionArgs != null && isNotEmpty(executionArgs.getArtifactVariables())) {
      for (ArtifactVariable artifactVariable : executionArgs.getArtifactVariables()) {
        if (isEmpty(artifactVariable.getValue()) && artifactVariable.getArtifactStreamMetadata() == null
            && artifactVariable.getArtifactInput() == null) {
          throw new InvalidRequestException(
              format("No value provided for artifact variable: [%s] ", artifactVariable.getName()), USER);
        }
      }
    }
    executionArgs.setCreatedByType(CreatedByType.USER);

    return new RestResponse<>(workflowExecutionService.continuePipelineStage(
        appId, pipelineExecutionId, pipelineStageElementId, executionArgs));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("triggerResume")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<WorkflowExecution> triggerPipelineResumeExecution(@QueryParam("appId") String appId,
      @QueryParam("parallelIndexToResume") int parallelIndexToResume,
      @QueryParam("workflowExecutionId") String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.appId, WorkflowExecutionKeys.createdAt,
        WorkflowExecutionKeys.envId, WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.latestPipelineResume,
        WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.pipelineResumeId,
        WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowId,
        WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);

    notNullCheck(EXECUTION_DOES_NOT_EXIST + workflowExecutionId, workflowExecution);
    deploymentAuthHandler.authorize(appId, workflowExecution);
    if (workflowExecution.getExecutionArgs() != null) {
      workflowExecution.getExecutionArgs().setCreatedByType(CreatedByType.USER);
    }
    WorkflowExecution resumedExecution =
        workflowExecutionService.triggerPipelineResumeExecution(appId, parallelIndexToResume, workflowExecution);
    resumedExecution.setStateMachine(null);
    return new RestResponse<>(resumedExecution);
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("resumeStages")
  @AuthRule(permissionType = DEPLOYMENT, action = READ)
  public RestResponse<List<PipelineStageGroupedInfo>> getResumeStages(
      @QueryParam("appId") String appId, @QueryParam("workflowExecutionId") String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.executionArgs,
        WorkflowExecutionKeys.latestPipelineResume, WorkflowExecutionKeys.pipelineExecution,
        WorkflowExecutionKeys.pipelineResumeId, WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowId,
        WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);
    notNullCheck(EXECUTION_DOES_NOT_EXIST + workflowExecutionId, workflowExecution);
    return new RestResponse<>(workflowExecutionService.getResumeStages(appId, workflowExecution));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("resumeHistory")
  @AuthRule(permissionType = DEPLOYMENT, action = READ)
  public RestResponse<List<WorkflowExecution>> getResumeHistory(
      @QueryParam("appId") String appId, @QueryParam("workflowExecutionId") String workflowExecutionId) {
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId,
        WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.pipelineResumeId, WorkflowExecutionKeys.workflowType);
    notNullCheck(EXECUTION_DOES_NOT_EXIST + workflowExecutionId, workflowExecution);
    return new RestResponse<>(workflowExecutionService.getResumeHistory(appId, workflowExecution));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("triggerRollback")
  // We are handling the check programmatically for now, since we don't have enough info in the query / path parameters
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<WorkflowExecution> triggerRollbackExecution(
      @QueryParam("appId") String appId, @QueryParam("workflowExecutionId") String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.artifacts,
        WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.appId, WorkflowExecutionKeys.infraMappingIds,
        WorkflowExecutionKeys.name, WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.pipelineExecutionId,
        WorkflowExecutionKeys.status, WorkflowExecutionKeys.workflowType, WorkflowExecutionKeys.infraDefinitionIds,
        WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.envId};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);
    notNullCheck("No Workflow Execution exist for Id: " + workflowExecutionId, workflowExecution);
    deploymentAuthHandler.authorizeRollback(appId, workflowExecution);
    WorkflowExecution rollbackWorkflowExecution =
        workflowExecutionService.triggerRollbackExecutionWorkflow(appId, workflowExecution, false);
    rollbackWorkflowExecution.setStateMachine(null);
    return new RestResponse<>(rollbackWorkflowExecution);
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("rollbackConfirmation")
  // We are handling the check programmatically for now, since we don't have enough info in the query / path parameters
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<RollbackConfirmation> getRollbackConfirmation(
      @QueryParam("appId") String appId, @QueryParam("workflowExecutionId") String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.appId, WorkflowExecutionKeys.artifacts,
        WorkflowExecutionKeys.envId, WorkflowExecutionKeys.infraDefinitionIds, WorkflowExecutionKeys.infraMappingIds,
        WorkflowExecutionKeys.name, WorkflowExecutionKeys.onDemandRollback, WorkflowExecutionKeys.serviceIds,
        WorkflowExecutionKeys.status, WorkflowExecutionKeys.useSweepingOutputs, WorkflowExecutionKeys.uuid,
        WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);
    notNullCheck("No Workflow Execution exist for Id: " + workflowExecutionId, workflowExecution);
    deploymentAuthHandler.authorizeRollback(appId, workflowExecution);
    RollbackConfirmation rollbackConfirmation =
        workflowExecutionService.getOnDemandRollbackConfirmation(appId, workflowExecution);
    return new RestResponse<>(rollbackConfirmation);
  }

  /**
   * Update execution details rest response.
   *
   * @param appId               the app id
   * @param workflowExecutionId the workflow execution id
   * @param executionInterrupt      the execution event
   * @return the rest response
   */
  @PUT
  @Path("{workflowExecutionId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<ExecutionInterrupt> triggerWorkflowExecutionInterrupt(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, ExecutionInterrupt executionInterrupt) {
    executionInterrupt.setAppId(appId);
    executionInterrupt.setExecutionUuid(workflowExecutionId);
    WorkflowExecution workflowExecution =
        workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecutionId);
    if (ExecutionInterruptType.ROLLBACK.equals(executionInterrupt.getExecutionInterruptType())
        || ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES.equals(
            executionInterrupt.getExecutionInterruptType())) {
      deploymentAuthHandler.authorizeRollback(appId, workflowExecutionId);
    } else if ((ExecutionInterruptType.ABORT_ALL.equals(executionInterrupt.getExecutionInterruptType())
                   || ExecutionInterruptType.ABORT.equals(executionInterrupt.getExecutionInterruptType()))
        && workflowExecution.getWorkflowType().equals(WorkflowType.ORCHESTRATION)) {
      deploymentAuthHandler.authorizeAbortWorkflow(appId, workflowExecution);
    } else {
      deploymentAuthHandler.authorize(appId, workflowExecutionId);
    }
    return new RestResponse<>(workflowExecutionService.triggerExecutionInterrupt(executionInterrupt));
  }

  /**
   * Trigger execution rest response.
   *
   * @param appId         the app id
   * @param workflowExecutionId    the workflowExecutionId
   * @param executionArgs the Execution Args
   * @return the rest response
   */
  @PUT
  @Path("{workflowExecutionId}/notes")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<Boolean> updateNotes(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, ExecutionArgs executionArgs) {
    deploymentAuthHandler.authorize(appId, workflowExecutionId);
    return new RestResponse<>(workflowExecutionService.updateNotes(appId, workflowExecutionId, executionArgs));
  }

  /**
   * Trigger execution rest response.
   *
   * @param appId         the app id
   * @param workflowExecutionId    the workflowExecutionId
   * @param approvalDetails the Approval User details
   * @return the rest response
   */
  @PUT
  @Path("{workflowExecutionId}/approval")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse approveOrRejectExecution(@QueryParam("appId") String appId,
      @QueryParam("stateExecutionId") String stateExecutionId,
      @PathParam("workflowExecutionId") String workflowExecutionId, ApprovalDetails approvalDetails) {
    ApprovalStateExecutionData approvalStateExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
            appId, workflowExecutionId, stateExecutionId, approvalDetails);

    if (!ApprovalStateType.USER_GROUP.equals(approvalStateExecutionData.getApprovalStateType())) {
      throw new InvalidRequestException(
          approvalStateExecutionData.getApprovalStateType() + " Approval Type not supported", USER);
    }

    String accountId = appService.getAccountIdByAppId(appId);

    if (approvalStateExecutionData.isAutoRejectPreviousDeployments()
        && approvalDetails.getAction() == ApprovalDetails.Action.APPROVE
        && featureFlagService.isEnabled(FeatureName.AUTO_REJECT_PREVIOUS_APPROVALS, accountId)) {
      workflowExecutionService.rejectPreviousDeployments(appId, workflowExecutionId, approvalDetails);
    }

    if (isEmpty(approvalStateExecutionData.getUserGroups())) {
      deploymentAuthHandler.authorize(appId, workflowExecutionId);
    }

    approvalStateExecutionData.setExecutionUuid(workflowExecutionId);

    return new RestResponse<>(workflowExecutionService.approveOrRejectExecution(
        appId, approvalStateExecutionData.getUserGroups(), approvalDetails, workflowExecutionId));
  }

  /**
   * Required args rest response.
   *
   * @param appId         the app id
   * @param envId         the env id
   * @param executionArgs the execution args
   * @return the rest response
   */
  @POST
  @Path("required-args")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<RequiredExecutionArgs> requiredArgs(
      @QueryParam("appId") String appId, @QueryParam("envId") String envId, ExecutionArgs executionArgs) {
    return new RestResponse<>(workflowExecutionService.getRequiredExecutionArgs(appId, envId, executionArgs));
  }

  @POST
  @Path("workflow-variables")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<WorkflowVariablesMetadata> getWorkflowVariables(@QueryParam("appId") String appId,
      @QueryParam("workflowExecutionId") String workflowExecutionId,
      @QueryParam("pipelineStageElementId") String pipelineStageElementId, ExecutionArgs executionArgs) {
    return new RestResponse<>(workflowExecutionService.fetchWorkflowVariables(
        appId, executionArgs, workflowExecutionId, pipelineStageElementId));
  }

  @POST
  @Path("deployment-metadata")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<DeploymentMetadata> getDeploymentMetadata(@QueryParam("appId") String appId,
      @QueryParam("withDefaultArtifact") boolean withDefaultArtifact,
      @QueryParam("workflowExecutionId") String workflowExecutionId,
      @DefaultValue("false") @QueryParam("isRunningExecution") boolean isRunningExecution,
      @QueryParam("pipelineStageElementId") String pipelineStageElementId,
      @QueryParam("withLastDeployedInfo") boolean withLastDeployedInfo, ExecutionArgs executionArgs) {
    if (isRunningExecution) {
      return new RestResponse<>(workflowExecutionService.fetchDeploymentMetadataRunningPipeline(appId,
          executionArgs.getWorkflowVariables(), withDefaultArtifact, workflowExecutionId, pipelineStageElementId));
    }
    return new RestResponse<>(workflowExecutionService.fetchDeploymentMetadata(
        appId, executionArgs, withDefaultArtifact, workflowExecutionId, withLastDeployedInfo));
  }

  /**
   * Gets execution node details.
   *
   * @param appId                    the app id
   * @param envId                    the env id
   * @param workflowExecutionId      the workflow execution id
   * @param stateExecutionInstanceId the state execution instance id
   * @return the execution node details
   */
  @GET
  @Path("{workflowExecutionId}/node/{stateExecutionInstanceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<GraphNode> getExecutionNodeDetails(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @PathParam("workflowExecutionId") String workflowExecutionId,
      @PathParam("stateExecutionInstanceId") String stateExecutionInstanceId) {
    return new RestResponse<>(
        workflowExecutionService.getExecutionDetailsForNode(appId, workflowExecutionId, stateExecutionInstanceId));
  }

  /**
   * Gets execution history list.
   *
   * @param appId                    the app id
   * @param workflowExecutionId      the workflow execution id
   * @param stateExecutionInstanceId the state execution instance id
   * @return the execution history list
   */
  @GET
  @Path("{workflowExecutionId}/history/{stateExecutionInstanceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<List<StateExecutionData>> getExecutionHistory(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId,
      @PathParam("stateExecutionInstanceId") String stateExecutionInstanceId) {
    return new RestResponse<>(
        workflowExecutionService.getExecutionHistory(appId, workflowExecutionId, stateExecutionInstanceId));
  }

  /**
   * Gets execution history list.
   *
   * @param appId                    the app id
   * @param workflowExecutionId      the workflow execution id
   * @param stateExecutionInstanceId the state execution instance id
   * @return the execution history list
   */
  @GET
  @Path("{workflowExecutionId}/interruption/{stateExecutionInstanceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<List<StateExecutionInterrupt>> getExecutionInterrupt(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId,
      @PathParam("stateExecutionInstanceId") String stateExecutionInstanceId) {
    return new RestResponse<>(workflowExecutionService.getExecutionInterrupts(appId, stateExecutionInstanceId));
  }

  /**
   * Gets execution history list.
   *
   * @param appId                    the app id
   * @param workflowExecutionId      the workflow execution id
   * @param stateExecutionInstanceId the state execution instance id
   * @return the execution history list
   */
  @GET
  @Path("{workflowExecutionId}/inspection/{stateExecutionInstanceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<StateInspection> getExecutionInspection(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId,
      @PathParam("stateExecutionInstanceId") String stateExecutionInstanceId) {
    return new RestResponse<>(stateInspectionService.get(stateExecutionInstanceId));
  }

  /**
   * Gets execution history list.
   *
   * @param appId                    the app id
   * @param workflowExecutionId      the workflow execution id
   * @param stateExecutionInstanceId the state execution instance id
   * @return the execution history list
   */
  @GET
  @Path("{workflowExecutionId}/element/{stateExecutionInstanceId}")
  @Timed
  @ExceptionMetered
  public RestResponse<List<StateExecutionElement>> getExecutionElement(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId,
      @PathParam("stateExecutionInstanceId") String stateExecutionInstanceId) {
    return new RestResponse<>(workflowExecutionService.getExecutionElements(appId, stateExecutionInstanceId));
  }

  /**
   * Marks the pipeline as baseline for verification steps
   * @param appId
   * @param workflowExecutionId
   * @return
   */
  @GET
  @Path("{workflowExecutionId}/mark-baseline")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<Set<WorkflowExecutionBaseline>> markAsBaseline(@QueryParam("appId") String appId,
      @QueryParam("isBaseline") boolean isBaseline, @PathParam("workflowExecutionId") String workflowExecutionId) {
    deploymentAuthHandler.authorize(appId, workflowExecutionId);
    return new RestResponse<>(workflowExecutionService.markBaseline(appId, workflowExecutionId, isBaseline));
  }

  /**
   * gets the details for baseline execution
   * @param appId
   * @param baselineExecutionId
   * @return
   */
  @GET
  @Path("{workflowExecutionId}/get-baseline")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<WorkflowExecutionBaseline> getBaselineDetails(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String baselineExecutionId,
      @QueryParam("stateExecutionId") String stateExecutionId, @QueryParam("currentExecId") String currentExecId) {
    return new RestResponse<>(
        workflowExecutionService.getBaselineDetails(appId, baselineExecutionId, stateExecutionId, currentExecId));
  }

  @GET
  @Path("artifacts")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<List<Artifact>> getLastDeployedArtifacts(
      @QueryParam("appId") String appId, @QueryParam("workflowId") String workflowId) {
    return new RestResponse<>(workflowExecutionService.obtainLastGoodDeployedArtifacts(appId, workflowId));
  }

  /**
   * Returns the list of either running or queued executions on the requested
   * execution
   * @param appId
   * @param workflowExecutionId
   * @return
   */
  @GET
  @Path("{workflowExecutionId}/waitingOnDeployments")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<List<WorkflowExecution>> getWaitingOnDeployments(
      @QueryParam("appId") String appId, @PathParam("workflowExecutionId") String workflowExecutionId) {
    final List<WorkflowExecution> workflowExecutions =
        workflowExecutionService.listWaitingOnDeployments(appId, workflowExecutionId);

    workflowExecutions.forEach(we -> we.setStateMachine(null));

    return new RestResponse<>(workflowExecutions);
  }

  @GET
  @Path("{workflowExecutionId}/approvalAuthorization")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<ApprovalAuthorization> getApprovalAuthorization(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, @QueryParam("userGroups") List<String> userGroups) {
    // In case of approvals with no user groups, we want to make sure that the user has permission to access that
    // workflow/pipeline. Below empty check does that
    notNullCheck("App cannot be null", appId);

    if (isEmpty(userGroups)) {
      try {
        deploymentAuthHandler.authorize(appId, workflowExecutionId);
      } catch (WingsException e) {
        ApprovalAuthorization approvalAuthorization = new ApprovalAuthorization();
        approvalAuthorization.setAuthorized(false);
        return new RestResponse<>(approvalAuthorization);
      }
    }

    return new RestResponse<>(workflowExecutionService.getApprovalAuthorization(appId, userGroups));
  }

  @GET
  @Path("{workflowExecutionId}/constraint-executions")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<ConcurrentExecutionResponse> getExecutionsForConstraint(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, @QueryParam("unit") String unit,
      @QueryParam("resourceConstraintName") String resourceConstraintName) {
    notNullCheck("App cant be null", appId);
    if (resourceConstraintName == null) {
      resourceConstraintName = QUEUING_RC_NAME;
    }
    return new RestResponse<>(
        workflowExecutionService.fetchConcurrentExecutions(appId, workflowExecutionId, resourceConstraintName, unit));
  }

  /**
   * Gets SubGraphs for selected nodes
   *
   * @param appId                    the app id
   * @param workflowExecutionId      the workflow execution id*
   * @param selectedNodes            Map of parent Id (RepeaterId) to list of selected hosts.
   * @return the execution history list
   */
  @POST
  @Path("nodeSubGraphs/{workflowExecutionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = READ, skipAuth = true)
  public RestResponse<Map<String, GraphGroup>> getNodeSubGraphs(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, Map<String, List<String>> selectedNodes) {
    if (isEmpty(selectedNodes)) {
      return new RestResponse<>();
    }
    return new RestResponse<>(workflowExecutionService.getNodeSubGraphs(appId, workflowExecutionId, selectedNodes));
  }

  @GET
  @Path("info/{workflowExecutionId}")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<WorkflowExecutionInfo> getWorkflowExecutionInfo(
      @PathParam("workflowExecutionId") String workflowExecutionId) {
    if (isEmpty(workflowExecutionId)) {
      throw new InvalidRequestException("workflowExecutionId is required", USER);
    }
    return new RestResponse<>(workflowExecutionService.getWorkflowExecutionInfo(workflowExecutionId));
  }

  @GET
  @Path("{workflowExecutionId}/previousApprovalDetails")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE_PIPELINE, skipAuth = true)
  public RestResponse<PreviousApprovalDetails> getPreviousApprovalDetails(@QueryParam("appId") String appId,
      @PathParam("workflowExecutionId") String workflowExecutionId, @QueryParam("pipelineId") String workflowId,
      @QueryParam("approvalId") String approvalId) {
    return new RestResponse<>(
        workflowExecutionService.getPreviousApprovalDetails(appId, workflowExecutionId, workflowId, approvalId));
  }

  @POST
  @Path("{workflowExecutionId}/approveAndRejectPreviousDeployments")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE_PIPELINE, skipAuth = true)
  public RestResponse<Boolean> approveAndRejectPreviousDeployments(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("stateExecutionId") String stateExecutionId,
      @PathParam("workflowExecutionId") String workflowExecutionId,
      ApproveAndRejectPreviousDeploymentsBody approveAndRejectPreviousDeploymentsBody) {
    return new RestResponse<>(workflowExecutionService.approveAndRejectPreviousExecutions(accountId, appId,
        workflowExecutionId, stateExecutionId, approveAndRejectPreviousDeploymentsBody.getApprovalDetails(),
        approveAndRejectPreviousDeploymentsBody.getPreviousApprovalDetails()));
  }
}
