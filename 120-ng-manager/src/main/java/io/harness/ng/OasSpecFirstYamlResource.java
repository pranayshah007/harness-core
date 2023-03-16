package io.harness.ng;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/spec-first")
public class OasSpecFirstYamlResource {
  public static final String NG_MANAGER_PATH = "./120-ng-manager/contracts/openapi/v1/openapi.yaml";
  public static final String CONNECTORS_PATH = "./440-connector-nextgen/contracts/openapi/v1/openapi.yaml";
  public static final String COMMONS_PATH = "./970-ng-commons/contracts/openapi/v1/openapi.yaml";

  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Path("/ng-manager/openapi.yaml")
  public Response getOpenApiYamlNGManager() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(NG_MANAGER_PATH));
      return Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Path("/connectors/openapi.yaml")
  public Response getOpenApiYamlConnectors() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(CONNECTORS_PATH));
      return Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Path("/commons/openapi.yaml")
  public Response getOpenApiYamlCommons() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(COMMONS_PATH));
      return Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Disposition", "attachment; filename=\"openapi.yaml\"")
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
