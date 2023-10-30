/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngmigration.dto.RunStat;
import io.harness.ngmigration.service.LogMigrationService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.google.inject.Inject;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(CDC)
@Slf4j
@Path("/log-migration")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
@NextGenManagerAuth
public class LogMigrationResource {
  @Inject LogMigrationService logMigrationService;

  @POST
  @Path("/bulk")
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<String> bulkMigrate(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("bucket") String bucket, @QueryParam("file") String file) {
    return new RestResponse<>(logMigrationService.migrate(bucket, file));
  }

  @GET
  @Path("/status")
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Map<String, RunStat>> getStatus(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return new RestResponse<>(logMigrationService.getStatus());
  }
}
