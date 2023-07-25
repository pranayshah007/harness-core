/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;
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

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.gitsync.common.YamlConstants;
import io.harness.gitsync.common.dtos.CreatePRDTO;
import io.harness.gitsync.common.dtos.CreatePRRequest;
import io.harness.gitsync.common.dtos.CreatePRResponse;
import io.harness.gitsync.common.dtos.GetFileResponseDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.RepoValidationResponse;
import io.harness.gitsync.common.dtos.SaasGitDTO;
import io.harness.gitsync.common.dtos.ScmBatchGetFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmBatchGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseV2DTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlResponseDTO;
import io.harness.gitsync.common.dtos.ScmListFilesRequestDTO;
import io.harness.gitsync.common.dtos.ScmListFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmRepoFilterParams;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.gitsync.common.impl.GitUtils;
import io.harness.gitsync.common.service.HarnessToGitHelperService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.security.annotations.NextGenManagerAuth;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotBlank;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@Api("/scm")
@Path("/scm")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "SCM", description = "Contains APIs related to Scm")
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
@OwnedBy(DX)
public class ScmFacilitatorResource {
  private final ScmOrchestratorService scmOrchestratorService;
  private final ScmFacilitatorService scmFacilitatorService;
  private final HarnessToGitHelperService harnessToGitHelperService;

  @Inject
  public ScmFacilitatorResource(ScmOrchestratorService scmOrchestratorService,
      ScmFacilitatorService scmFacilitatorService, HarnessToGitHelperService harnessToGitHelperService) {
    this.scmOrchestratorService = scmOrchestratorService;
    this.scmFacilitatorService = scmFacilitatorService;
    this.harnessToGitHelperService = harnessToGitHelperService;
  }

