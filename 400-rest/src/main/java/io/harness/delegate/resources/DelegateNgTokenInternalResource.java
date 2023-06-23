/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Api("/delegate-token/ng")
@Path("/delegate-token/ng")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Hidden
@InternalApi
public class DelegateNgTokenInternalResource {
  private final DelegateNgTokenService delegateTokenService;
  private final DelegateSetupService delegateSetupService;

  @Inject
  public DelegateNgTokenInternalResource(
      DelegateNgTokenService delegateTokenService, DelegateSetupService delegateSetupService) {
    this.delegateTokenService = delegateTokenService;
    this.delegateSetupService = delegateSetupService;
  }

  @POST
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateTokenDetails> createToken(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName,
      @Parameter(description = "Time after which delegate token will be auto revoked") @QueryParam(
          "revokeAfter") Long revokeAfter) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
    return new RestResponse<>(delegateTokenService.createToken(accountIdentifier, owner, tokenName, revokeAfter));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateTokenDetails> revokeToken(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("tokenName") @NotNull String tokenName) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
    return new RestResponse<>(delegateTokenService.revokeDelegateToken(accountIdentifier, tokenName));
  }

  @GET
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<List<DelegateTokenDetails>> getDelegateTokens(
      @Parameter(description = "Name of Delegate Token (ACTIVE or REVOKED).") @QueryParam(
          "tokenName") String delegateTokenName,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Status of Delegate Token (ACTIVE or REVOKED). "
              + "If left empty both active and revoked tokens will be retrieved") @QueryParam("status")
      DelegateTokenStatus status,
      @Parameter(description = "Whether or not to include token value.") @QueryParam(
          "includeValue") boolean includeValue) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
    if (StringUtils.isBlank(delegateTokenName)) {
      return new RestResponse<>(delegateTokenService.getDelegateTokens(accountIdentifier, owner, status, includeValue));
    } else {
      final DelegateTokenDetails tokenDetails =
          delegateTokenService.getDelegateToken(accountIdentifier, delegateTokenName, includeValue);
      return new RestResponse<>(Objects.isNull(tokenDetails) ? List.of() : List.of(tokenDetails));
    }
  }

  @GET
  @Timed
  @Path("/delegate-token-value")
  @ExceptionMetered
  @InternalApi
  public RestResponse<String> getDelegateTokenValue(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "tokenName") @QueryParam("delegateTokenName") String tokenValue) {
    return new RestResponse<>(delegateTokenService.getDelegateTokenValue(accountIdentifier, tokenValue));
  }

  @GET
  @Path("/delegate-groups")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateGroupListing> list(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("delegateTokenName") String delegateTokenName) {
    return new RestResponse<>(delegateSetupService.listDelegateGroupDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, delegateTokenName));
  }
}
