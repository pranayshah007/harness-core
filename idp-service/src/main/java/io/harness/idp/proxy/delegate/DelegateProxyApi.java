/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@OwnedBy(IDP)
@Path("/v1/idp-proxy/delegate/{url: .+}")
@Produces({"application/json", "text/yaml", "text/html", "text/plain"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface DelegateProxyApi {
  @POST
  Response forwardProxy(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers,
      @PathParam("url") String url, String body) throws JsonProcessingException;
}
