/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.resources;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryRequestDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryResponseDTO;
import io.harness.cvng.autodiscovery.services.AutoDiscoveryService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api("auto-discovery")
@Path("auto-discovery")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
public class AutoDiscoveryResource {
  public static final String MONITORED_SERVICE = "MONITOREDSERVICE";

  public static final String EDIT_PERMISSION = "chi_monitoredservice_edit";

  @Inject AutoDiscoveryService autoDiscoveryService;

  @POST
  @Timed
  @ExceptionMetered
  @Path("create")
  @ResponseMetered
  @ApiOperation(value = "Import all service dependencies for the given agent", nickname = "createAutoDiscovery")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<AutoDiscoveryResponseDTO> create(@BeanParam ProjectParams projectParams,
      @Parameter(description = "Auto Discovery create spec") @ApiParam(
          required = true) @Valid @Body AutoDiscoveryRequestDTO autoDiscoveryRequestDTO) throws IOException {
    return new RestResponse<>(autoDiscoveryService.create(projectParams, autoDiscoveryRequestDTO));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("re-import")
  @ResponseMetered
  @ApiOperation(value = "Re-import all service dependencies for the given agent", nickname = "reImportAutoDiscovery")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<AutoDiscoveryAsyncResponseDTO> reImport(@BeanParam ProjectParams projectParams) {
    return new RestResponse<>(autoDiscoveryService.reImport(projectParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("status/{correlationId}")
  @ResponseMetered
  @ApiOperation(value = "Get status of the re-import api.", nickname = "getReImportStatus")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<AutoDiscoveryAsyncResponseDTO> status(@BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("correlationId") String correlationId) {
    return new RestResponse<>(autoDiscoveryService.status(correlationId));
  }
}
