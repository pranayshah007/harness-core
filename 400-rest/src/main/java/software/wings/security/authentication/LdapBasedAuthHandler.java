/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.USER_DOES_NOT_EXIST;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretText;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.beans.Account;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.logcontext.UserLogContext;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class LdapBasedAuthHandler implements AuthHandler {
  @Inject private SSOSettingService ssoSettingService;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private FeatureFlagService featureFlagService;
  @Inject @Named("PRIVILEGED") private UserMembershipClient userMembershipClient;
  private UserService userService;
  private DomainWhitelistCheckerService domainWhitelistCheckerService;

  @Inject
  public LdapBasedAuthHandler(UserService userService, DomainWhitelistCheckerService domainWhitelistCheckerService) {
    this.userService = userService;
    this.domainWhitelistCheckerService = domainWhitelistCheckerService;
  }

  @Override
  public AuthenticationResponse authenticate(String... credentials) {
    if (credentials == null || credentials.length != 3) {
      throw new WingsException(INVALID_ARGUMENT);
    }

    String username = credentials[0];
    String password = credentials[1];

    User user = getUser(username);
    if (user == null) {
      throw new WingsException(USER_DOES_NOT_EXIST, USER);
    }

    String accountId = isEmpty(credentials[2]) ? user.getDefaultAccountId() : credentials[2];
    Account account = authenticationUtils.getAccount(accountId);
    String uuid = user.getUuid();
    LdapSettings settings = ssoSettingService.getLdapSettingsByAccountId(accountId);
    if (null == settings) {
      // log and throw invalid credential error
      log.error("No LDAP sso settings exists for the account id: " + accountId);
      throw new WingsException(INVALID_CREDENTIAL, USER);
    }
    try (AutoLogContext ignore = new UserLogContext(accountId, uuid, OVERRIDE_ERROR)) {
      log.info("Authenticating via LDAP");
      if (!domainWhitelistCheckerService.isDomainWhitelisted(user, account)) {
        domainWhitelistCheckerService.throwDomainWhitelistFilterException();
      }

      return doAuthenticationAndGetResponseInternal(username, password, user, settings, false);
    } catch (NoAvailableDelegatesException | NoEligibleDelegatesInAccountException e) {
      final String ldapConnectionInvalidRequestMsg =
          "Unable to connect to LDAP server, please try after some time. If the problem persist, please contact your admin";
      if (!account.isNextGenEnabled()) {
        log.warn("NGLDAP: Authentication flow. NG not enabled on account {}", accountId);
        throw new InvalidRequestException(ldapConnectionInvalidRequestMsg);
      }
      boolean userInNGScope = false;
      try {
        userInNGScope =
            NGRestUtils.getResponse(userMembershipClient.isUserInScope(uuid, settings.getAccountId(), null, null));
      } catch (Exception exception) {
        log.error(
            "NGLDAP: Authentication flow, while making a userMembershipClient call to isUserInScope an exception occurred: ",
            exception);
        // don't throw can't connect to NG exception
        throw new InvalidRequestException(ldapConnectionInvalidRequestMsg);
      }

      if (!userInNGScope) {
        log.warn("NGLDAP: Authentication flow. User {} not added to scope in NG for account {}", uuid, accountId);
        throw new InvalidRequestException(ldapConnectionInvalidRequestMsg);
      }

      log.info("NGLDAP: Authenticating via LDAP user {}, in NG for account {}", uuid, accountId);

      try {
        return doAuthenticationAndGetResponseInternal(username, password, user, settings, true);
      } catch (NoAvailableDelegatesException | NoEligibleDelegatesInAccountException ex) {
        log.warn("NGLDAP: Authentication flow. No eligible delegates in account {} for user {} authentication",
            accountId, uuid);
        throw new InvalidRequestException(ldapConnectionInvalidRequestMsg);
      }
    }
  }

  private AuthenticationResponse doAuthenticationAndGetResponseInternal(
      String username, String password, User user, LdapSettings settings, boolean isNg) {
    EncryptedDataDetail settingsEncryptedDataDetail = settings.getEncryptedDataDetails(secretManager);
    SecretText secretText = SecretText.builder()
                                .value(password)
                                .hideFromListing(true)
                                .name(UUID.randomUUID().toString())
                                .scopedToAccount(true)
                                .kmsId(settings.getAccountId())
                                .build();
    EncryptedData encryptedSecret = secretManager.encryptSecret(settings.getAccountId(), secretText, false);
    EncryptedDataDetail passwordEncryptedDataDetail =
        secretManager.getEncryptedDataDetails(settings.getAccountId(), "password", encryptedSecret, null).get();

    SyncTaskContext syncTaskContext;
    if (isNg) {
      syncTaskContext = SyncTaskContext.builder()
                            .accountId(settings.getAccountId())
                            .appId(GLOBAL_APP_ID)
                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                            .ngTask(true)
                            .build();
    } else {
      syncTaskContext = SyncTaskContext.builder()
                            .accountId(settings.getAccountId())
                            .appId(GLOBAL_APP_ID)
                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                            .build();
    }
    LdapResponse authenticationResponse = delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
                                              .authenticate(LdapSettingsMapper.ldapSettingsDTO(settings),
                                                  settingsEncryptedDataDetail, username, passwordEncryptedDataDetail);
    if (authenticationResponse.getStatus() == Status.SUCCESS) {
      return new AuthenticationResponse(user);
    }
    throw new WingsException(INVALID_CREDENTIAL, USER);
  }

  @Override
  public io.harness.ng.core.account.AuthenticationMechanism getAuthenticationMechanism() {
    return AuthenticationMechanism.LDAP;
  }

  protected User getUser(String email) {
    return userService.getUserByEmail(email);
  }
}
