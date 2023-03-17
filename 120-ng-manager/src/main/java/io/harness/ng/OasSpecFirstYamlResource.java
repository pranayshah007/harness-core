/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng;

import io.swagger.v3.oas.annotations.Operation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/spec-first")
public class OasSpecFirstYamlResource {
  public static final String NG_MANAGER_PATH =
      "/Users/singhmankrit/Documents/harness-core/120-ng-manager/contracts/openapi/v1/openapi.yaml";
  public static final String CONNECTORS_PATH =
      "/Users/singhmankrit/Documents/harness-core/440-connector-nextgen/contracts/openapi/v1/openapi.yaml";
  public static final String COMMONS_PATH =
      "/Users/singhmankrit/Documents/harness-core/970-ng-commons/contracts/openapi/v1/openapi.yaml";

  @GET
  @Produces("text/yaml")
  @Path("/ng-manager/openapi.yaml")
  @Operation(hidden = true)
  public Response getOpenApiYamlNGManager() throws IOException {
    File file = new File(NG_MANAGER_PATH);
    if (!file.exists()) {
      throw new FileNotFoundException("OpenAPI spec file not found at path: " + NG_MANAGER_PATH);
    }
    String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    return Response.ok().entity(yaml).header("Content-Disposition", "inline; filename=openapi.yaml").build();
  }

  @GET
  @Produces("text/yaml")
  @Path("/connectors/openapi.yaml")
  @Operation(hidden = true)
  public Response getOpenApiYamlConnectors() throws IOException {
    File file = new File(CONNECTORS_PATH);
    if (!file.exists()) {
      throw new FileNotFoundException("OpenAPI spec file not found at path: " + CONNECTORS_PATH);
    }
    String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    return Response.ok().entity(yaml).header("Content-Disposition", "inline; filename=openapi.yaml").build();
  }

  @GET
  @Produces("text/yaml")
  @Path("/commons/openapi.yaml")
  @Operation(hidden = true)
  public Response getOpenApiYamlCommons() throws IOException {
    File file = new File(COMMONS_PATH);
    if (!file.exists()) {
      throw new FileNotFoundException("OpenAPI spec file not found at path: " + COMMONS_PATH);
    }
    String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    return Response.ok().entity(yaml).header("Content-Disposition", "inline; filename=openapi.yaml").build();
  }
}
