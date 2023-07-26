/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.project.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface ProjectClient {
  String PROJECTS_API = "projects";

  @POST(PROJECTS_API)
  Call<ResponseDTO<ProjectResponse>> createProject(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Body ProjectRequest projectDTO);

  @GET(PROJECTS_API + "/{identifier}")
  Call<ResponseDTO<Optional<ProjectResponse>>> getProject(
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier);

  @GET(PROJECTS_API)
  Call<ResponseDTO<PageResponse<ProjectResponse>>> listProject(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = "hasModule") boolean hasModule,
      @Query(value = NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Query(value = NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(value = NGResourceFilterConstants.SIZE_KEY) int size,
      @Query(value = NGResourceFilterConstants.SORT_KEY) List<String> sort);

  @GET(PROJECTS_API)
  Call<ResponseDTO<PageResponse<ProjectResponse>>> listProjects(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers);

  @GET(PROJECTS_API)
  Call<ResponseDTO<PageResponse<ProjectResponse>>> listProjects(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(value = NGResourceFilterConstants.SIZE_KEY) int size);

  @GET(PROJECTS_API + "/list")
  Call<ResponseDTO<PageResponse<ProjectResponse>>> listWithMultiOrg(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORGS_KEY) Set<String> orgIdentifiers,
      @Query(value = "hasModule") boolean hasModule,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Query(value = NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Query(value = NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(value = NGResourceFilterConstants.SIZE_KEY) int size,
      @Query(value = NGResourceFilterConstants.SORT_KEY) List<String> sort);

  @PUT(PROJECTS_API + "/{identifier}")
  Call<ResponseDTO<Optional<ProjectResponse>>> updateProject(
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Body ProjectRequest projectDTO);

  @DELETE(PROJECTS_API + "/{identifier}")
  Call<ResponseDTO<Boolean>> deleteProject(@Header(IF_MATCH) Long ifMatch,
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Path(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Path(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier);

  @GET(PROJECTS_API + "/all-projects")
  Call<ResponseDTO<List<ProjectDTO>>> getProjectList(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm);

  @GET(PROJECTS_API + "/projects-count")
  Call<ResponseDTO<ActiveProjectsCountDTO>> getAccessibleProjectsCount(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = "startTime") long startInterval, @Query(value = "endTime") long endInterval);
}
