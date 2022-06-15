/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.beans.FeatureName.NG_PIPELINE_TEMPLATE;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.preflight.PreFlightDTO;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Tag(name = "Pipeline Execution", description = "This contains APIs for Executing a Pipeline")
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
@Api("/pipeline/execute")
@Path("/pipeline/execute")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PlanExecutionResource {
  @Inject private final PipelineExecutor pipelineExecutor;
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final PreflightService preflightService;
  @Inject private final PMSPipelineService pmsPipelineService;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  @Inject private final PmsFeatureFlagHelper featureFlagService;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;

  @POST
  @Path("/{identifier}")
  @ApiOperation(
      value = "Execute a pipeline with inputSet pipeline YAML", nickname = "postPipelineExecuteWithInputSetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetYaml",
      description = "Execute a Pipeline with Runtime Input YAML",
      summary = "Execute a Pipeline with Runtime Input YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  public ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description =
              "Enter Runtime Input YAML if the Pipeline contains Runtime Inputs. Please refer to https://ngdocs.harness.io/article/f6yobn7iq0 and https://ngdocs.harness.io/article/1eishcolt3 to see how to generate Runtime Input YAML for a Pipeline.")
      String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @POST
  @Path("/{identifier}/v2")
  @ApiOperation(
      value = "Execute a pipeline with inputSet pipeline YAML V2", nickname = "postPipelineExecuteWithInputSetYamlv2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetYamlv2",
      summary = "Execute a pipeline with inputSet pipeline yaml V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details V2")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description =
              "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if pipeline does not contains runtime inputs")
      String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, true);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @POST
  @Path("/{identifier}/stages")
  @ApiOperation(value = "Execute a pipeline with inputSet pipeline yaml", nickname = "runStagesWithRuntimeInputYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postExecuteStages", summary = "Execute given Stages of a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Execute given Stages of a Pipeline with Runtime Input Yaml")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  runStagesWithRuntimeInputYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                                    description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO) {
    return ResponseDTO.newResponse(pipelineExecutor.runStagesWithRuntimeInputYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, runStageRequestDTO, false));
  }

  @POST
  @Path("/rerun/{originalExecutionId}/{identifier}/stages")
  @ApiOperation(value = "Rerun a pipeline with inputSet pipeline yaml", nickname = "rerunStagesWithRuntimeInputYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postReExecuteStages", summary = "Re-run given Stages of a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Re-run a given Stages Execution of a Pipeline")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  rerunStagesWithRuntimeInputYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @NotNull @PathParam("originalExecutionId")
      @Parameter(description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO) {
    return ResponseDTO.newResponse(pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId, runStageRequestDTO, false));
  }

  @POST
  @Path("/rerun/{originalExecutionId}/{identifier}")
  @ApiOperation(
      value = "Re Execute a pipeline with inputSet pipeline yaml", nickname = "rePostPipelineExecuteWithInputSetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "rePostPipelineExecuteWithInputSetYaml",
      summary = "Re Execute a pipeline with inputSet pipeline yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description =
              "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if pipeline does not contains runtime inputs")
      String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, inputSetPipelineYaml, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @POST
  @Path("/rerun/v2/{originalExecutionId}/{identifier}")
  @ApiOperation(value = "Re Execute a pipeline with inputSet pipeline yaml Version 2",
      nickname = "rePostPipelineExecuteWithInputSetYamlV2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "rePostPipelineExecuteWithInputSetYamlV2",
      summary = "Re Execute a pipeline with InputSet Pipeline YAML Version 2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description =
              "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if pipeline does not contains runtime inputs")
      String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, inputSetPipelineYaml, true);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @GET
  @Path("/{planExecutionId}/retryStages")
  @ApiOperation(value = "Get retry stages for failed pipeline", nickname = "getRetryStages")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "getRetryStages", summary = "Get retry stages for failed pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all retry stages from where we can retry the failed pipeline")
      })
  @Hidden
  public ResponseDTO<io.harness.engine.executions.retry.RetryInfo>
  getRetryStages(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                     description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "planExecutionId of the execution we want to retry") String planExecutionId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId));
  }

  @POST
  @Path("/{identifier}/inputSetList")
  @ApiOperation(
      value = "Execute a pipeline with input set references list", nickname = "postPipelineExecuteWithInputSetList")

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetList",
      description = "Execute a Pipeline with Input Set References",
      summary = "Execute a Pipeline with Input Set References",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details V2")
      })
  public ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetIdentifierList(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.runPipelineWithInputSetReferencesList(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, mergeInputSetRequestDTO.getInputSetReferences(),
            gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId());
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @POST
  @Path("rerun/{originalExecutionId}/{identifier}/inputSetList")
  @Operation(operationId = "rerunPipelineWithInputSetIdentifierList",
      summary = "Rerun a pipeline with given inputSet identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns details of new plan execution and git details if any")
      })
  @ApiOperation(
      value = "Execute a pipeline with input set references list", nickname = "rePostPipelineExecuteWithInputSetList")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetIdentifierList(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE,
          required = true) @QueryParam(NGCommonEntityConstants.MODULE_TYPE) String moduleType,
      @NotNull @Parameter(description = PipelineResourceConstants.ORIGINAL_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @PathParam("originalExecutionId") String originalExecutionId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = PipelineResourceConstants.USE_FQN_IF_ERROR_RESPONSE_ERROR_MESSAGE,
          required = true) @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @RequestBody(required = true, description = "InputSet reference details") @NotNull
      @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetReferencesList(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, mergeInputSetRequestDTO.getInputSetReferences(),
            gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId());
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @PUT
  @ApiOperation(value = "pause, resume or stop the pipeline executions", nickname = "handleInterrupt")
  @Path("/interrupt/{planExecutionId}")
  @Operation(operationId = "putHandleInterrupt", description = "Executes an Interrupt on a Given Execution",
      summary = "Execute an Interrupt",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Takes a possible Interrupt value and applies it onto the execution referred by the planExecutionId")
      })
  public ResponseDTO<InterruptDTO>
  handleInterrupt(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                      description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull @QueryParam("interruptType") PlanExecutionInterruptType executionInterruptType,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull @PathParam("planExecutionId")
      String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_EXECUTE);

    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, null));
  }

  // TODO(prashant) : This is a temp route for now merge it with the above. Need be done in sync with UI changes
  @PUT
  @ApiOperation(value = "pause, resume or stop the stage executions", nickname = "handleStageInterrupt")
  @Operation(operationId = "handleStageInterrupt", summary = "Handles the interrupt for a given stage in a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Takes a possible Interrupt value and applies it onto the given stage in the execution")
      })
  @Path("/interrupt/{planExecutionId}/{nodeExecutionId}")
  @Hidden
  public ResponseDTO<InterruptDTO>
  handleStageInterrupt(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                           required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @QueryParam("interruptType") PlanExecutionInterruptType executionInterruptType,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("planExecutionId") String planExecutionId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("nodeExecutionId") String nodeExecutionId) {
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @PUT
  @ApiOperation(value = "Ignore,Abort,MarkAsSuccess,Retry on post manual intervention",
      nickname = "handleManualInterventionInterrupt")
  @Path("/manualIntervention/interrupt/{planExecutionId}/{nodeExecutionId}")
  @Operation(operationId = "handleManualInterventionInterrupt",
      summary =
          "Handles Ignore,Abort,MarkAsSuccess,Retry on post manual intervention for a given execution with the given planExecutionId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Takes a possible Interrupt value and applies it onto the given stage in the execution")
      })
  @Hidden
  public ResponseDTO<InterruptDTO>
  handleManualInterventionInterrupt(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @QueryParam("interruptType") PlanExecutionInterruptType executionInterruptType,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("planExecutionId") String planExecutionId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("nodeExecutionId") String nodeExecutionId) {
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @POST
  @ApiOperation(value = "initiate pre flight check", nickname = "startPreflightCheck")
  @Path("/preflightCheck")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "startPreFlightCheck", summary = "Start Preflight Checks for a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Start Preflight Checks for a Pipeline, given a Runtime Input YAML. Returns Preflight Id")
      })
  @Hidden
  public ResponseDTO<String>
  startPreFlightCheck(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @ApiParam(hidden = true) @Parameter(
          description = "Runtime Input YAML to be sent for Pipeline execution") String inputSetPipelineYaml) {
    try {
      return ResponseDTO.newResponse(preflightService.startPreflightCheck(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetPipelineYaml));
    } catch (IOException ex) {
      log.error(format("Invalid YAML in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @GET
  @ApiOperation(value = "get preflight check response", nickname = "getPreflightCheckResponse")
  @Path("/getPreflightCheckResponse")
  @Operation(operationId = "getPreFlightCheckResponse", summary = "Get Preflight Checks Response for a Preflight Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Get Preflight Checks Response for a Preflight Id. May require Multiple Queries if Preflight is In Progress")
      })
  @Hidden
  public ResponseDTO<PreFlightDTO>
  getPreflightCheckResponse(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                                description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam("preflightCheckId") @Parameter(
          description = "Preflight Id from the start Preflight Checks API") String preflightCheckId,
      @ApiParam(hidden = true) String inputSetPipelineYaml) {
    return ResponseDTO.newResponse(preflightService.getPreflightCheckResponse(preflightCheckId));
  }

  @GET
  @ApiOperation(value = "get list of stages for stage execution", nickname = "getStagesExecutionList")
  @Path("/stagesExecutionList")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getStagesExecutionList", summary = "Get list of Stages to select for Stage executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns list of Stage identifiers with their names and stage dependencies")
      })
  @Hidden
  public ResponseDTO<List<StageExecutionResponse>>
  getStagesExecutionList(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                             description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist.",
          pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String yaml = pipelineEntity.getYaml();
    if (featureFlagService.isEnabled(accountId, NG_PIPELINE_TEMPLATE)
        && Boolean.TRUE.equals(optionalPipelineEntity.get().getTemplateReference())) {
      yaml = pipelineTemplateHelper
                 .resolveTemplateRefsInPipeline(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getYaml())
                 .getMergedPipelineYaml();
    }
    boolean shouldAllowStageExecutions;
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      shouldAllowStageExecutions = basicPipeline.isAllowStageExecutions();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage(), e);
    }

    if (!shouldAllowStageExecutions) {
      return ResponseDTO.newResponse(Collections.emptyList());
    }
    List<StageExecutionResponse> stageExecutionResponse = StageExecutionSelectorHelper.getStageExecutionResponse(yaml);

    return ResponseDTO.newResponse(stageExecutionResponse);
  }

  @POST
  @Path("retry/{identifier}")
  @ApiOperation(value = "Retry a executed pipeline with inputSet pipeline yaml", nickname = "retryPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "retryPipeline", summary = "Retry a executed pipeline with inputSet pipeline yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns execution details")
      })
  @Hidden
  public ResponseDTO<PlanExecutionResponseDto>
  retryPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String previousExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.RETRY_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RETRY_STAGES_PARAM_MESSAGE) List<String> retryStagesIdentifier,
      @QueryParam(NGCommonEntityConstants.RUN_ALL_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RUN_ALL_STAGES) @DefaultValue("true") boolean runAllStages,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @ApiParam(hidden = true) String inputSetPipelineYaml) {
    if (retryStagesIdentifier.size() == 0) {
      throw new InvalidRequestException("You need to select the stage to retry!!");
    }

    RetryInfo retryInfo = retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, previousExecutionId);
    if (!retryInfo.isResumable()) {
      throw new InvalidRequestException(retryInfo.getErrorMessage());
    }

    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.retryPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml,
        previousExecutionId, retryStagesIdentifier, runAllStages, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @GET
  @Path("retryHistory/{planExecutionId}")
  @ApiOperation(value = "Retry History for a given execution", nickname = "retryHistory")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "retryHistory", summary = "Retry History for a given execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns retry history execution details")
      })
  @Hidden
  public ResponseDTO<RetryHistoryResponseDto>
  getRetryHistory(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "planExecutionId of the execution of whose we need to find the retry history")
      String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryHistory(rootParentId));
  }

  @GET
  @Path("latestExecutionId/{planExecutionId}")
  @ApiOperation(value = "Latest ExecutionId from Retry Executions", nickname = "latestExecutionId")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "latestExecutionId", summary = "Latest ExecutionId from Retry Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns execution id of the latest execution from all retries")
      })
  @Hidden
  public ResponseDTO<RetryLatestExecutionResponseDto>
  getRetryLatestExecutionId(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description =
              "planExecutionId of the execution of whose we need to find the latest execution planExecutionId")
      String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryLatestExecutionId(rootParentId));
  }
}
