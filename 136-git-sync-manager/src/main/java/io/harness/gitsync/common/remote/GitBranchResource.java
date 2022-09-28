/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.YamlConstants;
import io.harness.gitsync.common.beans.BranchSyncStatus;
import io.harness.gitsync.common.dtos.GitBranchListDTO;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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
import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;

@Api("/git-sync-branch")
@Path("/git-sync-branch")
@Deprecated
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
@Tag(name = "Git Branches", description = "Contains APIs related to Git Sync Branch")
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
public class GitBranchResource {
  private final GitBranchService gitBranchService;

  @GET
  @Path("listBranchesWithStatus")
  @ApiOperation(
      value = "Gets list of branches with their status by Git Sync Config Id", nickname = "getListOfBranchesWithStatus")
  @Operation(operationId = "getListOfBranchesWithStatus",
      summary = "Lists branches with their status(Synced, Unsynced) by Git Sync Config Id for the given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a list of branches along with their status within the given scope")
      })
  public ResponseDTO<GitBranchListDTO>
  listBranchesWithStatusForRepo(@Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE, required = true)
                                @NotEmpty @QueryParam(YamlConstants.YAML_GIT_CONFIG) String yamlGitConfigIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int pageNum,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @Max(100) int pageSize,
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SEARCH_TERM) @DefaultValue("") String searchTerm,
      @Parameter(description = "Used to filter out Synced and Unsynced branches") @QueryParam(
          "branchSyncStatus") BranchSyncStatus branchSyncStatus) {
    return ResponseDTO.newResponse(gitBranchService.listBranchesWithStatus(accountIdentifier, orgIdentifier,
        projectIdentifier, yamlGitConfigIdentifier, PageRequest.builder().pageIndex(pageNum).pageSize(pageSize).build(),
        searchTerm, branchSyncStatus));
  }

  @POST
  @Path("sync")
  @ApiOperation(value = "Sync the new branch into harness", nickname = "syncGitBranch")
  @Operation(operationId = "syncGitBranch",
      summary = "Sync the content of new Git Branch into harness with Git Sync Config Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns True if the new Git Branch is successfully synced into Harness")
      })
  public ResponseDTO<Boolean>
  listBranchesWithStatusForRepo(
      @Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
          GitSyncApiConstants.REPO_IDENTIFIER_KEY) String yamlGitConfigIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.BRANCH_KEY) String branchName) {
    return ResponseDTO.newResponse(gitBranchService.syncNewBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, yamlGitConfigIdentifier, branchName));
  }
}
