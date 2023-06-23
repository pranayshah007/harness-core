/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.resource;

import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKey;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKeyFilterParams;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheResponse;
import io.harness.gitsync.caching.beans.GitDefaultBranchDeleteResponse;
import io.harness.gitsync.caching.dtos.DefaultBranchCacheResponse;
import io.harness.gitsync.caching.dtos.GitDefaultBranchCacheListRequest;
import io.harness.gitsync.caching.dtos.GitDefaultBranchCacheListResponse;
import io.harness.gitsync.caching.dtos.GitDefaultBranchClearCacheRequest;
import io.harness.gitsync.caching.dtos.GitDefaultBranchClearCacheResponse;
import io.harness.gitsync.caching.dtos.GitDefaultBranchGetCacheRequest;
import io.harness.gitsync.caching.dtos.GitDefaultBranchGetCacheResponse;
import io.harness.gitsync.caching.dtos.GitDefaultBranchUpsertCacheRequest;
import io.harness.gitsync.caching.dtos.GitDefaultBranchUpsertCacheResponse;
import io.harness.gitsync.caching.service.GitDefaultBranchCacheService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api("/git-service/default-branch-cache")
@Path("/git-service/default-branch-cache")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
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
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DefaultBranchCacheResource {
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";
  private final GitDefaultBranchCacheService gitDefaultBranchCacheService;
  private final UserHelperService userHelperService;

  @Inject
  public DefaultBranchCacheResource(
      GitDefaultBranchCacheService gitDefaultBranchCacheService, UserHelperService userHelperService) {
    this.gitDefaultBranchCacheService = gitDefaultBranchCacheService;
    this.userHelperService = userHelperService;
  }

  @DELETE
  @Hidden
  public ResponseDTO<GitDefaultBranchClearCacheResponse> clearCache(
      @QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitDefaultBranchClearCacheRequest request) {
    checkUserAuthorization(
        String.format("User : %s not allowed to clear git-service default branch cache for request %s",
            USER_ID_PLACEHOLDER, request));

    GitDefaultBranchDeleteResponse gitDefaultBranchDeleteResponse = gitDefaultBranchCacheService.invalidateCache(
        GitDefaultBranchCacheKeyFilterParams.builder()
            .accountIdentifier(request.getGitDefaultBranchCacheParamsRequestDTO().getAccountIdentifier())
            .repo(request.getGitDefaultBranchCacheParamsRequestDTO().getRepo())
            .repoUrl(request.getGitDefaultBranchCacheParamsRequestDTO().getRepoUrl())
            .build());
    return ResponseDTO.newResponse(
        GitDefaultBranchClearCacheResponse.builder().count(gitDefaultBranchDeleteResponse.getCount()).build());
  }

  @PUT
  @Hidden
  public ResponseDTO<GitDefaultBranchUpsertCacheResponse> upsertCache(
      @QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitDefaultBranchUpsertCacheRequest request) {
    checkUserAuthorization(String.format(
        "User : %s not allowed to upsert git-service default branch cache request %s", USER_ID_PLACEHOLDER, request));
    if (request.getGitDefaultBranchCacheKeyRequestDTO() == null || request.getDefaultBranch() == null) {
      throw new InvalidRequestException("Either of key or value in the request is null");
    }
    GitDefaultBranchCacheKey gitDefaultBranchCacheKey =
        new GitDefaultBranchCacheKey(request.getGitDefaultBranchCacheKeyRequestDTO().getAccountIdentifier(),
            request.getGitDefaultBranchCacheKeyRequestDTO().getRepoUrl(),
            request.getGitDefaultBranchCacheKeyRequestDTO().getRepo());
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        gitDefaultBranchCacheService.upsertCache(gitDefaultBranchCacheKey, request.getDefaultBranch());
    return ResponseDTO.newResponse(
        GitDefaultBranchUpsertCacheResponse.builder()
            .defaultBranchCacheResponse(DefaultBranchCacheResponse.builder()
                                            .repo(gitDefaultBranchCacheResponse.getRepo())
                                            .defaultBranch(gitDefaultBranchCacheResponse.getDefaultBranch())
                                            .build())
            .build());
  }

  @GET
  @Hidden
  public ResponseDTO<GitDefaultBranchGetCacheResponse> fetchFromCache(
      @QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitDefaultBranchGetCacheRequest request) {
    checkUserAuthorization(
        String.format("User : %s not allowed to fetch git-service default branch cache for request %s",
            USER_ID_PLACEHOLDER, request));
    if (request.getGitDefaultBranchCacheKeyRequestDTO() == null) {
      throw new InvalidRequestException("Either of key in the request is null");
    }
    GitDefaultBranchCacheKey gitDefaultBranchCacheKey =
        new GitDefaultBranchCacheKey(request.getGitDefaultBranchCacheKeyRequestDTO().getAccountIdentifier(),
            request.getGitDefaultBranchCacheKeyRequestDTO().getRepoUrl(),
            request.getGitDefaultBranchCacheKeyRequestDTO().getRepo());
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        gitDefaultBranchCacheService.fetchFromCache(gitDefaultBranchCacheKey);
    return ResponseDTO.newResponse(
        GitDefaultBranchGetCacheResponse.builder()
            .defaultBranchCacheResponse(DefaultBranchCacheResponse.builder()
                                            .repo(gitDefaultBranchCacheResponse.getRepo())
                                            .defaultBranch(gitDefaultBranchCacheResponse.getDefaultBranch())
                                            .build())
            .build());
  }

  @POST
  @Hidden
  public ResponseDTO<GitDefaultBranchCacheListResponse> list(@QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitDefaultBranchCacheListRequest request) {
    checkUserAuthorization(
        String.format("User : %s not allowed to fetch git-service default branch cache for request %s",
            USER_ID_PLACEHOLDER, request));
    if (request.getGitDefaultBranchCacheParamsRequestDTO() == null) {
      throw new InvalidRequestException("Either of key in the request is null");
    }
    List<GitDefaultBranchCacheResponse> gitDefaultBranchCacheResponseList = gitDefaultBranchCacheService.listFromCache(
        GitDefaultBranchCacheKeyFilterParams.builder()
            .accountIdentifier(request.getGitDefaultBranchCacheParamsRequestDTO().getAccountIdentifier())
            .repoUrl(request.getGitDefaultBranchCacheParamsRequestDTO().getRepoUrl())
            .repo(request.getGitDefaultBranchCacheParamsRequestDTO().getRepo())
            .build());
    List<DefaultBranchCacheResponse> defaultBranchCacheResponseList =
        emptyIfNull(gitDefaultBranchCacheResponseList)
            .stream()
            .map(gitDefaultBranchCacheResponse
                -> DefaultBranchCacheResponse.builder()
                       .defaultBranch(gitDefaultBranchCacheResponse.getDefaultBranch())
                       .repo(gitDefaultBranchCacheResponse.getRepo())
                       .build())
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(GitDefaultBranchCacheListResponse.builder()
                                       .defaultBranchCacheResponseList(defaultBranchCacheResponseList)
                                       .build());
  }

  private void checkUserAuthorization(String errorMessageIfAuthorizationFailed) {
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    if (!userHelperService.isHarnessSupportUser(userId)) {
      log.error(errorMessageIfAuthorizationFailed.replace(USER_ID_PLACEHOLDER, userId));
      throw new AccessDeniedException("Not Authorized", WingsException.USER);
    }
  }
}
