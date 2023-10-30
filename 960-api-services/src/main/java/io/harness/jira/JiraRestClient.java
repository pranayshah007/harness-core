/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import net.sf.json.JSONArray;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public interface JiraRestClient {
  @GET("serverInfo") Call<JiraInstanceData> getInstanceData();
  @GET("user") Call<JiraUserData> getUser(@Query("key") String userKey);

  @GET("user/search")
  Call<List<JiraUserData>> getUsers(@Query("query") String userQuery, @Query("accountId") String accountId,
      @Query("maxResults") String maxResults, @Query("startAt") String startAt);

  @GET("user/search")
  Call<List<JiraUserData>> getUsersForJiraServer(@Query("username") String userQuery,
      @Query("accountId") String accountId, @Query("maxResults") String maxResults, @Query("startAt") String startAt);

  @GET("project") Call<List<JiraProjectBasicNG>> getProjects();

  @GET("issue/{issueKey}")
  Call<JiraIssueNG> getIssue(@Path("issueKey") String issueKey, @Query("expand") String expand);

  @GET("issue/{issueKey}")
  Call<JsonNode> getIssueV2(
      @Path("issueKey") String issueKey, @Query("expand") String expand, @Query("fields") String fields);

  @GET("issue/{issueKey}/transitions")
  Call<JiraIssueTransitionsNG> getIssueTransitions(@Path("issueKey") String issueKey);

  @GET("issue/createmeta")
  Call<JiraIssueCreateMetadataNG> getIssueCreateMetadata(@Query("projectKeys") String projectKeys,
      @Query("issuetypeNames") String issueTypes, @Query("expand") String expand);

  @GET("issue/createmeta/{projectKey}/issuetypes")
  Call<JiraIssueCreateMetadataNGIssueTypes> getIssueCreateMetadataIssueTypes(@Path("projectKey") String projectKey);

  @GET("issue/createmeta/{projectKey}/issuetypes/{issueTypeId}")
  Call<JiraIssueCreateMetadataNGFields> getIssueCreateMetadataFields(@Path("projectKey") String projectKey,
      @Path("issueTypeId") String issueTypeId, @Query("maxResults") Integer maxResults);

  @GET("issue/{issueKey}/editmeta")
  Call<JiraIssueUpdateMetadataNG> getIssueUpdateMetadata(@Path("issueKey") String issueKey);

  @GET("status") Call<List<JiraStatusNG>> getStatuses();

  @GET("project/{projectKey}/statuses")
  Call<List<JiraIssueTypeNG>> getProjectStatuses(@Path("projectKey") String projectKey);

  @POST("issue") Call<JiraIssueNG> createIssue(@Body JiraCreateIssueRequestNG createIssueRequest);

  @PUT("issue/{issueKey}")
  Call<Void> updateIssue(@Path("issueKey") String issueKey, @Body JiraUpdateIssueRequestNG updateIssueRequest);

  @GET("resolution") Call<JSONArray> getResolution();

  @POST("issue/{issueKey}/comment")
  Call<Void> addIssueComment(
      @Path("issueKey") String issueKey, @Body JiraAddIssueCommentRequestNG addIssueCommentRequest);

  @POST("issue/{issueKey}/transitions")
  Call<Void> transitionIssue(@Path("issueKey") String issueKey, @Body JiraUpdateIssueRequestNG updateIssueRequest);
}
