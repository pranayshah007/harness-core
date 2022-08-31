/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DUPLICATE_STATE_NAMES;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.ReportTarget.LOG_SYSTEM;
import static io.harness.validation.PersistenceValidator.validateUuid;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import software.wings.api.InstanceElement;
import software.wings.beans.EntityType;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.NotificationRule;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.TrafficShiftMetadata;
import software.wings.beans.User;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowCategorySteps;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.stats.CloneMetadata;
import software.wings.common.VerificationConstants;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;
import software.wings.sm.StateTypeScope;
import software.wings.stencils.Stencil;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
/**
 * The Class OrchestrationResource.
 *
 * @author Rishi
 */
@Api("workflows")
@Path("/workflows")
@Produces("application/json")
@Scope(ResourceType.APPLICATION)
@AuthRule(permissionType = WORKFLOW)
@OwnedBy(HarnessTeam.CDC)
public class WorkflowResource {
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;

  private WorkflowService workflowService;
  private AuthService authService;

  /**
   * Instantiates a new orchestration resource.
   *
   * @param workflowService the workflow service
   * @param authService the auth service
   */
  @Inject
  public WorkflowResource(WorkflowService workflowService, AuthService authService) {
    this.workflowService = workflowService;
    this.authService = authService;
  }

  /**
   * List.
   *
   * @param appIds                   the app id
   * @param pageRequest             the page request
   * @param previousExecutionsCount the previous executions count
   * @param workflowTypes           the workflow types
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.WORKFLOW, action = Action.READ)
  public RestResponse<PageResponse<Workflow>> list(@QueryParam("appId") List<String> appIds,
      @BeanParam PageRequest<Workflow> pageRequest,
      @QueryParam("previousExecutionsCount") Integer previousExecutionsCount,
      @QueryParam("workflowType") List<String> workflowTypes,
      @QueryParam("details") @DefaultValue("true") boolean details,
      @QueryParam("withArtifactStreamSummary") @DefaultValue("false") boolean withArtifactStreamSummary,
      @QueryParam("tagFilter") String tagFilter, @QueryParam("withTags") @DefaultValue("false") boolean withTags) {
    if ((isEmpty(workflowTypes))
        && (pageRequest.getFilters() == null
            || pageRequest.getFilters().stream().noneMatch(
                searchFilter -> searchFilter.getFieldName().equals("workflowType")))) {
      pageRequest.addFilter("workflowType", EQ, WorkflowType.ORCHESTRATION);
    }
    if (isNotEmpty(appIds)) {
      pageRequest.addFilter("appId", IN, appIds.toArray());
    }

    if (!details) {
      return new RestResponse<>(workflowService.listWorkflowsWithoutOrchestration(pageRequest));
    }

    PageResponse<Workflow> pageResponse =
        workflowService.listWorkflows(pageRequest, previousExecutionsCount, withTags, tagFilter);
    if (withArtifactStreamSummary) {
      if (pageResponse != null && isNotEmpty(pageResponse.getResponse())) {
        for (Workflow workflow : pageResponse.getResponse()) {
          OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
          if (orchestrationWorkflow != null) {
            // Add artifact stream summary to artifact variables
            artifactStreamServiceBindingService.processVariables(orchestrationWorkflow.getUserVariables());
          }
        }
      }
    }
    return new RestResponse<>(pageResponse);
  }

  /**
   *
   * @param minAutoscaleInstances
   * @param maxAutoscaleInstances
   * @param targetCpuUtilizationPercentage
   * @return
   */
  @GET
  @Path("hpa-metric-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getHPAYamlStringWithCustomMetric(@QueryParam("appId") String appId,
      @QueryParam("minAutoscaleInstances") Integer minAutoscaleInstances,
      @QueryParam("maxAutoscaleInstances") Integer maxAutoscaleInstances,
      @QueryParam("targetCpuUtilizationPercentage") Integer targetCpuUtilizationPercentage) {
    if (minAutoscaleInstances == null) {
      minAutoscaleInstances = Integer.valueOf(0);
    }
    if (maxAutoscaleInstances == null) {
      maxAutoscaleInstances = Integer.valueOf(0);
    }
    if (targetCpuUtilizationPercentage == null) {
      targetCpuUtilizationPercentage = Integer.valueOf(80);
    }
    return new RestResponse<>(workflowService.getHPAYamlStringWithCustomMetric(
        minAutoscaleInstances, maxAutoscaleInstances, targetCpuUtilizationPercentage));
  }

