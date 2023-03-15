/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.security.NextGenAuthenticationFilter.X_API_KEY;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static java.lang.String.format;
import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Calendar.getInstance;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.DiscoverySummaryReqDTO;
import io.harness.ngmigration.dto.BulkCreateProjectsDTO;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ProjectCreateResultDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SimilarWorkflowDetail;
import io.harness.ngmigration.service.CreateProjectService;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.MigrationResourceService;
import io.harness.ngmigration.service.UsergroupImportService;
import io.harness.ngmigration.service.async.AsyncDiscoveryHandler;
import io.harness.ngmigration.service.async.AsyncSimilarWorkflowHandler;
import io.harness.ngmigration.service.async.AsyncUpgradeHandler;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

@OwnedBy(CDC)
@Slf4j
@Path("/")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
@NextGenManagerAuth
public class NgMigrationResource {
  @Inject DiscoveryService discoveryService;
  @Inject AsyncDiscoveryHandler asyncDiscoveryHandler;
  @Inject MigrationResourceService migrationResourceService;
  @Inject UsergroupImportService usergroupImportService;
  @Inject AsyncSimilarWorkflowHandler asyncSimilarWorkflowHandler;
  @Inject AsyncUpgradeHandler asyncUpgradeHandler;
  @Inject CreateProjectService projectService;

  @POST
  @Path("/discover-multi")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<DiscoveryResult> discoverMultipleEntities(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("exportImg") boolean exportImage,
      DiscoveryInput discoveryInput) {
    discoveryInput.setExportImage(discoveryInput.isExportImage() || exportImage);
    return new RestResponse<>(discoveryService.discoverMulti(accountId, discoveryInput));
  }

  @GET
  @Path("/discover")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<DiscoveryResult> discoverEntities(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType, @QueryParam("exportImg") boolean exportImage) {
    return new RestResponse<>(discoveryService.discover(
        accountId, appId, entityId, entityType, exportImage ? NGMigrationConstants.DISCOVERY_IMAGE_PATH : null));
  }

  @GET
  @Path("/discover/summary")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Map<NGMigrationEntityType, BaseSummary>> discoverySummary(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType) {
    return new RestResponse<>(discoveryService.getSummary(accountId, appId, entityId, entityType));
  }

  // This is get because in prod we cannot run this on customers accounts if it is POST
  @GET
  @Path("/discover/summary/async")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Map<String, String>> queueSummary(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    String requestId = asyncDiscoveryHandler.queue(
        null, accountId, DiscoverySummaryReqDTO.builder().appId(appId).accountId(accountId).build());
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/discover/summary/async-result")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<MigrationAsyncTracker> getAccountLevelSummary(
      @QueryParam("requestId") String reqId, @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return new RestResponse<>(asyncDiscoveryHandler.getTaskResult(accountId, reqId));
  }

  @GET
  @Path("/discover/viz")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public Response discoverEntitiesImg(@QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType, @QueryParam("exportImg") boolean exportImage)
      throws IOException {
    return Response.ok(discoveryService.discoverImg(accountId, appId, entityId, entityType))
        .header("content-disposition", format("attachment; filename = %s_%s_%s.zip", accountId, entityId, entityType))
        .header("content-type", ContentType.IMAGE_PNG)
        .build();
  }

  @POST
  @Path("/save/v2")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<SaveSummaryDTO> saveEntitiesV2(@HeaderParam(X_API_KEY) String auth,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ImportDTO importDTO) {
    importDTO.setAccountIdentifier(accountId);
    return new RestResponse<>(migrationResourceService.save(auth, importDTO));
  }

  @POST
  @Path("/save/async")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Map<String, String>> queueUpgrade(@HeaderParam(X_API_KEY) String auth,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ImportDTO importDTO) {
    importDTO.setAccountIdentifier(accountId);
    String requestId = asyncUpgradeHandler.queue(auth, accountId, importDTO);
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/save/async-result")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<MigrationAsyncTracker> getQueuedUpgradeResult(
      @QueryParam("requestId") String reqId, @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return new RestResponse<>(asyncUpgradeHandler.getTaskResult(accountId, reqId));
  }

  @POST
  @Path("/user-group/save")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<SaveSummaryDTO> saveUserGroups(@HeaderParam(X_API_KEY) String auth,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("identifierCaseFormat") @DefaultValue("CAMEL_CASE") CaseFormat identifierCaseFormat) {
    return new RestResponse<>(usergroupImportService.importUserGroups(auth, accountId, identifierCaseFormat));
  }

  @POST
  @Path("/export-yaml")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public Response exportZippedYamlFilesV2(@HeaderParam(X_API_KEY) String auth,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ImportDTO importDTO) {
    importDTO.setAccountIdentifier(accountId);
    Calendar calendar = getInstance();
    String filename = String.format(
        "%s_%s_%s_%s", calendar.get(YEAR), calendar.get(MONTH), calendar.get(DATE), Date.from(Instant.EPOCH).getTime());
    return Response.ok(migrationResourceService.exportYaml(auth, importDTO))
        .header("content-disposition", format("attachment; filename = %s_%s.zip", accountId, filename))
        .build();
  }

  @GET
  @Path("/similar-workflows")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<Set<SimilarWorkflowDetail>>> getSimilarWorkflows(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return new RestResponse<>(migrationResourceService.listSimilarWorkflow(accountId));
  }

  // This is get because in prod we cannot run this on customers accounts if it is POST
  @GET
  @Path("/similar-workflows/async")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Map<String, String>> queueSimilarWorkflows(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    String requestId = asyncSimilarWorkflowHandler.queue(null, accountId, null);
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/similar-workflows/async-result")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<MigrationAsyncTracker> getSimilarWorkflows(
      @QueryParam("requestId") String reqId, @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return new RestResponse<>(asyncSimilarWorkflowHandler.getTaskResult(accountId, reqId));
  }

  @POST
  @Path("/projects/bulk")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<ProjectCreateResultDTO>> bulkCreateProjects(@HeaderParam(X_API_KEY) String auth,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BulkCreateProjectsDTO createProjectsDTO)
      throws IOException {
    return new RestResponse<>(projectService.bulkCreateProjects(accountId, auth, createProjectsDTO));
  }
}
