/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim.resource;

import io.harness.exception.WingsException;
import io.harness.scim.ScimResource;
import io.harness.security.annotations.ScimAPI;

import com.google.common.io.Resources;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("scim")
@Path("/scim/account/{accountIdentifier}/")
@Consumes({"application/scim+json", "application/json"})
@Produces({"application/scim+json", "application/json"})
@Slf4j
@ScimAPI
public class NGScimStaticResource extends ScimResource {
  @GET
  @Path("ServiceProviderConfig")
  @ApiOperation(value = "Get Service Provider Configuration supported by Application's SCIM 2.0 APIs.",
      nickname = "getServiceProviderConfig")
  public Response
  getServiceProviderConfig(@PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return Response.status(Response.Status.OK).entity(readFileAsString("outputServiceConfigProvider.json")).build();
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to fetch ServiceProviderConfig, for accountId {}, Exception is", accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @GET
  @Path("ResourceTypes")
  @ApiOperation(
      value = "Get All ResourceTypes supported by Application's SCIM 2.0 APIs.", nickname = "getResourceTypes")
  public Response
  getResourceTypes(@PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return Response.status(Response.Status.OK).entity(readFileAsString("outputResourceTypes.json")).build();
    } catch (Exception ex) {
      log.error("NG_SCIM: Failed to fetch ResourceTypes, for accountId {}, Exception is", accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @GET
  @Path("Schemas")
  @ApiOperation(value = " Get All Schemas supported by Application's SCIM 2.0 APIs. ", nickname = "getSchemas")
  public Response getSchemas(@PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return Response.status(Response.Status.OK).entity(readFileAsString("outputSchemas.json")).build();
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to fetch Schemas, for accountId {}, Exception is", accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  public static String readFileAsString(String scimStaticDataPath) {
    try {
      byte[] bytes = Resources.toByteArray(NGScimStaticResource.class.getClassLoader().getResource(scimStaticDataPath));
      return new String(bytes);
    } catch (Exception ex) {
      String errorMessage = "NG_SCIM: Failed reading the json from " + scimStaticDataPath;
      log.error(errorMessage + " with error " + ex.getMessage(), ex);
      throw new WingsException(errorMessage);
    }
  }
}
