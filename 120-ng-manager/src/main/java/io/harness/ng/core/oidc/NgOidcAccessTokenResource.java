/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenResponse;
import io.harness.oidc.gcp.GcpOidcAccessTokenRequestDTO;
import io.harness.oidc.gcp.GcpOidcTokenUtility;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Path("/oidc/access-token")
@Api("/oidc/access-token")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "Oidc-Access-Token",
    description = "This contains APIs related to OIDC Access Token generation as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NgOidcAccessTokenResource {
  GcpOidcTokenUtility gcpOidcTokenUtility;
  @POST
  @Path("gcp/workload-access")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate an OIDC Access Token for GCP", nickname = "generateOidcAccessTokenForGcp")
  @Operation(operationId = "generateOidcAccessTokenForGcp", summary = "Generates an OIDC Access Token for GCP",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC Access Token as an encoded string")
      })
  public ResponseDTO<OidcWorkloadAccessTokenResponse>
  getOidcIdTokenForGcp(@RequestBody(required = true, description = "Details of GCP Workload Identity")
      @Valid GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO1 = new GcpOidcAccessTokenRequestDTO();

    // Check if the ID Token is empty.
    if (StringUtils.isEmpty(gcpOidcAccessTokenRequestDTO.getOidcIdToken())) {
      gcpOidcAccessTokenRequestDTO1.setOidcIdToken(
          gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO()));
      gcpOidcAccessTokenRequestDTO1.setGcpOidcTokenRequestDTO(gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO());
    }

    // Get the Workload Access Token
    return ResponseDTO.newResponse(gcpOidcTokenUtility.exchangeOidcWorkloadAccessToken(gcpOidcAccessTokenRequestDTO1));
  }
}
