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
import software.wings.helpers.ext.ldap.LdapResponse;

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
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;
import retrofit2.http.Multipart;

@OwnedBy(PL)
@Api("ldap")
@Path("ldap")
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
  @Path("settings/test/connection")
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
      String projectIdentifier, LdapSettings settings);

  @POST
  @Path("settings/test/user")
  @Hidden
  @ApiOperation(value = "Validates Ldap User Setting", nickname = "validateLdapUserSettings")
  @Operation(operationId = "validateLdapUserSettings", summary = "Validates Ldap User Setting",
      description = "Checks if passed Ldap Group Setting is valid for configured ldap server",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully validated Ldap User Setting")
      })
  RestResponse<LdapTestResponse>
  validateLdapUserSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, LdapSettings settings);

  @POST
  @Path("settings/test/group")
  @Hidden
  @ApiOperation(value = "Validates Ldap Group Setting", nickname = "validateLdapGroupSettings")
  @Operation(operationId = "validateLdapGroupSettings", summary = "Validates Ldap Group Setting",
      description = "Checks if passed Ldap Group Setting is valid for configured ldap server",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully validated Ldap Group Setting")
      })
  RestResponse<LdapTestResponse>
  validateLdapGroupSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, LdapSettings settings);

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

  @GET
  @Path("/sync-groups")
  @Hidden
  @ApiOperation(value = "Sync Ldap groups within an account", nickname = "syncLdapGroups")
  @Operation(operationId = "syncLdapGroups", summary = "Triggers Ldap groups sync based on linked SSO groups",
      description = "Returns Boolean status whether Ldap groups sync got triggered on not.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns status whether Ldap groups sync started")
      })
  RestResponse<Boolean>
  syncLdapGroups(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @PUT
  @Path("/sync-groups")
  @Hidden
  @ApiOperation(value = "Sync Ldap groups within an account", nickname = "syncLdapGroups")
  @Operation(operationId = "syncLdapGroups", summary = "Triggers Ldap groups sync based on linked SSO groups",
      description = "Returns Boolean status whether Ldap groups sync got triggered on not.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns status whether Ldap groups sync started")
      })
  RestResponse<Boolean>
  syncLdapGroupsV2(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @Multipart
  @POST
  @Path("/ldap-login-test")
  @Consumes("multipart/form-data")
  @ApiOperation(value = "Perform LDAP Login Test", nickname = "postLdapAuthenticationTest")
  @Operation(operationId = "postLdapAuthenticationTest", summary = "Test LDAP authentication",
      description = "Tests LDAP authentication for the given Account ID, with a valid test email and password",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns authentication status")
      })
  RestResponse<LdapResponse>
  postLdapAuthenticationTest(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                 NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "This should be a valid test email") @FormDataParam("email") String email,
      @Parameter(description = "This should be a valid password for the test email") @FormDataParam(
          "password") String password);

  @GET
  @Path("/sync-group/{userGroupId}")
  @Hidden
  @ApiOperation(value = "Trigger sync for a harness user group linked to an LDAP user group in an account",
      nickname = "syncUserGroupLinkedToLDAP")
  @Operation(operationId = "syncUserGroupLinkedToLDAP",
      summary = "Trigger sync for a harness user group linked to an LDAP user group in an account",
      description =
          "Returns Boolean status whether sync for harness user group linked to Ldap group got triggered on not",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns status whether sync for harness user group linked to Ldap group started")
      })
  RestResponse<Void>
  syncUserGroupLinkedToLDAP(@Parameter(description = "Identifier of the harness user group",
                                required = true) @PathParam("userGroupId") String userGroupId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);
}
