/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.plan.execution.beans.dto.ExecutionInputDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionInputStatus;
import io.harness.pms.plan.execution.beans.dto.ExecutionInputStatusDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionInputVariablesResponseDTO;
import io.harness.pms.plan.execution.mapper.ExecutionInputDTOMapper;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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

//@Tag(name = "Execution-input", description = "This contains APIs for execution input of a pipeline execution.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.PIPELINE)
@Api("/pipeline/execution-input")
@Path("/pipeline/execution-input")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class ExecutionInputResource {
  @Inject private final VariableCreatorMergeService variableCreatorMergeService;
  @Inject private final ExecutionInputService executionInputService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;

  @GET
  @Path("/{nodeExecutionId}")
  @ApiOperation(
      value = "Get the template for Execution time inputs for any step", nickname = "getExecutionInputTemplate")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionInputTemplate",
      summary = "Get the template for Execution time inputs for any stage/step",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Return the template for Execution time inputs for any Stage/Step")
      })
  @Hidden
  public ResponseDTO<ExecutionInputDTO>
  getExecutionInputTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @PathParam("nodeExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE) String nodeExecutionId) {
    ExecutionInputInstance executionInputInstance = executionInputService.getExecutionInputInstance(nodeExecutionId);
    if (executionInputInstance == null) {
      throw new InvalidRequestException(
          String.format("Execution Input template does not exist for input execution id : %s", nodeExecutionId));
    }
    return ResponseDTO.newResponse(ExecutionInputDTOMapper.toExecutionInputDTO(executionInputInstance));
  }

  @POST
  @Path("/{nodeExecutionId}")
  @ApiOperation(value = "Submit the Execution Input for a Stage/Step and continue Pipeline execution",
      nickname = "submitExecutionInput")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "submitExecutionInput",
      summary = "Submit the Execution Input for a Stage/Step and continue execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns if Execution Input is valid or not")
      })
  @Hidden
  public ResponseDTO<ExecutionInputStatusDTO>
  submitExecutionInput(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @PathParam("nodeExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE) String nodeExecutionId,
      @RequestBody(required = true,
          description = "Execution Input for the provided nodeExecutionId") @NotNull String executionInputYaml) {
    boolean isInputProcessed = executionInputService.continueExecution(nodeExecutionId, executionInputYaml);
    return ResponseDTO.newResponse(
        ExecutionInputStatusDTO.builder()
            .nodeExecutionId(nodeExecutionId)
            .status(isInputProcessed ? ExecutionInputStatus.Success : ExecutionInputStatus.Failed)
            .build());
  }

  @POST
  @Path("/variables")
  @Operation(operationId = "createVariablesForPipelineExecution",
      summary = "Get all the Variables which can be used as expression in the Pipeline at execution time.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns all Variables used that are valid to be used as expression in pipeline at execution time.")
      })
  @ApiOperation(
      value = "Create variables for Pipeline at execution time", nickname = "createVariablesForPipelineExecution")
  @Hidden
  // This API is different from PipelineResource variable apis as at execution time we want to refer the yaml used for
  // execution.
  public ResponseDTO<ExecutionInputVariablesResponseDTO>
  listVariablesDuringExecution(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    log.info("Creating variables for pipeline execution.");
    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    if (planExecutionMetadataOptional.isPresent()) {
      VariableMergeServiceResponse variablesResponse =
          variableCreatorMergeService.createVariablesResponses(planExecutionMetadataOptional.get().getYaml(), false);
      return ResponseDTO.newResponse(ExecutionInputVariablesResponseDTO.builder()
                                         .variableMergeServiceResponse(variablesResponse)
                                         .pipelineYaml(planExecutionMetadataOptional.get().getYaml())
                                         .build());
    } else {
      log.error("Pipeline for planExecutionId {} is deleted or not does not exist.", planExecutionId);
      throw new InvalidRequestException(
          "Pipeline for planExecutionId " + planExecutionId + " is deleted or not does not exist.");
    }
  }
}
