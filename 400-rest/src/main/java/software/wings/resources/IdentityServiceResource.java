/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.eraro.ErrorCode.USER_DOES_NOT_EXIST;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.LogoutResponse;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.WingsException;
import io.harness.ng.core.switchaccount.RestrictedSwitchAccountInfo;
import io.harness.rest.RestResponse;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.IdentityServiceAuth;
import software.wings.security.authentication.AccountSettingsResponse;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.oauth.OauthUserInfo;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

/**
 * @author mark.lu on 2019-04-29
 */
@Api("identity")
@Path("/identity")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@IdentityServiceAuth
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class IdentityServiceResource {
  private AuthenticationManager authenticationManager;
  private UserService userService;
  private AccountService accountService;

  @Inject
  public IdentityServiceResource(
      AuthenticationManager authenticationManager, UserService userService, AccountService accountService) {
    this.authenticationManager = authenticationManager;
    this.userService = userService;
    this.accountService = accountService;
  }

  @GET
  @Path("/user/login")
  @Timed
  @ExceptionMetered
  public RestResponse<User> loginUser(@QueryParam("email") String email) {
    return new RestResponse<>(authenticationManager.loginUserForIdentityService(urlDecode(email)));
  }

  private String urlDecode(String encoded) {
    String decoded = encoded;
    try {
      decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // Should not happen and ignore.
    }
    return decoded;
  }

  @GET
  @Path("/users")
  @Timed
  @ExceptionMetered
  public RestResponse<List<User>> listUsers(@BeanParam PageRequest<User> pageRequest) {
    // Filter out 'disabled' users
    pageRequest.getFilters().add(SearchFilter.builder()
                                     .fieldName(UserKeys.disabled)
                                     .op(Operator.EQ)
                                     .fieldValues(new Object[] {Boolean.FALSE})
                                     .build());
    PageResponse<User> pageResponse = userService.list(pageRequest, false);
    return new RestResponse<>(pageResponse.getResponse());
  }

  @GET
  @Path("/accounts")
  @Timed
  @ExceptionMetered
  public RestResponse<List<Account>> listAccounts(@BeanParam PageRequest<Account> pageRequest) {
    return new RestResponse<>(accountService.list(pageRequest));
  }

  @GET
  @Path("/accounts/{accountId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Account> getAccount(@PathParam("accountId") String accountId) {
    Account account = accountService.get(accountId);
    return new RestResponse<>(account);
  }

  @GET
  @Path("/account-settings")
  @Timed
  @ExceptionMetered
  public RestResponse<AccountSettingsResponse> getAccountSettings(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getAuthSettingsByAccountId(accountId));
  }

  @GET
  @Path("/user")
  @Timed
  @ExceptionMetered
  public RestResponse<User> getUser(
      @QueryParam("includeSupportAccounts") @DefaultValue("true") boolean includeSupportAccounts) {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new WingsException(USER_DOES_NOT_EXIST, USER);
    } else {
      if (includeSupportAccounts) {
        userService.loadSupportAccounts(user);
      }
      return new RestResponse<>(user.getPublicUser(includeSupportAccounts));
    }
  }

  @POST
  @Path("/oauth/signup-user")
  @Timed
  @ExceptionMetered
  public RestResponse<User> signupOAuthUser(
      @NotNull OauthUserInfo oauthUserInfo, @QueryParam("provider") String oauthProviderName) {
    User user = userService.signUpUserUsingOauth(oauthUserInfo, oauthProviderName);
    return new RestResponse<>(user.getPublicUser(false));
  }

  @GET
  @Path("/restricted-switch-account-info")
  @Timed
  @ExceptionMetered
  public RestResponse<RestrictedSwitchAccountInfo> getSwitchAccountInfo(
      @QueryParam("accountId") @NotBlank String accountId, @QueryParam("userId") String userId) {
    return new RestResponse(userService.getSwitchAccountInfo(accountId, userId));
  }

  @GET
  @Path("/user/logout")
  @Timed
  @ExceptionMetered
  public RestResponse<LogoutResponse> logout(
      @QueryParam("accountId") @NotBlank String accountId, @QueryParam("userId") @NotBlank String userId) {
    return new RestResponse(userService.logout(accountId, userId));
  }
}
