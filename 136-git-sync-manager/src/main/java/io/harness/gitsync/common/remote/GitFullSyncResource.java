/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.dtos.TriggerFullSyncResponseDTO;
import io.harness.gitsync.common.service.FullSyncTriggerService;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo.GitFullSyncEntityInfoKeys;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityService;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigRequestDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoFilterDTO;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PaginationUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import retrofit2.http.Body;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.ng.core.rbac.ProjectPermissions.VIEW_PROJECT_PERMISSION;

@Api("/git-full-sync")
@Path("/git-full-sync")
@Deprecated
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Git Full Sync", description = "Contains APIs related to Git Full Sync")
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = BAD_REQUEST_CODE, description = BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = INTERNAL_SERVER_ERROR_CODE, description = INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
    })
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class GitFullSyncResource {
  private final FullSyncTriggerService fullSyncTriggerService;
  private final GitFullSyncConfigService gitFullSyncConfigService;
  private final GitFullSyncEntityService gitFullSyncEntityService;

  @POST
  @ApiOperation(value = "Triggers full sync", nickname = "triggerFullSync")
  @Operation(operationId = "triggerFullSync", summary = "Trigger Full Sync",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully Triggered Full Sync.") })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<TriggerFullSyncResponseDTO>
  triggerFullSync(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
                  @NotNull @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @io.harness.accesscontrol.ProjectIdentifier @ProjectIdentifier
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(
        fullSyncTriggerService.triggerFullSync(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @POST
  @Path("/config")
  @ApiOperation(value = "Create a full sync configuration", nickname = "createGitFullSyncConfig")
  @Operation(operationId = "createGitFullSyncConfig",
      summary = "Create Configuration for Git Full Sync for the provided scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the saved Configuration details for Git Full Sync.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  createFullSyncConfig(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      @RequestBody(description = "Details of the Git Full sync Configuration") @NotNull
      @Valid GitFullSyncConfigRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        gitFullSyncConfigService.createConfig(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }

  @PUT
  @Path("/config")
  @ApiOperation(value = "Update a full sync configuration", nickname = "updateGitFullSyncConfig")
  @Operation(operationId = "updateGitFullSyncConfig",
      summary = "Update Configuration for Git Full Sync for the provided scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated Git Full Sync Configuration for the provided scope.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  updateFullSyncConfig(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      @RequestBody(description = "Details of the Git Full sync Configuration") @NotNull
      @Valid GitFullSyncConfigRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        gitFullSyncConfigService.updateConfig(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }

  @GET
  @Path("/config")
  @ApiOperation(value = "Get full sync configuration", nickname = "getGitFullSyncConfig")
  @Operation(operationId = "getGitFullSyncConfig",
      summary = "Fetch Configuration for Git Full Sync for the provided scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the saved Git Full Sync Configuration for the provided scope.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  getFullSyncConfig(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier) {
    GitFullSyncConfigDTO gitFullSyncConfigDTO =
        gitFullSyncConfigService.get(accountIdentifier, orgIdentifier, projectIdentifier)
            .orElseThrow(()
                             -> new InvalidRequestException("No configuration found with given parameters",
                                 ErrorCode.RESOURCE_NOT_FOUND, WingsException.USER));
    return ResponseDTO.newResponse(gitFullSyncConfigDTO);
  }

  @POST
  @Path("/files")
  @ApiOperation(
      value = "Fetches the list of all Git Full Sync Entities and their status", nickname = "listFullSyncFiles")
  @Operation(operationId = "listFullSyncFiles", summary = "List files in full sync along with their status",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the List of Files for Git Full sync.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<PageResponse<GitFullSyncEntityInfoDTO>>
  listFiles(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
            @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      @RequestBody(description = "Details of the page to fetch paginated result") @BeanParam PageRequest pageRequest,
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @RequestBody(description = "Entity Type and Sync Status")
      @Body GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    PaginationUtils.setSortOrder(pageRequest, GitFullSyncEntityInfoKeys.createdAt, SortOrder.OrderType.DESC);
    return ResponseDTO.newResponse(gitFullSyncEntityService.list(
        accountIdentifier, orgIdentifier, projectIdentifier, pageRequest, searchTerm, gitFullSyncEntityInfoFilterDTO));
  }
}
