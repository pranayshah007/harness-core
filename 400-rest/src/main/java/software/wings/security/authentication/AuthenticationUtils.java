/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USER_DISABLED;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ReportTarget;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.exception.AccountNotFoundException;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class AuthenticationUtils {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserService userService;
  @Inject private MainConfiguration configuration;
  @Inject private AccountService accountService;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;

  public User getUserOrReturnNullIfUserDoesNotExists(String userName) {
    if (Strings.isNullOrEmpty(userName)) {
      return null;
    }
    return getUserByEmail(userName);
  }

  public User getUser(String userName) {
    return getUser(userName, null);
  }

  public User getUser(String userName, EnumSet<ReportTarget> reportTargets) {
    User user = isNotEmpty(userName) ? getUserByEmail(userName) : null;
    if (user == null) {
      log.info("User {} does not exists.", userName);
      if (reportTargets == null) {
        throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST);
      } else {
        throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST, reportTargets);
      }
    } else if (user.isDisabled()) {
      log.info("User {} is disabled.", userName);
      throw new WingsException(USER_DISABLED, USER);
    }
    return user;
  }

  protected User getUserByEmail(String userName) {
    return wingsPersistence.createQuery(User.class).field("email").equalIgnoreCase(userName.trim().toLowerCase()).get();
  }

  public Account getAccount(String accountId) {
    Account account = null;
    try {
      account = accountService.get(accountId);
    } catch (AccountNotFoundException accountNotFoundException) {
      log.warn("{} Account Id configured does not exist ", accountId);
    }
    return account;
  }
  public Account getDefaultAccount(User user) {
    String defaultAccountId = user.getDefaultAccountId();
    if (isEmpty(defaultAccountId)) {
      Preconditions.checkNotNull(user.getAccounts(), "Account field in user is null.");
      return user.getAccounts().get(0);
    } else {
      return accountService.get(defaultAccountId);
    }
  }

  public URI buildAbsoluteUrl(String baseUrl, String path, Map<String, String> params) {
    return buildAbsoluteUrlInternal(baseUrl, path, params);
  }

  public URI buildAbsoluteUrl(String path, Map<String, String> params, String accountId) {
    String baseUrl = getBaseUrl(accountId);
    return buildAbsoluteUrlInternal(baseUrl, path, params);
  }

  private URI buildAbsoluteUrlInternal(String baseUrl, String path, Map<String, String> params) {
    try {
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setPath(path);
      if (params != null) {
        params.forEach(uriBuilder::addParameter);
      }
      return uriBuilder.build();
    } catch (URISyntaxException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
  }

  public String getBaseUrl() {
    return subdomainUrlHelper.getApiBaseUrl(null);
  }

  public String getBaseUrl(String accountId) {
    return subdomainUrlHelper.getApiBaseUrl(accountId);
  }
}
