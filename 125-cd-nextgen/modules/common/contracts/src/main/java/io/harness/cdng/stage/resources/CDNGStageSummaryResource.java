/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.stage.resources;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cd.CDStageSummaryConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE;
import static io.harness.cd.CDStageSummaryConstants.STAGE_EXECUTION_IDENTIFIERS_KEY;
import static io.harness.cd.CDStageSummaryConstants.STAGE_EXECUTION_IDENTIFIERS_PARAM_MESSAGE;
import static io.harness.cd.CDStageSummaryConstants.STAGE_IDENTIFIERS_KEY;
import static io.harness.cd.CDStageSummaryConstants.STAGE_IDENTIFIERS_PARAM_MESSAGE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

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
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Tag(name = "CDStageSummary", description = "This contains APIs related to Deployment Stage Summary")
@Api("cdStageSummary")
@Path("cdStageSummary")
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
public interface CDNGStageSummaryResource {
  @GET
  //  @Hidden
  @Path("/listStageExecutionFormattedSummary")
  @ApiOperation(value = "Lists summary of execution of deployment stages filtered by stage execution identifiers",
      nickname = "listStageExecutionFormattedSummaryByStageExecutionIdentifiers")
  @Operation(operationId = "listStageExecutionFormattedSummaryByStageExecutionIdentifiers",
      summary = "Lists summary of execution of deployment stages filtered by stage execution identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns a map of stage execution identifiers and formatted stage summary if execution info is present")
      })

  ResponseDTO<Map<String, CDStageSummaryResponseDTO>>
  listStageExecutionFormattedSummary(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @NotEmpty @QueryParam(STAGE_EXECUTION_IDENTIFIERS_KEY) @Parameter(
          description = STAGE_EXECUTION_IDENTIFIERS_PARAM_MESSAGE) List<String> stageExecutionIdentifiers);

  @GET
  //  @Hidden
  @Path("/listStagePlanCreationFormattedSummary")
  @ApiOperation(value = "Lists summary of deployment stages available at plan creation filtered by stage identifiers",
      nickname = "listStagePlanCreationFormattedSummaryByStageIdentifiers")
  @Operation(operationId = "listStagePlanCreationFormattedSummaryByStageIdentifiers",
      summary = "Lists summary of deployment stages available at plan creation filtered by stage identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns a map of stage identifiers and the formatted summary if plan creation info is present")
      })

  ResponseDTO<Map<String, CDStageSummaryResponseDTO>>
  listStagePlanCreationFormattedSummary(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = PLAN_EXECUTION_ID_PARAM_MESSAGE) String planExecutionId,
      @NotNull @NotEmpty @QueryParam(STAGE_IDENTIFIERS_KEY) @Parameter(
          description = STAGE_IDENTIFIERS_PARAM_MESSAGE) List<String> stageIdentifiers);
}
