/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import com.google.inject.Inject;
import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.YamlConstants;
import io.harness.gitsync.common.dtos.GitEntityBranchSummaryFilterDTO;
import io.harness.gitsync.common.dtos.GitEntitySummaryFilterDTO;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesListDTO;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;

@Api("/git-sync-entities")
@Path("/git-sync-entities")
@Deprecated
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
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
@OwnedBy(DX)
public class GitEntityResource {
  private GitEntityService gitEntityService;
  @GET
  @Path("{entityType}")
  @ApiOperation(value = "Get Git Sync Entity By Type", nickname = "listGitSyncEntitiesByType")
  @Operation(operationId = "listGitSyncEntitiesByType",
      summary = "Lists Git Sync Entity filtered by their Type for the given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of Git Sync Entities filtered by Entity Type")
      })
  public ResponseDTO<PageResponse<GitSyncEntityListDTO>>
  listByType(@Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String organizationIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE) @QueryParam(
          YamlConstants.GITSYNC_CONFIG_ID) String gitSyncConfigId,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @QueryParam(
          YamlConstants.BRANCH) String branch,
      @Parameter(description = "Entity Type") @PathParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue("0")
      int page, @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) int size,
      @Parameter(description = "Module Type") @QueryParam(NGCommonEntityConstants.MODULE_TYPE) String moduleType) {
    // Added moduleType for now if in future we want to support product filter in entities as well.
    return ResponseDTO.newResponse(gitEntityService.getPageByType(
        projectIdentifier, organizationIdentifier, accountIdentifier, gitSyncConfigId, branch, entityType, page, size));
  }

  @POST
  @Path("summary")
  @ApiOperation(value = "List Git Sync Entity by product for List of Repos and Entities",
      nickname = "listGitSyncEntitiesSummaryForRepoAndTypes")
  @Operation(operationId = "listGitSyncEntitiesSummaryForRepoAndTypes",
      summary = "Lists Git Sync Entity by product for the given list of Repos and Entity Types",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description =
                        "Paginated list of Git Sync Entities by product based on given list of Repos and Entity Types")
      })
  public ResponseDTO<GitSyncRepoFilesListDTO>
  listSummary(@Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String organizationIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) int size,
      @RequestBody(required = true, description = "Filter Git Sync Entity based on multiple parameters")
      @Body GitEntitySummaryFilterDTO gitEntityFilter) {
    return ResponseDTO.newResponse(gitEntityService.listSummary(projectIdentifier, organizationIdentifier,
        accountIdentifier, gitEntityFilter.getModuleType(), gitEntityFilter.getSearchTerm(),
        gitEntityFilter.getGitSyncConfigIdentifiers(), gitEntityFilter.getEntityTypes(), size));
  }

  @POST
  @Path("branch/{branch}")
  @ApiOperation(value = "List Git Sync Entity by product for Repo and Branch and List of Entities",
      nickname = "listGitSyncEntitiesSummaryForRepoAndBranch")
  @Operation(operationId = "listGitSyncEntitiesSummaryForRepoAndBranch",
      summary = "Lists Git Sync Entity by product for the given Repo, Branch and list of Entity Types",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description =
                "This contains a list of Product-wise Git Sync Entities specific to the given Repo, Branch, and Entity Types")
      })
  public ResponseDTO<List<GitSyncEntityListDTO>>
  listSummaryByRepoAndBranch(@Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
                                 NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String organizationIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) int size,
      @Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE) @QueryParam(
          YamlConstants.GITSYNC_CONFIG_ID) String gitSyncConfigId,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @PathParam(
          YamlConstants.BRANCH) @NotEmpty String branch,
      @RequestBody(required = true, description = "This filters the Git Sync Entity based on multiple parameters")
      @Body GitEntityBranchSummaryFilterDTO gitEntityFilter) {
    return ResponseDTO.newResponse(gitEntityService.listSummaryByRepoAndBranch(projectIdentifier,
        organizationIdentifier, accountIdentifier, gitEntityFilter.getModuleType(), gitEntityFilter.getSearchTerm(),
        gitSyncConfigId, branch, gitEntityFilter.getEntityTypes(), size));
  }
}
