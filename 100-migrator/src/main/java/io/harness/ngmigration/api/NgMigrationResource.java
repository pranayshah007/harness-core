/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigrationInputResult;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.service.AsyncDiscoveryHandler;
import io.harness.ngmigration.service.DiscoveryService;
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
import java.util.List;
import java.util.Map;
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

  @POST
  @Path("/discover/summary/async")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> queueAccountLevelSummary(@QueryParam("accountId") String accountId) {
    String requestId = asyncDiscoveryHandler.queueAccountSummary(accountId);
    return new RestResponse<>(ImmutableMap.of("requestId", requestId));
  }

  @GET
  @Path("/discover/summary/async")
  @Timed
  @ExceptionMetered
  public RestResponse<MigrationAsyncTracker> getAccountLevelSummary(
      @QueryParam("requestId") String reqId, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(asyncDiscoveryHandler.getAccountSummary(accountId, reqId));
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
  public RestResponse<List<NGYamlFile>> getMigratedFiles(@HeaderParam("Authorization") String auth,
      @QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      MigrationInputDTO inputDTO) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, null);
    return new RestResponse<>(discoveryService.migrateEntity(auth, inputDTO, result, false));
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
    return Response.ok(discoveryService.exportYamlFilesAsZip(inputDTO, result), MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", format("attachment; filename = %s_%s_%s.zip", accountId, entityId, entityType))
        .build();
  }

  @GET
  @Path("/input")
  @Timed
  @ExceptionMetered
  public RestResponse<MigrationInputResult> getInputs(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, null);
    return new RestResponse<>(discoveryService.migrationInput(result));
  }
}
