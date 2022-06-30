/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Api("ng/ldap")
@Path("ng/ldap")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Nextgen Ldap", description = "This contains APIs related to Nextgen Ldap as defined in Harness")
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
public interface NGLdapResource {
  @POST
  @Path("test/connection")
  @Hidden
  @ApiOperation(value = "Validates Ldap Connection Setting", nickname = "validateLdapConnectionSettings")
  @Operation(operationId = "validateLdapConnectionSettings", summary = "Validates Ldap Connection Setting",
      description = "Checks if passed Ldap Connection Setting is able to connect to configured ldap server",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully validated Ldap Connection Setting")
      })
  RestResponse<LdapTestResponse>
  validateLdapConnectionSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Valid LdapSettings settings);

  @GET
  @Path("/{ldapId}/search/group")
  @ApiOperation(value = "Search Ldap groups with matching name", nickname = "searchLdapGroups")
  @Operation(operationId = "searchLdapGroups", summary = "Return Ldap groups matching name",
      description = "Returns all userGroups for the configured Ldap in the account matching a given name.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns ldap groups matching a given name")
      })
  RestResponse<Collection<LdapGroupResponse>>
  searchLdapGroups(@Parameter(description = "Ldap setting id") @PathParam("ldapId") String ldapId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @QueryParam("name") @NotBlank String name);
}
