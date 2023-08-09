/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.oauth;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.sso.OauthSettings;
import software.wings.logcontext.UserLogContext;
import software.wings.security.authentication.AuthHandler;
import software.wings.security.authentication.AuthenticationResponse;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.DomainWhitelistCheckerService;
import software.wings.security.authentication.OauthAuthenticationResponse;
import software.wings.service.impl.SSOSettingServiceImpl;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * This class authenticates user using oauth flow.
 * It calls the corresponding OauthProvider for getting the email and once it receives that,
 * applies the authentication mechanism filters to ensure that user is logging using the
 * correct auth mechanism.
 */
@OwnedBy(PL)
@Singleton
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class OauthBasedAuthHandler implements AuthHandler {
  @Inject AuthenticationUtils authenticationUtils;
  @Inject UserService userService;
  @Inject MainConfiguration mainConfiguration;
  @Inject SSOSettingServiceImpl ssoSettingService;
  @Inject OauthOptions oauthOptions;
  @Inject DomainWhitelistCheckerService domainWhitelistCheckerService;
  @Inject SignupService signupService;

  @Override
  public AuthenticationMechanism getAuthenticationMechanism() {
    return AuthenticationMechanism.OAUTH;
  }

  /**
   * @param credentials - Array of string having 3 values.
   *  1) authorization_code - Special code which is to be used in further communication with the service provider.
   *  2) state - this is special random jwt token code service provider sends when redirecting to harness as get param.
   *             This code is generated from harness side and the auth provider sends it back to confirm that the
   * request initiated from Harness and prevents chances of CSRF attack. 3) provider - The oauth provider sending the
   * request
   * @return the user associated with the email. If the user does not exists in harness, create and return for freemium
   *     cluster.
   */
  @Override
  public AuthenticationResponse authenticate(String... credentials)
      throws InterruptedException, ExecutionException, IOException {
    if (credentials == null || credentials.length != 3) {
      throw new WingsException("Invalid arguments while authenticating using oauth");
    }
    final String code = credentials[0];
    final String state = credentials[1];
    final String domain = credentials[2];

    OauthClient oauthProvider = getOauthProvider(domain);
    final OauthUserInfo userInfo = oauthProvider.execute(code, state);

    User user = null;
    try {
      user = authenticationUtils.getUserOrReturnNullIfUserDoesNotExists(userInfo.getEmail());
      String accountId = user == null ? null : user.getDefaultAccountId();
      String uuid = user == null ? null : user.getUuid();
      try (AutoLogContext ignore = new UserLogContext(accountId, uuid, OVERRIDE_ERROR)) {
        log.info("Authenticating via OAuth for accountId: {}", accountId);
        // if the email doesn't exists in harness system, sign him up.
        if (null == user) {
          return OauthAuthenticationResponse.builder()
              .oauthUserInfo(userInfo)
              .userFoundInDB(false)
              .oauthClient(oauthProvider)
              .build();
        } else {
          if (!domainWhitelistCheckerService.isDomainWhitelisted(user)) {
            domainWhitelistCheckerService.throwDomainWhitelistFilterException();
          }
          verifyAuthMechanismOfUser(user, oauthProvider);
          return new AuthenticationResponse(user);
        }
      }
    } catch (Exception ex) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED);
    }
  }

  public String getOauthProviderName(String... credentials) {
    if (credentials == null || credentials.length != 3) {
      return null;
    }

    String domain = credentials[2];
    return oauthOptions.getOauthProvider(OauthProviderType.valueOf(domain.toUpperCase())).getName();
  }

  private void verifyAuthMechanismOfUser(User user, OauthClient oauthProvider) {
    matchOauthProvider(user, oauthProvider);
    verifyAccountLevelAuthMechanismEqualsOauth(user);
  }

  private void verifyAccountLevelAuthMechanismEqualsOauth(User user) {
    Account account = userService.getAccountByIdIfExistsElseGetDefaultAccount(user, Optional.empty());
    if (!account.isOauthEnabled()) {
      // Freemium user who has already signed up should get a mail saying his signup is complete and ask him to login on
      // harness website.
      sendTrialSignupCompleteMailForFreeUsers(user);

      log.error(
          String.format("User [{}] tried to login using OauthMechanism while his authentication mechanism was: [{}]"),
          user.getEmail(), account.getAuthenticationMechanism());
      throw new WingsException(ErrorCode.INCORRECT_SIGN_IN_MECHANISM);
    }
  }

  private void sendTrialSignupCompleteMailForFreeUsers(User user) {
    if (mainConfiguration.isTrialRegistrationAllowed()) {
      UserInvite userInvite = signupService.getUserInviteByEmail(user.getEmail());
      signupService.sendTrialSignupCompletedEmail(userInvite);
    }
  }

  private void matchOauthProvider(final User user, final OauthClient oauthClient) {
    String primaryAccountId = authenticationUtils.getDefaultAccount(user).getUuid();
    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(primaryAccountId);
    if (oauthSettings == null) {
      return;
    }
    Set<OauthProviderType> allowedOauthProviders = Sets.newHashSet(oauthSettings.getAllowedProviders());
    log.info("Matching OAuth Provider for user {}, account {}", user.getEmail(), primaryAccountId);
    matchOauthProviderAndAuthMechanism(user.getEmail(), oauthClient.getName(), allowedOauthProviders);
  }

  private void matchOauthProviderAndAuthMechanism(
      String email, String oauthProvider, Set<OauthProviderType> allowedOauthProviders) {
    if (!allowedOauthProviders.contains(OauthProviderType.valueOf(oauthProvider.toUpperCase()))) {
      log.info("Could not login email {} with OAuth provider {} as the allowed providers for the account are {}", email,
          oauthProvider, allowedOauthProviders);
      String errorMsg = "OAuth via " + oauthProvider + " is not enabled for your account";
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, errorMsg, WingsException.USER);
    }
  }

  private OauthClient getOauthProvider(final String domain) {
    return oauthOptions.getOauthProvider(OauthProviderType.valueOf(domain.toUpperCase()));
  }
}
