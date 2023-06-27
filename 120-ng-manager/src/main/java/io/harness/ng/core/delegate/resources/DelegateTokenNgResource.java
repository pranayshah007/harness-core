/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_DELETE_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_VIEW_PERMISSION;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api("delegate-token-ng")
@Path("/delegate-token-ng")
@Produces("application/json")
@Consumes({"application/json"})
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Token Resource", description = "Contains APIs related to Delegate Token management")
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

public class DelegateTokenNgResource {
  private final DelegateNgManagerCgManagerClient delegateTokenClient;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateTokenNgResource(
      DelegateNgManagerCgManagerClient delegateTokenClient, AccessControlClient accessControlClient) {
    this.delegateTokenClient = delegateTokenClient;
    this.accessControlClient = accessControlClient;
  }

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Creates Delegate Token", nickname = "createDelegateToken")
  @Operation(operationId = "createDelegateToken", summary = "Creates Delegate Token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "A created Token.")
      })
  public RestResponse<DelegateTokenDetails>
  createDelegateToken(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName,
      @Parameter(
          description =
              "Epoch time in milliseconds after which the token will be marked as revoked. There can be a delay of upto one hour from the epoch value provided and actual revoking of the token.")
      @QueryParam("revokeAfter") Long revokeAfter) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    return new RestResponse<>(CGRestUtils.getResponse(
        delegateTokenClient.createToken(accountIdentifier, orgIdentifier, projectIdentifier, tokenName, revokeAfter)));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Revokes Delegate Token", nickname = "revokeDelegateToken")
  @Operation(operationId = "revokeDelegateToken", summary = "Revokes Delegate Token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "200 Ok response if everything successfully revoked token")
      })
  public RestResponse<DelegateTokenDetails>
  revokeDelegateToken(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_DELETE_PERMISSION);
    return new RestResponse<>(CGRestUtils.getResponse(
        delegateTokenClient.revokeToken(accountIdentifier, orgIdentifier, projectIdentifier, tokenName)));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get Delegate Tokens", nickname = "getDelegateTokens")
  @Operation(operationId = "getDelegateTokens",
      summary = "Retrieves Delegate Tokens by Account, Organization, Project and status.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of Delegate Tokens")
      })
  public RestResponse<List<DelegateTokenDetails>>
  getDelegateTokens(@Parameter(description = "Name of Delegate Token (ACTIVE or REVOKED).") @QueryParam(
                        "name") String delegateTokenName,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Status of Delegate Token (ACTIVE or REVOKED). "
              + "If left empty both active and revoked tokens will be retrieved") @QueryParam("status")
      DelegateTokenStatus status) {
    boolean hasEditAccess;
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);
    // Return token value only if the user has delegate edit permission
    hasEditAccess = accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    return new RestResponse<>(CGRestUtils.getResponse(delegateTokenClient.getTokens(
        delegateTokenName, accountIdentifier, orgIdentifier, projectIdentifier, status, hasEditAccess)));
  }

  @GET
  @Path("/delegate-groups")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get Delegate Groups", nickname = "getDelegateGroupsUsingToken")
  @Operation(operationId = "getDelegateGroupsUsingToken",
      summary = "Lists delegate groups that are using the specified delegate token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "A list of delegate groups that are usign the specified token.")
      })
  public RestResponse<DelegateGroupListing>
  list(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("delegateTokenName") String delegateTokenName) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);
    return new RestResponse<>(CGRestUtils.getResponse(delegateTokenClient.getDelegateGroupsUsingToken(
        accountIdentifier, orgIdentifier, projectIdentifier, delegateTokenName)));
  }
}
