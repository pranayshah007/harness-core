/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.freeze.resource;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.freeze.beans.FreezeReference;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.ShouldDisableDeploymentFreezeResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
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
import java.util.LinkedList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@Api("/freeze")
@Path("/freeze/evaluate")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Freeze Evaluation", description = "This contains APIs related to evaluation Freeze status")
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
@Slf4j
public class FreezeEvalutationResource {
  private final FreezeEvaluateService freezeEvaluateService;
  private final @Named("PRIVILEGED") AccessControlClient accessControlClient;
  private final NGFeatureFlagHelperService featureFlagHelperService;

  @GET
  @Path("/isGlobalFreezeActive")
  @ApiOperation(value = "Get if global freeze is Active", nickname = "isGlobalFreezeActive")
  @Operation(operationId = "isGlobalFreezeActive", summary = "Get if global freeze is Active",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if global freeze is active at any scope")
      })
  @Hidden
  public ResponseDTO<Boolean>
  isGlobalFreezeActive(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId) {
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTO =
        freezeEvaluateService.anyGlobalFreezeActive(accountId, orgId, projectId);
    return ResponseDTO.newResponse(!EmptyPredicate.isEmpty(freezeSummaryResponseDTO));
  }

  @GET
  @Path("/shouldDisableDeployment")
  @ApiOperation(value = "If to disable run button for deployment", nickname = "shouldDisableDeployment")
  @Operation(operationId = "isGlobalFreezeActive", summary = "If to disable run button for deployment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns true along with metadata if run button is to be disabled")
      })
  @Hidden
  public ResponseDTO<ShouldDisableDeploymentFreezeResponseDTO>
  shouldDisableDeployment(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "pipeline identifier for the entity") @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineId) {
    if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(
            featureFlagHelperService, accountId, orgId, projectId, accessControlClient)) {
      return ResponseDTO.newResponse(ShouldDisableDeploymentFreezeResponseDTO.builder()
                                         .shouldDisable(false)
                                         .freezeReferences(new LinkedList<>())
                                         .build());
    }

    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTO =
        freezeEvaluateService.getActiveFreezeEntities(accountId, orgId, projectId, pipelineId);
    List<FreezeReference> freezeReferences = new LinkedList<>();
    freezeSummaryResponseDTO.stream().forEach(freeze
        -> freezeReferences.add(FreezeReference.builder()
                                    .freezeScope(freeze.getFreezeScope())
                                    .identifier(freeze.getIdentifier())
                                    .type(freeze.getType())
                                    .build()));
    boolean shouldDisableDeployment = !EmptyPredicate.isEmpty(freezeSummaryResponseDTO);
    return ResponseDTO.newResponse(ShouldDisableDeploymentFreezeResponseDTO.builder()
                                       .shouldDisable(shouldDisableDeployment)
                                       .freezeReferences(freezeReferences)
                                       .build());
  }
}
