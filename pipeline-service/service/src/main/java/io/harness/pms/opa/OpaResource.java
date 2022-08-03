/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.opa.service.PMSOpaService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.IOException;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Api("/opa")
@Path("/opa")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class OpaResource {
  @Inject private final PMSOpaService pmsOpaService;

  @POST
  @ApiOperation(value = "get pipeline opa context", nickname = "getPipelineOpaContext")
  @Path("/getPipelineOpaContext")
  public ResponseDTO<PipelineOpaEvaluationContext> getPipelineOpaContext(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineId,
      @NotNull @QueryParam("opaAction") String action, @ApiParam(hidden = true) String inputSetPipelineYaml) {
    try {
      return ResponseDTO.newResponse(pmsOpaService.getPipelineContext(
          accountId, orgIdentifier, projectIdentifier, pipelineId, inputSetPipelineYaml, action));
    } catch (IOException ex) {
      log.error("Exception while evaluation pipeline opa context", ex);
      throw new InvalidRequestException(ex.getMessage(), ex);
    }
  }

  @GET
  @ApiOperation(value = "get pipeline opa context from evaluation", nickname = "getPipelineOpaContextFromEvaluation")
  @Path("/getPipelineOpaContextFromEvaluation/{planExecutionId}")
  public ResponseDTO<PipelineOpaEvaluationContext> getPipelineOpaContextFromEvaluation(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("opaAction") String action,
      @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    try {
      return ResponseDTO.newResponse(pmsOpaService.getPipelineContextFromExecution(
          accountId, orgIdentifier, projectIdentifier, planExecutionId, action));
    } catch (Exception ex) {
      log.error("Exception while evaluation pipeline opa context", ex);
      throw new InvalidRequestException(ex.getMessage(), ex);
    }
  }
}
