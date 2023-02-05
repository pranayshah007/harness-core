/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.resources.agent;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.agent.utils.AgentMtlsApiConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.service.intfc.AgentMtlsEndpointService;

import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Hidden;
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
 * Exposes the internal agent mTLS endpoint management REST Api on CG which is called by NG.
 */
@Path(AgentMtlsApiConstants.API_ROOT_NG_INTERNAL)
@Produces("application/json")
@Consumes("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Hidden
public class AgentMtlsEndpointInternalNgResource {
  private final AgentMtlsEndpointService agentMtlsEndpointService;

  @Inject
  public AgentMtlsEndpointInternalNgResource(AgentMtlsEndpointService agentMtlsEndpointService) {
    this.agentMtlsEndpointService = agentMtlsEndpointService;
  }

  @POST
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<AgentMtlsEndpointDetails> createEndpointForAccount(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          this.agentMtlsEndpointService.createEndpointForAccount(accountIdentifier, endpointRequest));
    }
  }

  @PUT
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<AgentMtlsEndpointDetails> updateEndpointForAccount(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @NotNull AgentMtlsEndpointRequest endpointRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          this.agentMtlsEndpointService.updateEndpointForAccount(accountIdentifier, endpointRequest));
    }
  }

  @PATCH
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<AgentMtlsEndpointDetails> patchEndpointForAccount(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @NotNull AgentMtlsEndpointRequest patchRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(this.agentMtlsEndpointService.patchEndpointForAccount(accountIdentifier, patchRequest));
    }
  }

  @DELETE
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<Boolean> deleteEndpointForAccount(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(this.agentMtlsEndpointService.deleteEndpointForAccount(accountIdentifier));
    }
  }

  @GET
  @Path(AgentMtlsApiConstants.API_PATH_ENDPOINT)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<AgentMtlsEndpointDetails> getEndpointForAccount(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(this.agentMtlsEndpointService.getEndpointForAccount(accountIdentifier));
    }
  }

  /**
   * Checks whether the provided domain prefix is available.
   *
   * @param accountIdentifier required to be compliant with new internal OpenAPI specifications.
   * @param domainPrefix The domain prefix to check.
   * @return True if and only if there is no existing mTLS endpoint that uses the provided domain prefix.
   */
  @GET
  @Path(AgentMtlsApiConstants.API_PATH_CHECK_AVAILABILITY)
  @Timed
  @ExceptionMetered
  @InternalApi
  @Hidden
  public RestResponse<Boolean> isDomainPrefixAvailable(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @QueryParam(AgentMtlsApiConstants.API_PARAM_DOMAIN_PREFIX_NAME) @NotNull String domainPrefix) {
    return new RestResponse<>(this.agentMtlsEndpointService.isDomainPrefixAvailable(domainPrefix));
  }
}
