/*
 * Copyright 2022 Harness Inc. All rights reserved.
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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.caching.beans.GitFileCacheDeleteResult;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateRequestKey;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateRequestValues;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateResult;
import io.harness.gitsync.caching.beans.GitProvider;
import io.harness.gitsync.caching.dtos.GitFileCacheClearCacheRequest;
import io.harness.gitsync.caching.dtos.GitFileCacheClearCacheResponse;
import io.harness.gitsync.caching.dtos.GitFileCacheUpdateRequest;
import io.harness.gitsync.caching.dtos.GitFileCacheUpdateResponse;
import io.harness.gitsync.caching.service.GitFileCacheService;
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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api("/git-service/git-file-cache")
@Path("/git-service/git-file-cache")
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
public class GitFileCacheResource {
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";
  private final GitFileCacheService gitFileCacheService;
  private final UserHelperService userHelperService;

  @Inject
  public GitFileCacheResource(GitFileCacheService gitFileCacheService, UserHelperService userHelperService) {
    this.gitFileCacheService = gitFileCacheService;
    this.userHelperService = userHelperService;
  }

  @DELETE
  @Hidden
  public ResponseDTO<GitFileCacheClearCacheResponse> clearCache(
      @QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitFileCacheClearCacheRequest request) {
    checkUserAuthorization(
        String.format("User : %s not allowed to clear git-service cache for request %s", USER_ID_PLACEHOLDER, request));

    GitProvider gitProvider = null;
    if (request.getGitProvider() != null) {
      gitProvider = GitProvider.getByName(request.getGitProvider());
    }
    GitFileCacheDeleteResult gitFileCacheDeleteResult =
        gitFileCacheService.invalidateCache(GitFileCacheKey.builder()
                                                .accountIdentifier(request.getAccountIdentifier())
                                                .ref(request.getRef())
                                                .gitProvider(gitProvider)
                                                .completeFilePath(request.getFilepath())
                                                .repoName(request.getRepoName())
                                                .build());
    return ResponseDTO.newResponse(
        GitFileCacheClearCacheResponse.builder().count(gitFileCacheDeleteResult.getCount()).build());
  }

  @PUT
  @Hidden
  public ResponseDTO<GitFileCacheUpdateResponse> updateCache(@QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid GitFileCacheUpdateRequest request) {
    checkUserAuthorization(String.format(
        "User : %s not allowed to update git-service cache for request %s", USER_ID_PLACEHOLDER, request));
    if (request.getKey() == null || request.getValues() == null) {
      throw new InvalidRequestException("Either of key or value in the request is null");
    }
    GitProvider gitProvider = null;
    if (request.getKey().getGitProvider() != null) {
      gitProvider = GitProvider.getByName(request.getKey().getGitProvider());
    }
    GitFileCacheUpdateResult gitFileCacheUpdateResult =
        gitFileCacheService.updateCache(GitFileCacheUpdateRequestKey.builder()
                                            .accountIdentifier(request.getKey().getAccountIdentifier())
                                            .ref(request.getKey().getRef())
                                            .gitProvider(gitProvider)
                                            .filepath(request.getKey().getFilepath())
                                            .repoName(request.getKey().getRepoName())
                                            .build(),
            GitFileCacheUpdateRequestValues.builder().build());
    return ResponseDTO.newResponse(
        GitFileCacheUpdateResponse.builder().count(gitFileCacheUpdateResult.getCount()).build());
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
