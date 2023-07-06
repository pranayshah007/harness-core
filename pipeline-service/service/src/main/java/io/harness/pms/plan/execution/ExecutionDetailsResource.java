/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateInputsErrorResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineExecutionNotesDTO;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetail;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
@Api("pipelines/execution")
@Path("pipelines/execution")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = TemplateInputsErrorResponseDTO.class,
              message = "TemplateRefs Resolved failed in pipeline yaml.")
    })
@Tag(name = "Pipeline Execution Details", description = "This contains APIs for fetching Pipeline Execution Details")
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
@PipelineServiceAuth
@Slf4j
public class ExecutionDetailsResource {
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private final ExecutionHelper executionHelper;
  @Inject private final PlanExecutionMetadataService planExecutionMetadataService;

  @POST
  @Path("/summary")
  @ApiOperation(value = "Gets Executions list", nickname = "getListOfExecutions")
  @Operation(operationId = "getListOfExecutions",
      description = "Returns a List of Pipeline Executions with Specific Filter", summary = "List Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all the Executions of pipelines for given filter")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PipelineExecutionSummaryDTO>>
  getListOfExecutions(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("10") int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam("sort") List<String> sort,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @QueryParam("module") String moduleName,
      @RequestBody(description = "Returns a List of Pipeline Executions with Specific Filters",
          content =
          {
            @Content(mediaType = "application/json",
                examples = @ExampleObject(name = "List", summary = "Sample List Pipeline Executions",
                    value = PipelineAPIConstants.LIST_EXECUTIONS,
                    description = "Sample List Pipeline Executions JSON Payload"))
          }) FilterPropertiesDTO filterProperties,
      @QueryParam("status") List<ExecutionStatus> statusesList, @QueryParam("myDeployments") boolean myDeployments,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info("Get List of executions");
    Criteria criteria = pmsExecutionService.formCriteria(accountId, orgId, projectId, pipelineIdentifier,
        filterIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, moduleName, searchTerm, statusesList,
        myDeployments, false, true);
    Pageable pageRequest;
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    // NOTE: We are getting entity git details from git context and not pipeline entity as we'll have to make DB calls
    // to fetch them and each might have a different branch context so we cannot even batch them. The only data missing
    // because of this approach is objectId which UI doesn't use.
    Page<PipelineExecutionSummaryDTO> planExecutionSummaryDTOS =
        pmsExecutionService.getPipelineExecutionSummaryEntity(criteria, pageRequest)
            .map(e
                -> PipelineExecutionSummaryDtoMapper.toDto(e,
                    e.getEntityGitDetails() != null
                        ? e.getEntityGitDetails()
                        : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext())));

