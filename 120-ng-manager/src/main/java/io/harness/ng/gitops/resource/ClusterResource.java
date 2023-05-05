/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;
import static org.jooq.tools.StringUtils.defaultIfEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.beans.ClusterBatchRequest;
import io.harness.cdng.gitops.beans.ClusterBatchResponse;
import io.harness.cdng.gitops.beans.ClusterFromGitops;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.mappers.ClusterEntityMapper;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.validations.helper.OrgAndProjectValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.NGResourceType;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import retrofit2.Response;

@NextGenManagerAuth
@Api("/gitops/clusters")
@Path("/gitops/clusters")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Clusters", description = "This contains APIs related to Gitops Clusters")
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
public class ClusterResource {
  @Inject private ClusterService clusterService;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private EnvironmentValidationHelper environmentValidationHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private GitopsResourceClient gitopsResourceClient;

  private static final String CLUSTER_PARAM_MESSAGE = "Cluster Identifier for the entity";
  private static final int UNLIMITED_PAGE_SIZE = 10000;

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Gets a Cluster by identifier", nickname = "getCluster")
  @Operation(operationId = "getCluster", summary = "Gets a Cluster by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "The saved Cluster") })
  public ResponseDTO<ClusterResponse>
  get(@Parameter(description = CLUSTER_PARAM_MESSAGE) @PathParam("identifier") @ResourceIdentifier String clusterRef,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @NotEmpty String environmentIdentifier,
      @Parameter(description = "Specify whether cluster is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");

    Optional<Cluster> entity =
        clusterService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, clusterRef);
    if (entity.isEmpty()) {
      throw new NotFoundException(format("Cluster with clusterRef [%s] in project [%s], org [%s] not found", clusterRef,
          projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(entity.map(ClusterEntityMapper::writeDTO).orElse(null));
  }

  @POST
  @ApiOperation(value = "Link a gitops cluster to an environment", nickname = "linkCluster")
  @Operation(operationId = "linkCluster", summary = "link a Cluster",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the linked Cluster") })
  public ResponseDTO<ClusterResponse>
  link(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the createCluster to be linked") @Valid ClusterRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    // TODO: add validation that cluster scope can not be higher than env scope
    Cluster entity = ClusterEntityMapper.toEntity(accountId, request);

    Cluster created = clusterService.create(entity);
    return ResponseDTO.newResponse(ClusterEntityMapper.writeDTO(created));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Link gitops clusters to an environment", nickname = "linkClusters")
  @Operation(operationId = "linkClusters", summary = "Link Clusters",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the linked Clusters") })
  public ResponseDTO<ClusterBatchResponse>
  linkBatch(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the createCluster to be created") @Valid ClusterBatchRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    if (request.isUnlinkAllClusters()) {
      throw new InvalidRequestException(
          "This is api for linking clusters, please do not set `unlinkAllClusters` field or set it to false");
    }
    List<Cluster> entities = new ArrayList<>();
    if (!request.isLinkAllClusters()) {
      // TODO: add validation that cluster scope can not be higher than env scope
      entities = ClusterEntityMapper.toEntities(accountId, request);
    } else {
      PageResponse<ClusterFromGitops> accountLevelClusters =
          fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, "", "", request.getSearchTerm());
      entities.addAll(ClusterEntityMapper.toEntities(accountId, request.getOrgIdentifier(),
          request.getProjectIdentifier(), request.getEnvRef(), accountLevelClusters.getContent()));

      if (isNotEmpty(request.getOrgIdentifier()) && isNotEmpty(request.getProjectIdentifier())) {
        PageResponse<ClusterFromGitops> projectLevelClusters = fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE,
            accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getSearchTerm());
        entities.addAll(ClusterEntityMapper.toEntities(accountId, request.getOrgIdentifier(),
            request.getProjectIdentifier(), request.getEnvRef(), projectLevelClusters.getContent()));
      }
    }
    long linked = isNotEmpty(entities) ? clusterService.bulkCreate(entities) : 0;
    return ResponseDTO.newResponse(ClusterBatchResponse.builder().linked(linked).build());
  }

  @POST
  @Path("/batchunlink")
  @ApiOperation(value = "Unlink gitops clusters to an environment", nickname = "unlinkClustersInBatch")
  @Operation(operationId = "unlinkClustersInBatch", summary = "Unlink Clusters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if all the Clusters are deleted")
      })
  public ResponseDTO<ClusterBatchResponse>
  unlinkBatch(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the createCluster to be created") @Valid ClusterBatchRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "delete");

    if (request.isLinkAllClusters()) {
      throw new InvalidRequestException(
          "This is api for unlinking clusters, please do not set `linkAllClusters` field or set it to false");
    }

    long unlinked;
    if (request.isUnlinkAllClusters()) {
      unlinked = clusterService.deleteAllFromEnvAndReturnCount(
          accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());
    } else {
      List<Cluster> entities = ClusterEntityMapper.toEntities(accountId, request);
      unlinked = isNotEmpty(entities) ? clusterService.bulkDelete(entities, accountId, request.getOrgIdentifier(),
                     request.getProjectIdentifier(), request.getEnvRef())
                                      : 0;
    }
    return ResponseDTO.newResponse(ClusterBatchResponse.builder().unlinked(unlinked).build());
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a Cluster by identifier", nickname = "deleteCluster")
  @Operation(operationId = "deleteCluster", summary = "Delete a Cluster by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Cluster is deleted") })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = CLUSTER_PARAM_MESSAGE) @PathParam("identifier") @ResourceIdentifier String clusterRef,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @NotEmpty String environmentIdentifier,
      @Parameter(description = "Scope for the gitops cluster") @QueryParam("scope") ScopeLevel scope) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");
    return ResponseDTO.newResponse(
        clusterService.delete(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, clusterRef, scope));
  }

  @GET
  @ApiOperation(value = "Gets cluster list ", nickname = "getClusterList")
  @Operation(operationId = "getClusterList", summary = "Gets cluster list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of cluster for a Project")
      })
  public ResponseDTO<PageResponse<ClusterResponse>>
  list(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "Environment Identifier of the clusters", required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String envIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of cluster identifiers") @QueryParam("identifiers") List<String> identifiers,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "list");

    // NG Clusters
    Page<Cluster> entities = getClustersForEnvId(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, identifiers, sort);

    // Account level clusters
    PageResponse<ClusterFromGitops> accountLevelClusters =
        fetchClustersFromGitopsService(page, size, accountId, "", "", searchTerm);

    // Project level clusters
    PageResponse<ClusterFromGitops> projectLevelClusters =
        fetchClustersFromGitopsService(page, size, accountId, orgIdentifier, projectIdentifier, searchTerm);

    Map<String, ClusterFromGitops> allClusters =
        Stream.of(accountLevelClusters.getContent(), projectLevelClusters.getContent())
            .flatMap(List::stream)
            .collect(Collectors.toMap(e
                -> e.getScopeLevel().toString().toLowerCase() + "." + e.getIdentifier(),
                Function.identity(), (c1, c2) -> c1));
    return ResponseDTO.newResponse(getNGPageResponse(entities.map(e -> ClusterEntityMapper.writeDTO(e, allClusters))));
  }

  @GET
  @Path("/listFromGitops")
  @ApiOperation(value = "Gets cluster list from Gitops Service ", nickname = "getClusterListFromSource")
  @Hidden
  public ResponseDTO<PageResponse<ClusterFromGitops>> listFromGitopsService(
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(
          description =
              "If true, returns cluster list based on the context(acc/org/project) passed in request. Else will aggregate from account and project levels.")
      @QueryParam("scoped") @DefaultValue("false") boolean scoped) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);

    if (scoped) {
      // Instead of aggregating from all the levels, we will send the clusters as per the level/context passed.
      return ResponseDTO.newResponse(
          fetchClustersFromGitopsService(page, size, accountId, orgIdentifier, projectIdentifier, searchTerm));
    }
    // Account level clusters
    PageResponse<ClusterFromGitops> accountLevelClusters =
        fetchClustersFromGitopsService(page, size, accountId, "", "", searchTerm);
    // check number of project level clusters
    PageResponse<ClusterFromGitops> projectLevelClusterSample =
        fetchClustersFromGitopsService(0, 1, accountId, orgIdentifier, projectIdentifier, searchTerm);

    final long totalItems = accountLevelClusters.getTotalItems() + projectLevelClusterSample.getTotalItems();
    final long totalCombinedPages = totalItems % size == 0 ? totalItems / size : totalItems / size + 1;

    if (accountLevelClusters.getContent().size() == size) {
      return ResponseDTO.newResponse(
          accountLevelClusters.but().totalPages(totalCombinedPages).totalItems(totalItems).build());
    }

    int leftOverSpace = size - accountLevelClusters.getContent().size();
    int newPageIndex = (int) (leftOverSpace == size ? page - accountLevelClusters.getTotalPages()
                                                    : 1 + page - accountLevelClusters.getTotalPages());
    // Project level clusters
    PageResponse<ClusterFromGitops> projectLevelClusters = fetchClustersFromGitopsService(
        newPageIndex, leftOverSpace, accountId, orgIdentifier, projectIdentifier, searchTerm);

    PageResponse<ClusterFromGitops> result = PageResponse.getEmptyPageResponse(null);
    result.setEmpty(accountLevelClusters.isEmpty() && projectLevelClusters.isEmpty());
    result.setPageIndex(page);
    result.setTotalPages(totalCombinedPages);
    result.setContent(Stream.of(accountLevelClusters.getContent(), projectLevelClusters.getContent())
                          .flatMap(List::stream)
                          .collect(Collectors.toList()));
    result.setPageSize(size);
    result.setPageItemCount(result.getContent().size());
    result.setTotalItems(totalItems);

    return ResponseDTO.newResponse(result);
  }

  private PageResponse<ClusterFromGitops> fetchClustersFromGitopsService(
      int page, int size, String accountId, String orgIdentifier, String projectIdentifier, String searchTerm) {
    final PageResponse<ClusterFromGitops> clusters;
    final ClusterQuery query = ClusterQuery.builder()
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .pageSize(size)
                                   .pageIndex(page)
                                   .searchTerm(searchTerm)
                                   .build();

    final Response<PageResponse<io.harness.gitops.models.Cluster>> clusterResponse;
    try {
      clusterResponse = gitopsResourceClient.listClusters(query).execute();
      if (!clusterResponse.isSuccessful()) {
        handleFailureResponse(clusterResponse);
      }
      if (clusterResponse.body() == null) {
        handleFailureResponse(clusterResponse);
      }
      ScopeLevel scopeLevel = ScopeLevel.of(accountId, orgIdentifier, projectIdentifier);
      if (clusterResponse.body().isEmpty()) {
        clusterResponse.body().setContent(new ArrayList<>());
      }
      clusters = clusterResponse.body().map(c -> ClusterEntityMapper.writeDTO(scopeLevel, c));
    } catch (IOException io) {
      throw new InvalidRequestException("failed to fetch cluster list from gitops", io);
    }
    return clusters;
  }

  private void handleFailureResponse(Response<?> response) {
    String errorBody = null;
    try {
      errorBody = response.errorBody().string();
    } catch (Exception e) {
      log.error("Could not read error body {}", response.errorBody(), e);
    }
    if (isEmpty(errorBody) && response.body() == null) {
      errorBody = "No clusters found in gitops";
    }
    throw new InvalidRequestException(
        String.format("Failed to list clusters from gitops service. %s", defaultIfEmpty(errorBody, "")));
  }

  private void throwExceptionForNoRequestDTO(Object dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s gitops cluster(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }

  private Page<Cluster> getClustersForEnvId(int page, int size, String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String searchTerm, Collection<String> identifiers,
      List<String> sort) {
    String[] strings = envIdentifier.split("\\.");
    if (strings.length == 2) {
      projectIdentifier = null;
      envIdentifier = strings[1];
      if (strings[0].equals("account")) {
        orgIdentifier = null;
      }
    } else if (strings.length != 1) {
      throw new InvalidRequestException("Environment identifier cannot contain dots.");
    }
    return clusterService.list(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, identifiers, sort);
  }
}
