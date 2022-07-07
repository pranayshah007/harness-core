/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMtlsEndpointDetails;
import io.harness.delegate.beans.DelegateMtlsEndpointRequest;
import io.harness.delegate.utils.DelegateMtlsApiConstants;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.service.intfc.DelegateMtlsEndpointService;

import software.wings.beans.User;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.HarnessUserGroupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Exposes the delegate mTLS endpoint management REST Api for CG.
 *
 * Note:
 *    As of now limited access for harness support only.
 */
@Api(DelegateMtlsApiConstants.API_ROOT)
@Path(DelegateMtlsApiConstants.API_ROOT)
@Produces("application/json")
@Consumes("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateMtlsEndpointResource {
  private static final String ACCOUNT_ID_PARAM = "accountId";
  private static final String ACCOUNT_ID_DESCRIPTION = "The account id.";

  private final HarnessUserGroupService harnessUserGroupService;
  private final DelegateMtlsEndpointService delegateMtlsEndpointService;

  @Inject
  public DelegateMtlsEndpointResource(
      DelegateMtlsEndpointService delegateMtlsEndpointService, HarnessUserGroupService harnessUserGroupService) {
    this.delegateMtlsEndpointService = delegateMtlsEndpointService;
    this.harnessUserGroupService = harnessUserGroupService;
  }

  @POST
  @Path(DelegateMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_DESC)
  public RestResponse<DelegateMtlsEndpointDetails>
  createEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                               ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = DelegateMtlsApiConstants.API_PARAM_CREATE_REQUEST_DESC)
      @NotNull DelegateMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.delegateMtlsEndpointService.createEndpointForAccount(accountId, endpointRequest));
    }
  }

  @PUT
  @Path(DelegateMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_DESC)
  public RestResponse<DelegateMtlsEndpointDetails>
  updateEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                               ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = DelegateMtlsApiConstants.API_PARAM_UPDATE_REQUEST_DESC)
      @NotNull DelegateMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.delegateMtlsEndpointService.updateEndpointForAccount(accountId, endpointRequest));
    }
  }

  @PATCH
  @Path(DelegateMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_DESC)
  public RestResponse<DelegateMtlsEndpointDetails>
  patchEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                              ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = DelegateMtlsApiConstants.API_PARAM_PATCH_REQUEST_DESC)
      @NotNull DelegateMtlsEndpointRequest patchRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.delegateMtlsEndpointService.patchEndpointForAccount(accountId, patchRequest));
    }
  }

  @DELETE
  @Path(DelegateMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_DESC)
  public RestResponse<Boolean>
  deleteEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
      ACCOUNT_ID_PARAM) @NotNull String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.delegateMtlsEndpointService.deleteEndpointForAccount(accountId));
    }
  }

  @GET
  @Path(DelegateMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_GET_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_GET_DESC)
  public RestResponse<DelegateMtlsEndpointDetails>
  getEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
      ACCOUNT_ID_PARAM) @NotNull String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.delegateMtlsEndpointService.getEndpointForAccount(accountId));
    }
  }

  /**
   * Checks whether the provided domain prefix is available.
   *
   * @param accountId required to be compliant with new internal OpenAPI specifications.
   * @param domainPrefix The domain prefix to check.
   * @return True if and only if there is no existing delegate mTLS endpoint that uses the provided domain prefix.
   */
  @GET
  @Path(DelegateMtlsApiConstants.API_PATH_CHECK_AVAILABILITY)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_NAME,
      value = DelegateMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_DESC)
  public RestResponse<Boolean>
  isDomainPrefixAvailable(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                              ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = DelegateMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_DESC) @QueryParam(
          DelegateMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix) {
    this.ensureOperationIsExecutedByHarnessSupport();
    return new RestResponse<>(this.delegateMtlsEndpointService.isDomainPrefixAvailable(domainPrefix));
  }

  /**
   * Throws if the user executing the command isn't a Harness Support Group member.
   * This is required initially to ensure only harness support can add / remove endpoints in prod.
   *
   * @throws InvalidRequestException If no user information are available.
   * @throws UnauthorizedException If the user isn't a member of the Harness Support Group.
   */
  private void ensureOperationIsExecutedByHarnessSupport() {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("No user information available.", USER);
    }

    if (!this.harnessUserGroupService.isHarnessSupportUser(user.getUuid())) {
      throw new AccessDeniedException(
          "Only Harness Support Group Users can access this endpoint.", ErrorCode.ACCESS_DENIED, USER);
    }
  }
}
