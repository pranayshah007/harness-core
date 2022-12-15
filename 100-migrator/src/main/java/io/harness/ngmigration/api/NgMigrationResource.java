/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;
import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Calendar.getInstance;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SimilarWorkflowDetail;
import io.harness.ngmigration.service.AsyncDiscoveryHandler;
import io.harness.ngmigration.service.AsyncSimilarWorkflowHandler;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.MigrationResourceService;
import io.harness.ngmigration.service.UsergroupImportService;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.rest.RestResponse;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.security.PermissionAttribute.ResourceType;
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
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

@OwnedBy(CDC)
@Slf4j
@Path("/")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
public class NgMigrationResource {
  @Inject DiscoveryService discoveryService;
  @Inject AsyncDiscoveryHandler asyncDiscoveryHandler;
  @Inject MigrationResourceService migrationResourceService;
  @Inject UsergroupImportService usergroupImportService;
  @Inject AsyncSimilarWorkflowHandler asyncSimilarWorkflowHandler;

  @POST
  @Path("/discover-multi")
  @Timed
  @ExceptionMetered
  public RestResponse<DiscoveryResult> discoverMultipleEntities(@QueryParam("accountId") String accountId,
      @QueryParam("exportImg") boolean exportImage, DiscoveryInput discoveryInput) {
    discoveryInput.setExportImage(discoveryInput.isExportImage() || exportImage);
    return new RestResponse<>(discoveryService.discoverMulti(accountId, discoveryInput));
  }

  @GET
  @Path("/discover")
  @Timed
  @ExceptionMetered
  public RestResponse<DiscoveryResult> discoverEntities(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType, @QueryParam("exportImg") boolean exportImage) {
    return new RestResponse<>(discoveryService.discover(
        accountId, appId, entityId, entityType, exportImage ? NGMigrationConstants.DISCOVERY_IMAGE_PATH : null));
  }

  @GET
  @Path("/discover/summary")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<NGMigrationEntityType, BaseSummary>> discoverySummary(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType) {
    return new RestResponse<>(discoveryService.getSummary(accountId, appId, entityId, entityType));
  }

  // This is get because in prod we cannot run this on customers accounts if it is POST
  @GET
  @Path("/discover/summary/async")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> queueAccountLevelSummary(@QueryParam("accountId") String accountId) {
    String requestId = asyncDiscoveryHandler.queue(accountId);
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/discover/summary/async-result")
  @Timed
  @ExceptionMetered
  public RestResponse<MigrationAsyncTracker> getAccountLevelSummary(
      @QueryParam("requestId") String reqId, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(asyncDiscoveryHandler.getTaskResult(accountId, reqId));
  }

  @GET
  @Path("/discover/viz")
  @Timed
  @ExceptionMetered
  public Response discoverEntitiesImg(@QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      @QueryParam("exportImg") boolean exportImage) throws IOException {
    return Response.ok(discoveryService.discoverImg(accountId, appId, entityId, entityType))
        .header("content-disposition", format("attachment; filename = %s_%s_%s.zip", accountId, entityId, entityType))
        .header("content-type", ContentType.IMAGE_PNG)
        .build();
  }

  @GET
  @Path("/discover/validate")
  @Timed
  @ExceptionMetered
  public RestResponse<NGMigrationStatus> findDiscoveryErrors(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType) {
    DiscoveryResult discoveryResult = discoveryService.discover(accountId, appId, entityId, entityType, null);
    return new RestResponse<>(discoveryService.getMigrationStatus(discoveryResult));
  }

  @POST
  @Path("/save")
  @Timed
  @ExceptionMetered
  public RestResponse<SaveSummaryDTO> getMigratedFiles(@HeaderParam("Authorization") String auth,
      @QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      MigrationInputDTO inputDTO) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, null);
    return new RestResponse<>(discoveryService.migrateEntity(auth, inputDTO, result));
  }

  @POST
  @Path("/save/v2")
  @Timed
  @ExceptionMetered
  public RestResponse<SaveSummaryDTO> saveEntitiesV2(
      @HeaderParam("Authorization") String auth, @QueryParam("accountId") String accountId, ImportDTO importDTO) {
    importDTO.setAccountIdentifier(accountId);
    return new RestResponse<>(migrationResourceService.save(auth, importDTO));
  }

  @POST
  @Path("/user-group/save")
  @Timed
  @ExceptionMetered
  public RestResponse<SaveSummaryDTO> saveUserGroups(
      @HeaderParam("Authorization") String auth, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(usergroupImportService.importUserGroups(auth, accountId));
  }

  @POST
  @Path("/export-yaml")
  @Timed
  @ExceptionMetered
  public Response exportZippedYamlFiles(@HeaderParam("Authorization") String auth,
      @QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      MigrationInputDTO inputDTO) {
    DiscoveryResult result;
    if (EmptyPredicate.isNotEmpty(inputDTO.getEntities())) {
      result = discoveryService.discoverMulti(
          accountId, DiscoveryInput.builder().entities(inputDTO.getEntities()).exportImage(false).build());
    } else {
      result = discoveryService.discover(accountId, appId, entityId, entityType, null);
    }
    inputDTO.setMigrateReferencedEntities(true);
    return Response.ok(discoveryService.exportYamlFilesAsZip(inputDTO, result), MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", format("attachment; filename = %s_%s_%s.zip", accountId, entityId, entityType))
        .build();
  }

  @POST
  @Path("/export-yaml/v2")
  @Timed
  @ExceptionMetered
  public Response exportZippedYamlFilesV2(
      @HeaderParam("Authorization") String auth, @QueryParam("accountId") String accountId, ImportDTO importDTO) {
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
  public RestResponse<List<Set<SimilarWorkflowDetail>>> getSimilarWorkflows(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(migrationResourceService.listSimilarWorkflow(accountId));
  }

  // This is get because in prod we cannot run this on customers accounts if it is POST
  @GET
  @Path("/similar-workflows/async")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> queueSimilarWorkflows(@QueryParam("accountId") String accountId) {
    String requestId = asyncSimilarWorkflowHandler.queue(accountId);
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/similar-workflows/async-result")
  @Timed
  @ExceptionMetered
  public RestResponse<MigrationAsyncTracker> getSimilarWorkflows(
      @QueryParam("requestId") String reqId, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(asyncSimilarWorkflowHandler.getTaskResult(accountId, reqId));
  }
}
