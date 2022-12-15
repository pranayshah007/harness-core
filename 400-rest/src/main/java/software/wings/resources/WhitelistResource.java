/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_IP_WHITELIST;
import static software.wings.security.PermissionAttribute.ResourceType.WHITE_LIST;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;

import software.wings.beans.User;
import software.wings.beans.security.access.Whitelist;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.WhitelistService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Whitelist resource class.
 *
 * @author rktummala on 03/29/18
 */
@Api("whitelist")
@Path("/whitelist")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(WHITE_LIST)
@AuthRule(permissionType = MANAGE_IP_WHITELIST)
@Slf4j
public class WhitelistResource {
  private WhitelistService whitelistService;
  private HarnessUserGroupService harnessUserGroupService;

  /**
   * Instantiates a new Access resource.
   *
   * @param whitelistService    the whitelist service
   */
  @Inject
  public WhitelistResource(WhitelistService whitelistService, HarnessUserGroupService harnessUserGroupService) {
    this.whitelistService = whitelistService;
    this.harnessUserGroupService = harnessUserGroupService;
  }

  /**
   * List.
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Whitelist>> list(
      @BeanParam PageRequest<Whitelist> pageRequest, @QueryParam("accountId") @NotEmpty String accountId) {
    PageResponse<Whitelist> pageResponse = whitelistService.list(accountId, pageRequest);
    return new RestResponse<>(pageResponse);
  }

  /**
   * Gets the whitelist config.
   *
   * @param accountId   the account id
   * @param whitelistId  the whitelistId
   * @return the rest response
   */
  @GET
  @Path("{whitelistId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Whitelist> get(
      @QueryParam("accountId") String accountId, @PathParam("whitelistId") String whitelistId) {
    return new RestResponse<>(whitelistService.get(accountId, whitelistId));
  }

  /**
   * Checks if the given ip address is already whitelisted.
   *
   * @param accountId   the account id
   * @param ipAddress  the ip address
   * @return the rest response
   */
  @GET
  @Path("ip-address-whitelisted")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> isIpAddressWhitelisted(
      @QueryParam("accountId") String accountId, @QueryParam("ipAddress") String ipAddress) {
    return new RestResponse<>(whitelistService.isValidIPAddress(accountId, ipAddress));
  }

  /**
   * Gets the whitelist config.
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("isEnabled")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> isEnabled(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(true);
  }

  /**
   * Save.
   *
   * @param accountId   the account id
   * @param whitelist the whitelist
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Whitelist> save(@QueryParam("accountId") String accountId, Whitelist whitelist) {
    whitelist.setAccountId(accountId);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(whitelistService.save(whitelist));
    }
  }

  /**
   * Update whitelist.
   *
   * @param accountId   the account id
   * @param whitelistId  the whitelistId
   * @param whitelist the whitelist
   * @return the rest response
   */
  @PUT
  @Path("{whitelistId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Whitelist> update(
      @QueryParam("accountId") String accountId, @PathParam("whitelistId") String whitelistId, Whitelist whitelist) {
    whitelist.setUuid(whitelistId);
    whitelist.setAccountId(accountId);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(whitelistService.update(whitelist));
    }
  }

  /**
   * Delete.
   *
   * @param accountId   the account id
   * @param whitelistId  the whitelistId
   * @return the rest response
   */
  @DELETE
  @Path("{whitelistId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> delete(
      @QueryParam("accountId") String accountId, @PathParam("whitelistId") String whitelistId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(whitelistService.delete(accountId, whitelistId));
    }
  }

  @DELETE
  public RestResponse<Boolean> deleteAll(@QueryParam("accountId") String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(whitelistService.deleteAll(accountId));
    }
  }

  @GET
  @Path("evict-cache")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> evictIpWhitelistCache(@QueryParam("accountId") String accountId) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }
    if (!harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to reset cache").build()))
          .build();
    }

    whitelistService.evictWhitelistConfigCache(accountId);
    log.info("Reset cache successful for account id {}", accountId);
    return new RestResponse<>(Boolean.TRUE);
  }
}
