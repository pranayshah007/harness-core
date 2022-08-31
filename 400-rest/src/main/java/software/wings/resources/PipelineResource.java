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

import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DUPLICATE_STATE_NAMES;
import static io.harness.exception.WingsException.ReportTarget.LOG_SYSTEM;

import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;

import software.wings.beans.EntityType;
import software.wings.beans.FailureStrategy;
import software.wings.beans.Pipeline;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateTypeScope;
import software.wings.stencils.Stencil;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.EnumSet;
import java.util.List;
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
 * The Class PipelineResource.
 *
 * @author Rishi
 */
@Api("pipelines")
@Path("/pipelines")
@Produces("application/json")
@Scope(APPLICATION)
@AuthRule(permissionType = PermissionType.PIPELINE)
@OwnedBy(HarnessTeam.CDC)
public class PipelineResource {
  private WorkflowService workflowService;
  private PipelineService pipelineService;
  private AuthService authService;

  /**
   * Instantiates a new pipeline resource.
   *
   * @param workflowService the workflow service
   * @param pipelineService the pipeline service
   * @param authService the auth service
   */
  @Inject
  public PipelineResource(WorkflowService workflowService, PipelineService pipelineService, AuthService authService) {
    this.workflowService = workflowService;
    this.pipelineService = pipelineService;
    this.authService = authService;
  }

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Pipeline>> list(@QueryParam("appId") List<String> appIds,
      @BeanParam PageRequest<Pipeline> pageRequest,
      @QueryParam("previousExecutionsCount") Integer previousExecutionsCount,
      @QueryParam("details") @DefaultValue("true") boolean details, @QueryParam("tagFilter") String tagFilter,
      @QueryParam("withTags") @DefaultValue("false") boolean withTags) {
    if (isNotEmpty(appIds)) {
      pageRequest.addFilter("appId", IN, appIds.toArray());
    }

    return new RestResponse<>(
        pipelineService.listPipelines(pageRequest, details, previousExecutionsCount, withTags, tagFilter));
  }

  /**
   * Read.
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @return the rest response
   */
  @GET
  @Path("{pipelineId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Pipeline> read(@QueryParam("appId") String appId, @PathParam("pipelineId") String pipelineId,
      @QueryParam("withServices") boolean withServices,
      @QueryParam("withVariables") @DefaultValue("false") boolean withVariables) {
    if (withVariables) {
      return new RestResponse<>(pipelineService.readPipelineWithVariables(appId, pipelineId));
    }
    return new RestResponse<>(pipelineService.readPipeline(appId, pipelineId, withServices));
  }

  /**
   * Creates the.
   *
   * @param appId    the app id
   * @param pipeline the pipeline
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Pipeline> create(@QueryParam("appId") String appId, Pipeline pipeline) {
    pipeline.setAppId(appId);
    authService.checkPipelinePermissionsForEnv(appId, pipeline, Action.CREATE);
    return new RestResponse<>(pipelineService.save(pipeline));
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @param pipeline   the pipeline
   * @return the rest response
   */
  @PUT
  @Path("{pipelineId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Pipeline> update(
      @QueryParam("appId") String appId, @PathParam("pipelineId") String pipelineId, Pipeline pipeline) {
    pipeline.setAppId(appId);
    pipeline.setUuid(pipelineId);
    authService.checkPipelinePermissionsForEnv(appId, pipeline, Action.UPDATE);
    try {
      return new RestResponse<>(pipelineService.update(pipeline, false, false));
    } catch (WingsException exception) {
      // When the pipeline update is coming from the user there is no harness engineer wrong doing to alerted for
      exception.excludeReportTarget(DUPLICATE_STATE_NAMES, EnumSet.of(LOG_SYSTEM));
      throw exception;
    }
  }

  /**
   * Update.
   *
   * @param appId             the app id
   * @param pipelineId        the pipeline id
   * @param failureStrategies the failureStrategies
   * @return the rest response
   */
  @PUT
  @Path("{pipelineId}/failure-strategies")
  @Timed
  @ExceptionMetered
  public RestResponse<List<FailureStrategy>> updateFailureStrategies(@QueryParam("appId") String appId,
      @PathParam("pipelineId") String pipelineId, List<FailureStrategy> failureStrategies) {
    return new RestResponse<>(pipelineService.updateFailureStrategies(appId, pipelineId, failureStrategies));
  }

  @POST
  @Path("{pipelineId}/clone")
  @Timed
  @ExceptionMetered
  public RestResponse<Pipeline> clone(
      @QueryParam("appId") String appId, @PathParam("pipelineId") String pipelineId, Pipeline pipeline) {
    Pipeline originalPipeline = pipelineService.readPipeline(appId, pipelineId, false);
    pipeline.setAppId(appId);
    authService.checkPipelinePermissionsForEnv(appId, originalPipeline, Action.CREATE);
    return new RestResponse<>(pipelineService.clonePipeline(originalPipeline, pipeline));
  }

  /**
   * Delete.
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @param pipeline   the pipeline
   * @return the rest response
   */
  @DELETE
  @Path("{pipelineId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(
      @QueryParam("appId") String appId, @PathParam("pipelineId") String pipelineId, Pipeline pipeline) {
    pipelineService.deletePipeline(appId, pipelineId);
    return new RestResponse();
  }

  /**
   * Stencils rest response.
   *
   * @param appId the app id
   * @param envId the env id
   * @return the rest response
   */
  @GET
  @Path("stencils")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  public RestResponse<List<Stencil>> stencils(@QueryParam("appId") String appId, @QueryParam("envId") String envId) {
    return new RestResponse<>(workflowService.stencils(appId, null, null, StateTypeScope.PIPELINE_STENCILS)
                                  .get(StateTypeScope.PIPELINE_STENCILS));
  }

  /**
   * Required args rest response.
   *
   * @param appId         the app id
   * @param pipelineId    the pipelineId
   * @return the rest response
   */
  @GET
  @Path("required-entities")
  @Timed
  @ExceptionMetered
  public RestResponse<List<EntityType>> requiredEntities(
      @QueryParam("appId") String appId, @QueryParam("pipelineId") String pipelineId) {
    return new RestResponse<>(pipelineService.getRequiredEntities(appId, pipelineId));
  }
}
