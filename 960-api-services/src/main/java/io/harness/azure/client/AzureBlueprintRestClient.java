/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.blueprint.Blueprint;
import io.harness.azure.model.blueprint.PublishedBlueprint;
import io.harness.azure.model.blueprint.PublishedBlueprintList;
import io.harness.azure.model.blueprint.artifact.Artifact;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.AssignmentList;
import io.harness.azure.model.blueprint.assignment.WhoIsBlueprintContract;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperationList;

import reactor.core.publisher.Mono;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface AzureBlueprintRestClient {
  String APP_VERSION = "2018-11-01-preview";

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @PUT("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}")
  Mono<Response<Blueprint>> createOrUpdateBlueprint(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("blueprintName") String blueprintName,
      @Body Object blueprintJSON, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}")
  Mono<Response<Blueprint>> getBlueprint(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("blueprintName") String blueprintName,
      @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @PUT("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/artifacts/{artifactName}")
  Mono<Response<Artifact>> createOrUpdateArtifact(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("blueprintName") String blueprintName,
      @Path("artifactName") String artifactName, @Body Object artifactJSON, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @PUT("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions/{versionId}")
  Mono<Response<PublishedBlueprint>> publishBlueprintDefinition(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("blueprintName") String blueprintName,
      @Path("versionId") String versionId, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions/{versionId}")
  Mono<Response<PublishedBlueprint>> getPublishedBlueprintVersion(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("blueprintName") String blueprintName,
      @Path("versionId") String versionId, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions")
  Mono<Response<PublishedBlueprintList>> listPublishedBlueprintVersions(
      @Header("Authorization") String bearerAuthHeader, @Path("resourceScope") String resourceScope,
      @Path("blueprintName") String blueprintName, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET
  Mono<Response<PublishedBlueprintList>> listPublishedBlueprintVersionsNextPage(
      @Header("Authorization") String bearerAuthHeader, @Url String nextUrl, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @PUT("{resourceScope}/providers/Microsoft.Blueprint/blueprintAssignments/{assignmentName}")
  Mono<Response<Assignment>> beginCreateOrUpdateAssignment(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("assignmentName") String assignmentName,
      @Body Object assignmentJSON, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprintAssignments/{assignmentName}")
  Mono<Response<Assignment>> getAssignment(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("assignmentName") String assignmentName,
      @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprintAssignments")
  Mono<Response<AssignmentList>> listBlueprintAssignments(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET
  Mono<Response<AssignmentList>> listBlueprintAssignmentsNextPage(
      @Header("Authorization") String bearerAuthHeader, @Url String nextUrl, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("{resourceScope}/providers/Microsoft.Blueprint/blueprintAssignments/{assignmentName}/assignmentOperations")
  Mono<Response<AssignmentOperationList>> listAssignmentOperations(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("assignmentName") String assignmentName,
      @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET
  Mono<Response<AssignmentOperationList>> listAssignmentOperationsNextPage(
      @Header("Authorization") String bearerAuthHeader, @Url String nextUrl, @Query("api-version") String appVersion);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @POST("{resourceScope}/providers/Microsoft.Blueprint/blueprintAssignments/{assignmentName}/whoIsBlueprint")
  Mono<Response<WhoIsBlueprintContract>> whoIsBlueprint(@Header("Authorization") String bearerAuthHeader,
      @Path("resourceScope") String resourceScope, @Path("assignmentName") String assignmentName,
      @Query("api-version") String appVersion);
}
