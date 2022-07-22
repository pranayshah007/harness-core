/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.resources.agent;

import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.agent.utils.AgentMtlsApiConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.service.intfc.AgentMtlsEndpointService;

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
 * Exposes the agent mTLS endpoint management REST Api for CG.
 *
 * Note:
 *    As of now limited access for harness support only.
 */
@Api(AgentMtlsApiConstants.API_ROOT)
@Path(AgentMtlsApiConstants.API_ROOT)
@Produces("application/json")
@Consumes("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AgentMtlsEndpointResource {
  private static final String ACCOUNT_ID_PARAM = "accountId";
  private static final String ACCOUNT_ID_DESCRIPTION = "The account id.";

  private final HarnessUserGroupService harnessUserGroupService;
  private final AgentMtlsEndpointService agentMtlsEndpointService;

  @Inject
  public AgentMtlsEndpointResource(
      AgentMtlsEndpointService agentMtlsEndpointService, HarnessUserGroupService harnessUserGroupService) {
    this.agentMtlsEndpointService = agentMtlsEndpointService;
    this.harnessUserGroupService = harnessUserGroupService;
  }

  @POST
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CREATE_DESC)
  public RestResponse<AgentMtlsEndpointDetails>
  createEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                               ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = AgentMtlsApiConstants.API_PARAM_CREATE_REQUEST_DESC)
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.agentMtlsEndpointService.createEndpointForAccount(accountId, endpointRequest));
    }
  }

  @PUT
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_UPDATE_DESC)
  public RestResponse<AgentMtlsEndpointDetails>
  updateEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                               ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = AgentMtlsApiConstants.API_PARAM_UPDATE_REQUEST_DESC)
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.agentMtlsEndpointService.updateEndpointForAccount(accountId, endpointRequest));
    }
  }

  @PATCH
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_PATCH_DESC)
  public RestResponse<AgentMtlsEndpointDetails>
  patchEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                              ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true,
          value = AgentMtlsApiConstants.API_PARAM_PATCH_REQUEST_DESC) @NotNull AgentMtlsEndpointRequest patchRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.agentMtlsEndpointService.patchEndpointForAccount(accountId, patchRequest));
    }
  }

  @DELETE
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_DELETE_DESC)
  public RestResponse<Boolean>
  deleteEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
      ACCOUNT_ID_PARAM) @NotNull String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.agentMtlsEndpointService.deleteEndpointForAccount(accountId));
    }
  }

  @GET
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_GET_DESC)
  public RestResponse<AgentMtlsEndpointDetails>
  getEndpointForAccount(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
      ACCOUNT_ID_PARAM) @NotNull String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      this.ensureOperationIsExecutedByHarnessSupport();
      return new RestResponse<>(this.agentMtlsEndpointService.getEndpointForAccount(accountId));
    }
  }

  /**
   * Checks whether the provided domain prefix is available.
   *
   * @param accountId required to be compliant with new internal OpenAPI specifications.
   * @param domainPrefix The domain prefix to check.
   * @return True if and only if there is no existing mTLS endpoint that uses the provided domain prefix.
   */
  @GET
  @Path(AgentMtlsApiConstants.API_PATH_CHECK_AVAILABILITY)
  @Timed
  @ExceptionMetered
  //  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(skipAuth = true)
  @ApiOperation(nickname = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_NAME,
      value = AgentMtlsApiConstants.API_OPERATION_ENDPOINT_CHECK_AVAILABILITY_DESC)
  public RestResponse<Boolean>
  isDomainPrefixAvailable(@ApiParam(required = true, value = ACCOUNT_ID_DESCRIPTION) @QueryParam(
                              ACCOUNT_ID_PARAM) @NotNull String accountId,
      @ApiParam(required = true, value = AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_DESC) @QueryParam(
          AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix) {
    this.ensureOperationIsExecutedByHarnessSupport();
    return new RestResponse<>(this.agentMtlsEndpointService.isDomainPrefixAvailable(domainPrefix));
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
