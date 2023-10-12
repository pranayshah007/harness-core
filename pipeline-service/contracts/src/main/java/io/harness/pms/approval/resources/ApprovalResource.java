/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceAuthorizationDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
@Tag(name = "Approvals", description = "This contains APIs related to Pipeline approvals")
@Api("approvals")
@Path("approvals")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
public interface ApprovalResource {
  String APPROVAL_PARAM_MESSAGE = "Approval Identifier for the entity";

  @GET
  @Path("/{approvalInstanceId}")
  @ApiOperation(value = "Gets an Approval Instance by identifier", nickname = "getApprovalInstance")
  @Operation(operationId = "getApprovalInstance", summary = "Gets an Approval Instance by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the saved Approval Instance")
      })
  @Hidden
  ResponseDTO<ApprovalInstanceResponseDTO>
  getApprovalInstance(@Parameter(description = APPROVAL_PARAM_MESSAGE) @NotEmpty @PathParam(
                          "approvalInstanceId") String approvalInstanceId,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId);

  @POST
  @Path("/{approvalInstanceId}/harness/activity")
  @ApiOperation(value = "Approve or Reject a Pipeline Execution", nickname = "addHarnessApprovalActivity")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "addHarnessApprovalActivity", summary = "Approve or Reject a Pipeline Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a newly added Harness Approval activity")
      })
  ResponseDTO<ApprovalInstanceResponseDTO>
  addHarnessApprovalActivity(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @Parameter(description = APPROVAL_PARAM_MESSAGE) @NotEmpty @PathParam(
          "approvalInstanceId") String approvalInstanceId,
      @Parameter(
          description = "Details of approval activity") @NotNull @Valid HarnessApprovalActivityRequestDTO request);

  @GET
  @Path("/{approvalInstanceId}/harness/authorization")
  @ApiOperation(value = "Gets a Harness Approval Instance authorization for the current user",
      nickname = "getHarnessApprovalInstanceAuthorization")
  @Operation(operationId = "getHarnessApprovalInstanceAuthorization",
      summary = "Gets a Harness Approval Instance authorization for the current user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the Harness Approval instance authorization for the current user")
      })
  @Hidden
  ResponseDTO<HarnessApprovalInstanceAuthorizationDTO>
  getHarnessApprovalInstanceAuthorization(@Parameter(description = APPROVAL_PARAM_MESSAGE) @NotEmpty @PathParam(
      "approvalInstanceId") String approvalInstanceId);

  @GET
  @Path("/stage-yaml-snippet")
  @ApiOperation(value = "Gets the initial yaml snippet for Approval stage", nickname = "getInitialStageYamlSnippet")
  @Hidden
  ResponseDTO<String> getInitialStageYamlSnippet(
      @Parameter(description = "Approval Type") @NotNull @QueryParam("approvalType") ApprovalType approvalType,
      @QueryParam("routingId") String routingId) throws IOException;
}