    return ResponseDTO.newResponse(planExecutionSummaryDTOS);
  }

  @POST
  @Path("/executionSummary")
  @ApiOperation(value = "Gets Executions Id list", nickname = "getListOfExecutionIdentifier")
  @Operation(operationId = "getListOfExecutionIdentifier",
      description = "Returns a List of Pipeline Executions Identifier with Specific Filter",
      summary = "List Execution Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all the Executions Identifier of pipelines for given filter")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PipelineExecutionIdentifierSummaryDTO>>
  getListOfExecutionIdentifier(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,

      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("10") int size, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info("Get List of executions");
    Criteria criteria = pmsExecutionService.formCriteria(
        accountId, orgId, projectId, pipelineIdentifier, null, null, null, null, Arrays.asList(), false, false, true);
    Pageable pageRequest;
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }

    pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));

    List<String> projections = Arrays.asList(PlanExecutionSummaryKeys.planExecutionId,
        PlanExecutionSummaryKeys.runSequence, PlanExecutionSummaryKeys.orgIdentifier,
        PlanExecutionSummaryKeys.pipelineIdentifier, PlanExecutionSummaryKeys.projectIdentifier);

    Page<PipelineExecutionIdentifierSummaryDTO> planExecutionSummaryDTOS =
        pmsExecutionService.getPipelineExecutionSummaryEntityWithProjection(criteria, pageRequest, projections)
            .map(e -> PipelineExecutionSummaryDtoMapper.toExecutionIdentifierDto(e));

    return ResponseDTO.newResponse(planExecutionSummaryDTOS);
  }

  // This API is used only for internal purpose currently to support IDP plugin to fetch the executions based on
  // Parametrised Operator on modules in filterProperties. This API only supports multiple accountId,orgId,
  // projectId,pipelineIdentifier (As list to support multiple pipeline identifiers) and filterProperties as filter
  // criteria to obtain the executions.
  @POST
  @Path("/v2/summary")
  @ApiModelProperty(hidden = true)
  @Hidden
  @ApiOperation(value = "Gets Executions list for multiple pipeline filters with OR operator",
      nickname = "getListOfExecutionsForMultiplePipelinesIdentifiersWithOrOperators")
  @Operation(operationId = "getListOfExecutionsForMultiplePipelinesIdentifiersWithOrOperators",
      description = "Returns a List of Pipeline Executions with Specific Filters", summary = "List Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all the Executions of pipelines for given filters")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PipelineExecutionSummaryDTO>>
  getListOfExecutionsWithOrOperator(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @Size(max = 20) List<String> pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("10") int size, @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @RequestBody(description = "Returns a List of Pipeline Executions with Specific Filters", content = {
        @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "List", summary = "Sample List Pipeline Executions",
                value = PipelineAPIConstants.LIST_EXECUTIONS,
                description = "Sample List Pipeline Executions JSON Payload"))
      }) FilterPropertiesDTO filterProperties) {
    log.info("Get List of executions");
    Criteria criteria = pmsExecutionService.formCriteriaOROperatorOnModules(accountId, orgId, projectId,
        pipelineIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, filterIdentifier);
    Pageable pageRequest;
    pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
    pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));

    // NOTE: We are getting entity git details from git context and not pipeline entity as we'll have to make DB calls
    // to fetch them and each might have a different branch context so we cannot even batch them. The only data missing
    // because of this approach is objectId which UI doesn't use.
    Page<PipelineExecutionSummaryDTO> planExecutionSummaryDTOS =
        pmsExecutionService.getPipelineExecutionSummaryEntity(criteria, pageRequest)
            .map(e
                -> PipelineExecutionSummaryDtoMapper.toDto(e,
                    e.getEntityGitDetails() != null
                        ? e.getEntityGitDetails()
                        : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext())));

    return ResponseDTO.newResponse(planExecutionSummaryDTOS);
  }

  @GET
  @Path("/v2/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Detail V2", nickname = "getExecutionDetailV2")
  @Operation(operationId = "getExecutionDetailV2",
      description = "Returns the Pipeline Execution Details for a Given PlanExecution ID",
      summary = "Fetch Execution Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Return the Pipeline Execution details for given PlanExecution Id without full graph if stageNodeId is null")
      })
  public ResponseDTO<PipelineExecutionDetailDTO>
  getExecutionDetailV2(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_ID_PARAM_MESSAGE) @QueryParam(
          "stageNodeId") String stageNodeId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "stageNodeExecutionId") String stageNodeExecutionId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "childStageNodeId") String childStageNodeId,
      @Parameter(description = PipelineResourceConstants.GENERATE_FULL_GRAPH_PARAM_MESSAGE) @QueryParam(
          "renderFullBottomGraph") Boolean renderFullBottomGraph,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }

    PipelineExecutionDetailDTO executionDetailDTO = executionHelper.getResponseDTO(stageNodeId, stageNodeExecutionId,
        childStageNodeId, renderFullBottomGraph, executionSummaryEntity, entityGitDetails);

    return ResponseDTO.newResponse(executionDetailDTO);
  }

  @GET
  @Path("/{planExecutionId}/evaluateExpression")
  @ApiOperation(value = "Gets Execution Expression evaluated", nickname = "getExpressionEvaluated")
  @Operation(operationId = "getExpressionEvaluated", description = "Returns the Map of evaluated Expression",
      summary = "Gets Execution Expression evaluated",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Map of evaluated Expression")
      })
  @Hidden
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<ExpressionEvaluationDetailDTO>
  getExpressionEvaluated(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true)
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ProjectIdentifier String pipelineIdentifier,
      @Parameter(description = "Plan Execution Id for which Expression have to be evaluated",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml) {
    // TODO: need to be implemented
    Map<String, ExpressionEvaluationDetail> dummyMapData = new HashMap<>();
    dummyMapData.put("expression+fqn",
        ExpressionEvaluationDetail.builder()
            .fqn("fqn")
            .originalExpression("originalExpression")
            .resolvedValue("resolvedYaml")
            .build());
    return ResponseDTO.newResponse(
        ExpressionEvaluationDetailDTO.builder().compiledYaml(yaml).mapExpression(dummyMapData).build());
  }

  @GET
  @Path("/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Detail", nickname = "getExecutionDetail")
  @Operation(operationId = "getExecutionDetail",
      description = "Returns the Pipeline Execution Details for a Given PlanExecution ID",
      summary = "Fetch Execution Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Return the Pipeline Execution details for given PlanExecution Id")
      },
      deprecated = true)
  @Deprecated
  public ResponseDTO<PipelineExecutionDetailDTO>
  getExecutionDetail(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                     @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_ID_PARAM_MESSAGE) @QueryParam(
          "stageNodeId") String stageNodeId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "stageNodeExecutionId") String stageNodeExecutionId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }

    return ResponseDTO.newResponse(
        PipelineExecutionDetailDTO.builder()
            .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
            .executionGraph(ExecutionGraphMapper.toExecutionGraph(
                pmsExecutionService.getOrchestrationGraph(stageNodeId, planExecutionId, stageNodeExecutionId),
                executionSummaryEntity))
            .build());
  }

  @GET
  @Path("/{planExecutionId}/metadata")
  @ApiOperation(value = "Get metadata of an execution", nickname = "getExecutionData")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionData", summary = "Get execution metadata of a pipeline execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns metadata of a execution")
      })
  @Hidden
  public ResponseDTO<ExecutionDataResponseDTO>
  getExecutions(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get Metadata") String planExecutionId) {
    ExecutionDataResponseDTO executionDetailsResponseDTO = pmsExecutionService.getExecutionData(planExecutionId);
    return ResponseDTO.newResponse(executionDetailsResponseDTO);
  }

  @GET
  @Path("/{planExecutionId}/metadata/details")
  @ApiOperation(value = "Get plan metadata details of an execution", nickname = "getExecutionDataDetails")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionDataDetails",
      summary = "Get execution metadata details of a pipeline execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns plan metadata details of a execution")
      })
  @Hidden
  public ResponseDTO<ExecutionMetaDataResponseDetailsDTO>
  getExecutionsDetails(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get Metadata") String planExecutionId) {
    ExecutionMetaDataResponseDetailsDTO executionDetailsResponseDTO =
        pmsExecutionService.getExecutionDataDetails(planExecutionId);
    return ResponseDTO.newResponse(executionDetailsResponseDTO);
  }

  @GET
  @Produces({"application/yaml"})
  @Path("/{planExecutionId}/inputset")
  @ApiOperation(value = "Gets  inputsetYaml", nickname = "getInputsetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(deprecated = true, operationId = "getInputsetYaml",
      summary = "Get the Input Set YAML used for given Plan Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Input Set YAML used for given Plan Execution")
      })
  @Hidden
  public String
  getInputsetYaml(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("resolveExpressions") @DefaultValue("false") boolean resolveExpressions,
      @QueryParam("resolveExpressionsType") @DefaultValue("UNKNOWN") ResolveInputYamlType resolveExpressionsType,
      @Parameter(description = "Plan Execution Id for which we want to get the Input Set YAML",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    return pmsExecutionService
        .getInputSetYamlWithTemplate(
            accountId, orgId, projectId, planExecutionId, false, resolveExpressions, resolveExpressionsType)
        .getInputSetYaml();
  }

  @GET
  @Path("/{planExecutionId}/inputsetV2")
  @ApiOperation(value = "Gets  inputsetYaml", nickname = "getInputsetYamlV2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getInputsetYamlV2", summary = "Get the Input Set YAML used for given Plan Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Input Set YAML used for given Plan Execution")
      })
  public ResponseDTO<InputSetYamlWithTemplateDTO>
  getInputsetYamlV2(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(
          description = "A boolean that indicates whether or not expressions should be resolved in input set yaml ")
      @QueryParam("resolveExpressions") @DefaultValue("false") boolean resolveExpressions,
      @Parameter(
          description =
              "Resolve Expressions Type indicates what kind of expressions should be resolved in input set yaml. "
              + "The default value is UNKNOWN in which case no expressions will be resolved"
              + "Choose a value from the enum list: [RESOLVE_ALL_EXPRESSIONS, RESOLVE_TRIGGER_EXPRESSIONS, UNKNOWN]")
      @QueryParam("resolveExpressionsType") @DefaultValue("UNKNOWN") ResolveInputYamlType resolveExpressionsType,
      @Parameter(description = "Plan Execution Id for which we want to get the Input Set YAML",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    return ResponseDTO.newResponse(pmsExecutionService.getInputSetYamlWithTemplate(
        accountId, orgId, projectId, planExecutionId, false, resolveExpressions, resolveExpressionsType));
  }

  @GET
  @Path("/list-repositories")
  @ApiOperation(value = "Gets execution repositories list", nickname = "getExecutionRepositoriesList")
  @Operation(operationId = "getExecutionRepositoriesList", description = "Returns a list of repositories branches",
      summary = "List repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns a list of all the repositories for Pipeline created in this scope")
      })
  @Hidden
  public ResponseDTO<PMSPipelineListRepoResponse>
  getListOfRepos(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier) {
    Criteria criteria = pmsExecutionService.formCriteriaForRepoAndBranchListing(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, "");
    return ResponseDTO.newResponse(pmsExecutionService.getListOfRepo(criteria));
  }

  @GET
  @Path("/list-branches")
  @ApiOperation(value = "Gets execution branches list", nickname = "getExecutionBranchesList")
  @Operation(operationId = "getExecutionBranchesList",
      description = "Returns a list of branches the pipeline was executed from", summary = "List Branches",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of branches the pipeline was executed from")
      })
  @Hidden
  public ResponseDTO<PMSPipelineListBranchesResponse>
  getListOfBranches(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName) {
    Criteria criteria = pmsExecutionService.formCriteriaForRepoAndBranchListing(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, repoName);
    return ResponseDTO.newResponse(pmsExecutionService.getListOfBranches(criteria));
  }

  @GET
  @Path("/{planExecutionId}/notes")
  @ApiOperation(value = "Get Notes of an execution from planExecutionMetadata", nickname = "getNotesForExecution")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getNotesForExecution", summary = "Get Notes for a pipelineExecution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Notes of a pipelineExecution")
      })
  public ResponseDTO<PipelineExecutionNotesDTO>
  getNotesForPlanExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get notes",
          required = true) String planExecutionId) {
    String pipelineExecutionNotes = planExecutionMetadataService.getNotesForExecution(planExecutionId);
    return ResponseDTO.newResponse(PipelineExecutionNotesDTO.builder().notes(pipelineExecutionNotes).build());
  }

  @PUT
  @Path("/{planExecutionId}/notes")
  @ApiOperation(value = "Updates Notes of a pipelineExecution", nickname = "updateNotesForExecution")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "updateNotesForExecution", summary = "Updates Notes for a pipelineExecution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Notes of a pipelineExecution")
      })
  public ResponseDTO<PipelineExecutionNotesDTO>
  updateNotesForPlanExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NOTES_OF_A_PIPELINE_EXECUTION,
          required = true) @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) String notes,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to update notes",
          required = true) String planExecutionId) {
    String pipelineExecutionNotes = planExecutionMetadataService.updateNotesForExecution(planExecutionId, notes);
    return ResponseDTO.newResponse(PipelineExecutionNotesDTO.builder().notes(pipelineExecutionNotes).build());
  }
}