  @GET
  @Path("listRepoBranches")
  @ApiOperation(value = "Gets list of branches by Connector Identifier", nickname = "getListOfBranchesByConnector")
  @Operation(operationId = "getListOfBranchesByConnector",
      summary = "Lists Branches of given Repo by referenced Connector Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains a list of Branches specific to Referenced Connector Id")
      },
      hidden = true)
  public ResponseDTO<List<String>>
  listBranchesForRepo(@Parameter(description = "Connector Identifier Reference") @QueryParam(
                          NGCommonEntityConstants.CONNECTOR_IDENTIFIER_REF) String connectorIdentifierRef,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_URL_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_URL) String repoURL,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int pageNum,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "50") int pageSize,
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SEARCH_TERM) @DefaultValue("") String searchTerm) {
    return ResponseDTO.newResponse(scmFacilitatorService.listBranchesUsingConnector(accountIdentifier, orgIdentifier,
        projectIdentifier, connectorIdentifierRef, URLDecoderUtility.getDecodedString(repoURL),
        PageRequest.builder().pageIndex(pageNum).pageSize(pageSize).build(), searchTerm));
  }

  @GET
  @Path("listBranchesByGitConfig")
  @ApiOperation(value = "Retrieves a list of Git Branches corresponding to a Git Sync Config Id",
      nickname = "getListOfBranchesByGitConfig")
  @Operation(operationId = "getListOfBranchesByGitConfig", summary = "Lists Branches by given Git Sync Config Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains a list of Branches specific to Git Sync Config Id")
      },
      hidden = true)
  public ResponseDTO<List<String>>
  listBranchesForRepo(@Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE) @QueryParam(
                          YamlConstants.YAML_GIT_CONFIG) String yamlGitConfigIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int pageNum,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "50") int pageSize,
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SEARCH_TERM) @DefaultValue("") String searchTerm) {
    return ResponseDTO.newResponse(scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.listBranchesForRepoByGitSyncConfig(accountIdentifier, orgIdentifier,
            projectIdentifier, yamlGitConfigIdentifier,
            PageRequest.builder().pageIndex(pageNum).pageSize(pageSize).build(), searchTerm),
        projectIdentifier, orgIdentifier, accountIdentifier));
  }

  @GET
  @Path("fileContent")
  @ApiOperation(value = "Gets file content", nickname = "getFileContent")
  @Operation(operationId = "getFileContent", summary = "Gets Git File Content",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Git File Content including: object Id and content")
      },
      hidden = true)
  public ResponseDTO<GitFileContent>
  getFileContent(@Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE, required = true) @NotBlank @NotNull
                 @QueryParam(YamlConstants.YAML_GIT_CONFIG) String yamlGitConfigIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "File Path") @QueryParam(YamlConstants.FILE_PATH) @NotBlank @NotNull String filePath,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @QueryParam(YamlConstants.BRANCH)
      String branch, @Parameter(description = "Commit Id") @QueryParam(YamlConstants.COMMIT_ID) String commitId) {
    return ResponseDTO.newResponse(scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFileContent(
            yamlGitConfigIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, filePath, branch, commitId),
        projectIdentifier, orgIdentifier, accountIdentifier));
  }

  @POST
  @Path("isSaasGit")
  @ApiOperation(value = "Checks if Saas is possible", nickname = "isSaasGit")
  @Operation(operationId = "isSaasGit", summary = "Checks if Saas is possible for given Repo Url",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "True if Saas is possible for given Repo Url")
      },
      hidden = true)
  public ResponseDTO<SaasGitDTO>
  isSaasGit(@Parameter(description = GitSyncApiConstants.REPO_URL_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.REPO_URL) String repoURL) {
    return ResponseDTO.newResponse(GitUtils.isSaasGit(URLDecoderUtility.getDecodedString(repoURL)));
  }

  @POST
  @Path("createPR")
  @ApiOperation(value = "creates a pull request", nickname = "createPR")
  @Operation(operationId = "createPR", summary = "creates a Pull Request",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully created a PR") },
      hidden = true)
  public ResponseDTO<CreatePRDTO>
  createPR(@RequestBody(
      description = "Details to create a PR", required = true) @Valid @NotNull GitPRCreateRequest gitCreatePRRequest) {
    return ResponseDTO.newResponse(scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.createPullRequest(gitCreatePRRequest),
        gitCreatePRRequest.getProjectIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
        gitCreatePRRequest.getAccountIdentifier()));
  }

  @POST
  @Hidden
  @Path("create-pull-request")
  @ApiOperation(value = "creates a pull request", nickname = "createPRV2")
  @Operation(operationId = "createPR", summary = "creates a Pull Request",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully created a PR") },
      hidden = true)
  public ResponseDTO<CreatePRResponse>
  createPRV2(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          description = "Details to create a PR", required = true) @Valid @NotNull CreatePRRequest createPRRequest) {
    ScmCreatePRResponseDTO scmCreatePRResponseDTO =
        scmFacilitatorService.createPR(ScmCreatePRRequestDTO.builder()
                                           .title(createPRRequest.getTitle())
                                           .scope(Scope.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(createPRRequest.getOrgIdentifier())
                                                      .projectIdentifier(createPRRequest.getProjectIdentifier())
                                                      .build())
                                           .connectorRef(createPRRequest.getConnectorRef())
                                           .repoName(createPRRequest.getRepoName())
                                           .targetBranch(createPRRequest.getTargetBranchName())
                                           .sourceBranch(createPRRequest.getSourceBranchName())
                                           .build());
    return ResponseDTO.newResponse(CreatePRResponse.builder().prNumber(scmCreatePRResponseDTO.getPrNumber()).build());
  }

  @GET
  @Path("get-file-by-branch")
  @ApiOperation(value = "get file by branch", nickname = "getFileByBranch")
  @Hidden
  @Operation(operationId = "getFile", summary = "get file",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Get file by branch") },
      hidden = true)
  public ResponseDTO<GetFileResponseDTO>
  getFileByBranch(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @NotNull @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.BRANCH_KEY) @NotBlank String branch,
      @Parameter(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.FILE_PATH_KEY) @NotBlank @NotNull String filePath) {
    ScmGetFileResponseDTO scmGetFileResponseDTO =
        scmFacilitatorService.getFileByBranch(ScmGetFileByBranchRequestDTO.builder()
                                                  .scope(Scope.builder()
                                                             .accountIdentifier(accountIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .build())
                                                  .branchName(branch)
                                                  .filePath(filePath)
                                                  .connectorRef(connectorRef)
                                                  .repoName(repoName)
                                                  .build());
    return ResponseDTO.newResponse(GetFileResponseDTO.builder()
                                       .commitId(scmGetFileResponseDTO.getCommitId())
                                       .fileContent(scmGetFileResponseDTO.getFileContent())
                                       .blobId(scmGetFileResponseDTO.getBlobId())
                                       .build());
  }

  @GET
  @Path("list-repos-by-connector")
  @ApiOperation(
      value = "Lists Git Repos corresponding to given reference connector", nickname = "getListOfReposByRefConnector")
  @Hidden
  @Operation(operationId = "listReposByRefConnector",
      summary = "Lists Git Repos corresponding to given reference connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains list of Git Repos specific to given reference connector.")
      },
      hidden = true)
  public ResponseDTO<List<GitRepositoryResponseDTO>>
  listUserRepo(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int pageNum,
      @Parameter(description = SIZE_PARAM_MESSAGE + "(max 100)"
              + "Default Value: 50") @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("50") @Max(100)
      int pageSize,
      @Parameter(description = GitSyncApiConstants.APPLY_GITX_REPO_ALLOW_LIST_FILTER_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.APPLY_GITX_REPO_ALLOW_LIST_FILTER) @DefaultValue("false")
      boolean applyGitXRepoAllowListFilter, @BeanParam ScmRepoFilterParams scmRepoFilterParams) {
    return ResponseDTO.newResponse(scmFacilitatorService.listReposByRefConnector(accountIdentifier, orgIdentifier,
        projectIdentifier, connectorRef, PageRequest.builder().pageIndex(pageNum).pageSize(pageSize).build(),
        scmRepoFilterParams, applyGitXRepoAllowListFilter));
  }

  @GET
  @Path("list-all-repos-by-connector")
  @ApiOperation(value = "Lists All Git Repos corresponding to given reference connector",
      nickname = "getListOfAllReposByRefConnector")
  @Hidden
  @Operation(operationId = "listReposByRefConnector",
      summary = "Lists All Git Repos corresponding to given reference connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains list of All Git Repos specific to given reference connector.")
      },
      hidden = true)
  public ResponseDTO<List<UserRepoResponse>>
  getAllUserRepos(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef) {
    return ResponseDTO.newResponse(scmFacilitatorService.listAllReposForOnboardingFlow(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef));
  }

  @GET
  @Path("list-branches")
  @ApiOperation(value = "Lists Git Branches of given repo", nickname = "getListOfBranchesByRefConnectorV2")
  @Hidden
  @Operation(operationId = "getListOfBranchesByRefConnectorV2", summary = "Lists Git Branches of given repo",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains paginated list of Git Branches of given repo.")
      },
      hidden = true)
  public ResponseDTO<GitBranchesResponseDTO>
  listBranches(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = "Size of the list"
              + "(max 100)"
              + "Default Value: 50") @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("50") @Max(100)
      int listSize,
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SEARCH_TERM) @DefaultValue("") String searchTerm) {
    return ResponseDTO.newResponse(scmFacilitatorService.listBranchesV2(accountIdentifier, orgIdentifier,
        projectIdentifier, connectorRef, repoName, PageRequest.builder().pageSize(listSize).build(), searchTerm));
  }

  @GET
  @Path("repo-url")
  @ApiOperation(value = "Get repo url", nickname = "getRepoURL")
  @Hidden
  public ResponseDTO<String> getRepoURL(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                                            NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef) {
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(scmFacilitatorService.getRepoUrl(scope, connectorRef, repoName));
  }

  // TODO Added only for TESTING purpose, remove later
  @GET
  @Path("list-files")
  @ApiOperation(value = "List files", nickname = "listFiles")
  @Hidden
  public ResponseDTO<ScmListFilesResponseDTO> listFiles(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @QueryParam(GitSyncApiConstants.BRANCH_KEY) String branchName,
      @QueryParam(GitSyncApiConstants.FILE_PATH_KEY) String fileDirectory) {
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(scmFacilitatorService.listFiles(ScmListFilesRequestDTO.builder()
                                                                       .ref(branchName)
                                                                       .scope(scope)
                                                                       .fileDirectoryPath(fileDirectory)
                                                                       .repoName(repoName)
                                                                       .connectorRef(connectorRef)
                                                                       .build()));
  }

  @GET
  @Path("file-url")
  @ApiOperation(value = "Get file url", nickname = "getFileURL")
  @Hidden
  public ResponseDTO<String> getFileURL(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                                            NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "File Path") @QueryParam(YamlConstants.FILE_PATH) @NotBlank @NotNull String filePath,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.BRANCH_KEY) @NotNull @NotBlank String branch,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) @NotNull String connectorRef,
      @Parameter(description = "Commit Id") @QueryParam(YamlConstants.COMMIT_ID) @NotNull String commitId) {
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    ScmGetFileUrlResponseDTO scmGetFileUrlResponseDTO =
        scmFacilitatorService.getFileUrl(ScmGetFileUrlRequestDTO.builder()
                                             .scope(scope)
                                             .branch(branch)
                                             .connectorRef(connectorRef)
                                             .commitId(commitId)
                                             .filePath(filePath)
                                             .repoName(repoName)
                                             .build());
    return ResponseDTO.newResponse(scmGetFileUrlResponseDTO.getFileURL());
  }

  @POST
  @Path("get-batch-file")
  @ApiOperation(value = "Get file url", nickname = "getBatchFile")
  @Hidden
  public ResponseDTO<ScmBatchGetFileResponseDTO> getBatchFile(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
      String accountIdentifier, ScmBatchGetFileRequestDTO scmBatchGetFileRequestDTO) {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap =
        new HashMap<>();
    scmBatchGetFileRequestDTO.getScmGetFileRequestDTOMap().forEach((identifier, scmGetFileRequestDTO) -> {
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO =
          ScmGetFileByBranchRequestDTO.builder()
              .branchName(scmGetFileRequestDTO.getBranch())
              .repoName(scmGetFileRequestDTO.getRepoName())
              .filePath(scmGetFileRequestDTO.getFilepath())
              .useCache(scmGetFileRequestDTO.getUseCache())
              .connectorRef(scmGetFileRequestDTO.getConnectorRef())
              .scope(Scope.builder()
                         .accountIdentifier(scmGetFileRequestDTO.getAccountIdentifier())
                         .projectIdentifier(scmGetFileRequestDTO.getProjectIdentifier())
                         .orgIdentifier(scmGetFileRequestDTO.getOrgIdentifier())
                         .build())
              .build();
      scmGetFileByBranchRequestDTOMap.put(
          ScmGetBatchFileRequestIdentifier.builder().identifier(identifier).build(), scmGetFileByBranchRequestDTO);
    });
    ScmGetBatchFilesResponseDTO response = scmFacilitatorService.getBatchFilesByBranch(
        ScmGetBatchFilesByBranchRequestDTO.builder()
            .accountIdentifier(accountIdentifier)
            .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap)
            .build());
    Map<String, ScmGetFileResponseV2DTO> scmGetFileResponseV2DTOMap = new HashMap<>();
    response.getScmGetFileResponseV2DTOMap().forEach((requestIdentifier, batchResponse) -> {
      scmGetFileResponseV2DTOMap.put(requestIdentifier.getIdentifier(), batchResponse);
    });
    return ResponseDTO.newResponse(
        ScmBatchGetFileResponseDTO.builder().scmGetFileResponseV2DTOMap(scmGetFileResponseV2DTOMap).build());
  }

  @GET
  @Path("validate-repo")
  @ApiOperation(value = "Validates repos on the basis of repoAllowList in default settings", nickname = "validateRepo")
  @Hidden
  @Operation(operationId = "validateRepoByRefConnector",
      summary = "Validates repos on the basis repoAllowList in default settings using referenced connector.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description =
                "Validates if the repo is accessible or not on the basis of repoAllowList in default settings using referenced connector.")
      },
      hidden = true)
  public ResponseDTO<RepoValidationResponse>
  validateRepo(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @NotBlank @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName) {
    scmFacilitatorService.validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    return ResponseDTO.newResponse(RepoValidationResponse.builder().isValid(true).build());
  }
}