  /**
   * Read.
   *
   * @param appId      the app id
   * @param workflowId the workflow id
   * @param version    the version
   * @return the rest response
   */
  @GET
  @Path("{workflowId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Workflow> read(@QueryParam("appId") String appId, @PathParam("workflowId") String workflowId,
      @QueryParam("version") Integer version,
      @QueryParam("withArtifactStreamSummary") @DefaultValue("false") boolean withArtifactStreamSummary) {
    Workflow workflow = workflowService.readWorkflow(appId, workflowId, version);
    if (withArtifactStreamSummary) {
      OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
      if (orchestrationWorkflow != null) {
        // Add artifact stream summary to artifact variables
        artifactStreamServiceBindingService.processVariables(orchestrationWorkflow.getUserVariables());
      }
    }
    return new RestResponse<>(workflow);
  }

  @GET
  @Path("{workflowId}/traffic-shift-metadata")
  @Timed
  @ExceptionMetered
  public RestResponse<TrafficShiftMetadata> readTrafficShiftMetadata(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId) {
    return new RestResponse<>(workflowService.readWorkflowTrafficShiftMetadata(appId, workflowId));
  }

  /**
   * Creates a workflow
   *
   * @param appId    the app id
   * @param workflow the workflow
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Workflow> create(@QueryParam("appId") String appId, Workflow workflow) {
    workflow.setAppId(appId);
    workflow.setWorkflowType(WorkflowType.ORCHESTRATION);
    try {
      authService.checkWorkflowPermissionsForEnv(appId, workflow, Action.CREATE);
      Workflow wf = workflowService.createWorkflow(workflow);
      return new RestResponse<>(wf);
    } catch (WingsException e) {
      if (e.getCode() == ErrorCode.USAGE_LIMITS_EXCEEDED) {
        throw new WingsException(ErrorCode.USAGE_LIMITS_EXCEEDED,
            "You have reached the maximum number of allowed Workflows. Please contact Harness Support.",
            WingsException.USER);
      }
      throw e;
    }
  }

  @PUT
  @Path("{workflowId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Workflow> update(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, WorkflowVersion workflowVersion) {
    return new RestResponse<>(workflowService.updateWorkflow(appId, workflowId, workflowVersion.getDefaultVersion()));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class WorkflowVersion {
    private Integer defaultVersion;

    public Integer getDefaultVersion() {
      return defaultVersion;
    }

    public void setDefaultVersion(Integer defaultVersion) {
      this.defaultVersion = defaultVersion;
    }
  }

  /**
   * Delete.
   *
   * @param appId      the app id
   * @param workflowId the orchestration id
   * @return the rest response
   */
  @DELETE
  @Path("{workflowId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("workflowId") String workflowId) {
    workflowService.deleteWorkflow(appId, workflowId);
    return new RestResponse();
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param workflowId the workflow id
   * @param workflow   the workflow
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/basic")
  @Timed
  @ExceptionMetered
  public RestResponse<Workflow> updatePreDeployment(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, Workflow workflow) {
    validateUuid(workflow, "workflowId", workflowId);
    authService.checkWorkflowPermissionsForEnv(appId, workflow, Action.UPDATE);
    workflow.setAppId(appId);
    return new RestResponse<>(workflowService.updateWorkflow(workflow, null, false));
  }

  /**
   * Clone workflow rest response.
   *
   * @param appId      the app id
   * @param workflowId the workflow id
   * @param cloneMetadata   the workflow
   * @return the rest response
   */
  @POST
  @Path("{workflowId}/clone")
  @Timed
  @ExceptionMetered
  public RestResponse<Workflow> cloneWorkflow(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, CloneMetadata cloneMetadata) {
    try {
      Workflow originalWorkflow = workflowService.readWorkflow(appId, workflowId);
      String targetAppId = cloneMetadata.getTargetAppId();
      if (targetAppId == null || targetAppId.equals(appId)) {
        authService.checkWorkflowPermissionsForEnv(appId, originalWorkflow, Action.CREATE);
      } else {
        authService.checkIfUserCanCloneWorkflowToOtherApp(targetAppId, originalWorkflow);
      }

      Workflow wf = workflowService.cloneWorkflow(appId, originalWorkflow, cloneMetadata);
      return new RestResponse<>(wf);
    } catch (WingsException e) {
      if (e.getCode() == ErrorCode.USAGE_LIMITS_EXCEEDED) {
        throw new WingsException(ErrorCode.USAGE_LIMITS_EXCEEDED,
            "You have reached the maximum number of allowed Workflows. Please contact Harness Support.",
            WingsException.USER);
      }

      throw e;
    }
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param workflowId the orchestration id
   * @param phaseStep  the pre-deployment steps
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/pre-deploy")
  @Timed
  @ExceptionMetered
  public RestResponse<PhaseStep> updatePreDeployment(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, PhaseStep phaseStep) {
    return new RestResponse<>(workflowService.updatePreDeployment(appId, workflowId, phaseStep));
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param workflowId the orchestration id
   * @param phaseStep  the pre-deployment steps
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/post-deploy")
  @Timed
  @ExceptionMetered
  public RestResponse<PhaseStep> updatePostDeployment(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, PhaseStep phaseStep) {
    return new RestResponse<>(workflowService.updatePostDeployment(appId, workflowId, phaseStep));
  }

  /**
   * Creates the phase.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param workflowPhase the phase
   * @return the rest response
   */
  @POST
  @Path("{workflowId}/phases")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = WORKFLOW, action = UPDATE)
  public RestResponse<WorkflowPhase> create(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, WorkflowPhase workflowPhase) {
    return new RestResponse<>(workflowService.createWorkflowPhase(appId, workflowId, workflowPhase));
  }

  /**
   * Creates the phase.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param workflowPhase the phase
   * @return the rest response
   */
  @POST
  @Path("{workflowId}/phases/clone")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = WORKFLOW, action = UPDATE)
  public RestResponse<WorkflowPhase> clone(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, WorkflowPhase workflowPhase) {
    return new RestResponse<>(workflowService.cloneWorkflowPhase(appId, workflowId, workflowPhase));
  }

  /**
   * Updates the phase.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param phaseId       the orchestration id
   * @param workflowPhase the phase
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/phases/{phaseId}")
  @Timed
  @ExceptionMetered
  public RestResponse<WorkflowPhase> update(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, @PathParam("phaseId") String phaseId, WorkflowPhase workflowPhase) {
    validateUuid(workflowPhase, "phaseId", phaseId);

    try {
      return new RestResponse<>(workflowService.updateWorkflowPhase(appId, workflowId, workflowPhase));
    } catch (WingsException exception) {
      // When the workflow update is coming from the user there is no harness engineer wrong doing to alerted for
      exception.excludeReportTarget(DUPLICATE_STATE_NAMES, EnumSet.of(LOG_SYSTEM));
      exception.excludeReportTarget(INVALID_ARGUMENT, EnumSet.of(LOG_SYSTEM));
      throw exception;
    }
  }

  /**
   * Updates the phase.
   *
   * @param appId                 the app id
   * @param workflowId            the orchestration id
   * @param phaseId               the orchestration id
   * @param rollbackWorkflowPhase the rollback workflow phase
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/phases/{phaseId}/rollback")
  @Timed
  @ExceptionMetered
  public RestResponse<WorkflowPhase> updateRollback(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, @PathParam("phaseId") String phaseId,
      WorkflowPhase rollbackWorkflowPhase) {
    return new RestResponse<>(
        workflowService.updateWorkflowPhaseRollback(appId, workflowId, phaseId, rollbackWorkflowPhase));
  }

  /**
   * Delete.
   *
   * @param appId      the app id
   * @param workflowId the orchestration id
   * @param phaseId    the orchestration id
   * @return the rest response
   */
  @DELETE
  @Path("{workflowId}/phases/{phaseId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = WORKFLOW, action = UPDATE)
  public RestResponse<WorkflowPhase> deletePhase(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, @PathParam("phaseId") String phaseId) {
    workflowService.deleteWorkflowPhase(appId, workflowId, phaseId);
    return new RestResponse();
  }

  /**
   * Updates the GraphNode.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param subworkflowId the subworkflow id
   * @param nodeId        the nodeId
   * @param node          the node
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/nodes/{nodeId}")
  @Timed
  @ExceptionMetered
  public RestResponse<GraphNode> updateGraphNode(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, @QueryParam("subworkflowId") String subworkflowId,
      @PathParam("nodeId") String nodeId, GraphNode node) {
    node.setId(nodeId);
    return new RestResponse<>(workflowService.updateGraphNode(appId, workflowId, subworkflowId, node));
  }

  /**
   * Updates the GraphNode.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param nodeId        the nodeId
   * @return the rest response
   */
  @GET
  @Path("{workflowId}/nodes/{nodeId}")
  @Timed
  @ExceptionMetered
  public RestResponse<GraphNode> readGraphNode(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, @PathParam("nodeId") String nodeId) {
    return new RestResponse<>(workflowService.readGraphNode(appId, workflowId, nodeId));
  }

  /**
   * Update.
   *
   * @param appId             the app id
   * @param workflowId        the orchestration id
   * @param notificationRules the notificationRules
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/notification-rules")
  @Timed
  @ExceptionMetered
  public RestResponse<List<NotificationRule>> updateNotificationRules(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, List<NotificationRule> notificationRules) {
    return new RestResponse<>(workflowService.updateNotificationRules(appId, workflowId, notificationRules));
  }

  @PUT
  @Path("{workflowId}/concurrency-strategy")
  @Timed
  @ExceptionMetered
  public RestResponse<ConcurrencyStrategy> concurrencyStrategy(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, ConcurrencyStrategy concurrencyStrategy) {
    return new RestResponse<>(workflowService.updateConcurrencyStrategy(appId, workflowId, concurrencyStrategy));
  }

  /**
   * Update.
   *
   * @param appId             the app id
   * @param workflowId        the orchestration id
   * @param failureStrategies the failureStrategies
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/failure-strategies")
  @Timed
  @ExceptionMetered
  public RestResponse<List<FailureStrategy>> updateFailureStrategies(@QueryParam("appId") String appId,
      @PathParam("workflowId") String workflowId, List<FailureStrategy> failureStrategies) {
    return new RestResponse<>(workflowService.updateFailureStrategies(appId, workflowId, failureStrategies));
  }

  /**
   * Update.
   *
   * @param appId         the app id
   * @param workflowId    the orchestration id
   * @param userVariables the user variables
   * @return the rest response
   */
  @PUT
  @Path("{workflowId}/user-variables")
  @Timed
  @ExceptionMetered
  public RestResponse<List<Variable>> updateUserVariables(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId, List<Variable> userVariables) {
    return new RestResponse<>(workflowService.updateUserVariables(appId, workflowId, userVariables));
  }

  /**
   * Stencils rest response.
   *
   * @param appId      the app id
   * @param envId      the env id
   * @param workflowId the workflow id
   * @param phaseId    the phase id
   * @return the rest response
   */
  @GET
  @Path("stencils")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<Stencil>> stencils(@QueryParam("appId") String appId, @QueryParam("envId") String envId,
      @QueryParam("workflowId") String workflowId, @QueryParam("phaseId") String phaseId) {
    return new RestResponse<>(
        workflowService.stencils(appId, workflowId, phaseId, StateTypeScope.ORCHESTRATION_STENCILS)
            .get(StateTypeScope.ORCHESTRATION_STENCILS));
  }

  /**
   * Stencils rest response.
   *
   * @param appId      the app id
   * @param serviceId the workflow id
   * @param strStateType    the state type
   * @return the rest response
   */
  @GET
  @Path("state-defaults")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> stateDefaults(@QueryParam("appId") String appId,
      @QueryParam("serviceId") String serviceId, @QueryParam("stateType") String strStateType) {
    if (isEmpty(strStateType)) {
      return new RestResponse<>();
    }
    return new RestResponse<>(workflowService.getStateDefaults(appId, serviceId, StateType.valueOf(strStateType)));
  }

  @GET
  @Path("{workflowId}/infra-types")
  @Timed
  public RestResponse<Boolean> workflowHasSSHInfraMapping(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId) {
    return new RestResponse(workflowService.workflowHasSshDeploymentPhase(appId, workflowId));
  }

  @GET
  @Path("{workflowId}/deployed-nodes")
  @Timed
  @ExceptionMetered
  public RestResponse<List<InstanceElement>> getDeployedNodes(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId) {
    return new RestResponse<>(workflowService.getDeployedNodes(appId, workflowId));
  }

  @GET
  @Path("steps/phase/{phaseId}/sections/{sectionId}/{position}")
  @Timed
  @ExceptionMetered
  public RestResponse<WorkflowCategorySteps> getSteps(@QueryParam("accountId") String accountId,
      @PathParam("phaseId") String phaseId, @PathParam("sectionId") String sectionId,
      @PathParam("position") int position, @QueryParam("rollbackSection") boolean rollbackSection,
      @QueryParam("appId") String appId, @QueryParam("workflowId") String workflowId) {
    final User user = UserThreadLocal.get();
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    notNullCheck("Workflow does not exist", workflow);
    notNullCheck("Orchestration workflow does not exist", workflow.getOrchestrationWorkflow());
    final WorkflowCategorySteps steps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, sectionId, position, rollbackSection);
    return new RestResponse<>(steps);
  }

  @GET
  @Path("required-entities")
  @Timed
  @ExceptionMetered
  public RestResponse<List<EntityType>> requiredEntities(
      @QueryParam("appId") String appId, @QueryParam("workflowId") String workflowId) {
    return new RestResponse<>(workflowService.getRequiredEntities(appId, workflowId));
  }

  @GET
  @Path(VerificationConstants.LAST_SUCCESSFUL_WORKFLOW_IDS)
  @Timed
  @LearningEngineAuth
  public RestResponse<List<String>> getLastSuccessfulWorkflowExecutionIds(@QueryParam("appId") String appId,
      @QueryParam("workflowId") String workflowId, @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(workflowService.getLastSuccessfulWorkflowExecutionIds(appId, workflowId, serviceId));
  }

  @GET
  @Path(VerificationConstants.CHECK_STATE_VALID)
  @Timed
  @LearningEngineAuth
  public RestResponse<Boolean> isStateValid(
      @QueryParam("appId") String appId, @QueryParam("stateExecutionId") String stateExecutionId) {
    return new RestResponse<>(workflowService.isStateValid(appId, stateExecutionId));
  }

  @GET
  @Path(VerificationConstants.WORKFLOW_FOR_STATE_EXEC)
  @Timed
  @LearningEngineAuth
  public RestResponse<WorkflowExecution> getWorkflowExecutionForStateExecution(
      @QueryParam("appId") String appId, @QueryParam("stateExecutionId") String stateExecutionId) {
    final WorkflowExecution workflowExecution =
        workflowService.getWorkflowExecutionForStateExecutionId(appId, stateExecutionId);
    workflowExecution.setStateMachine(null);
    return new RestResponse<>();
  }
}
