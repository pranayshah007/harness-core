/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Path("/spec-first")
public class OasSpecFirstYamlResource {
  @Context ServletContext servletContext;
  public static final String NG_MANAGER_PATH = "/120-ng-manager/contracts/openapi/v1/openapi.yaml";
  public static final String CONNECTORS_PATH = "/440-connector-nextgen/contracts/openapi/v1/openapi.yaml";
  public static final String COMMONS_PATH = "/970-ng-commons/contracts/openapi/v1/openapi.yaml";

  @GET
  @Produces("application/yaml")
  @Path("/ng-manager/openapi.yaml")
  public Response getOpenApiYamlNGManager() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(servletContext.getRealPath(NG_MANAGER_PATH)));
      return Response.ok(bytes, "application/yaml")
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Produces("application/yaml")
  @Path("/connectors/openapi.yaml")
  public Response getOpenApiYamlConnectors() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(servletContext.getRealPath(CONNECTORS_PATH)));
      return Response.ok(bytes, "application/yaml")
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Produces("application/yaml")
  @Path("/commons/openapi.yaml")
  public Response getOpenApiYamlCommons() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(servletContext.getRealPath(COMMONS_PATH)));
      return Response.ok(bytes, "application/yaml")
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
