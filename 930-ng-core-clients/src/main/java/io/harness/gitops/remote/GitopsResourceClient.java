/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitops.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitops.models.Agent;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationQuery;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.models.Repository;
import io.harness.gitops.models.RepositoryQuery;
import io.harness.ng.beans.PageResponse;

import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.GITOPS)
public interface GitopsResourceClient {
  @GET("agents")
  Call<PageResponse<Agent>> listAgents(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size);

  @POST("applications") Call<PageResponse<Application>> listApps(@Body ApplicationQuery query);

  @POST("repositories") Call<PageResponse<Repository>> listRepositories(@Body RepositoryQuery query);

  @POST("clusters") Call<PageResponse<Cluster>> listClusters(@Body ClusterQuery query);

  @GET("agents/{agentIdentifier}/applications/{applicationName}")
  Call<ApplicationResource> refreshApplication(@Path("agentIdentifier") String agentId,
      @Path("applicationName") String applicationName,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) @NotEmpty String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) @NotEmpty String projectIdentifier,
      @Query(value = NGResourceFilterConstants.APPLICATION_REFRESH_TYPE) @NotEmpty String refreshType);

  @POST("agents/{agentIdentifier}/applications/{applicationName}/sync")
  Call<ApplicationResource> syncApplication(@Path("agentIdentifier") String agentId,
      @Path("applicationName") String applicationName,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) @NotEmpty String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) @NotEmpty String projectIdentifier,
      @Body ApplicationSyncRequest syncRequest);

  @GET("agents/{agentIdentifier}/applications/{applicationName}")
  Call<ApplicationResource> getApplication(@Path("agentIdentifier") String agentId,
      @Path("applicationName") String applicationName,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) @NotEmpty String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) @NotEmpty String projectIdentifier);
}
