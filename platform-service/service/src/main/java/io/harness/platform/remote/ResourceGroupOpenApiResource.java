/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.remote;

import io.swagger.v3.oas.annotations.Operation;
import javax.servlet.ServletConfig;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/resource-group-openapi.{type:json}")
public class ResourceGroupOpenApiResource extends BaseModuleOpenApiResource {
  @Context ServletConfig config;
  @Context Application app;

  @GET
  @Produces({MediaType.APPLICATION_JSON})
  @Operation(hidden = true)
  public Response getOpenApi(@Context HttpHeaders headers, @Context UriInfo uriInfo) throws Exception {
    return getOpenApi(config);
  }
}
