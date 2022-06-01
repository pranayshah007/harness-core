/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
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
import io.harness.exception.WingsException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.security.encryption.EncryptedDataDetail;

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
  private UserService userService;
  private DomainWhitelistCheckerService domainWhitelistCheckerService;

  @Inject
  public LdapBasedAuthHandler(UserService userService, DomainWhitelistCheckerService domainWhitelistCheckerService) {
    this.userService = userService;
    this.domainWhitelistCheckerService = domainWhitelistCheckerService;
  }

  @Override
  public AuthenticationResponse authenticate(String... credentials) {
    if (credentials == null || credentials.length != 2) {
      throw new WingsException(INVALID_ARGUMENT);
    }

    String username = credentials[0];
    String password = credentials[1];

    User user = getUser(username);
    if (user == null) {
      throw new WingsException(USER_DOES_NOT_EXIST, USER);
    }

    Account account = authenticationUtils.getDefaultAccount(user);
    String accountId = user == null ? null : user.getDefaultAccountId();
    String uuid = user == null ? null : user.getUuid();
    try (AutoLogContext ignore = new UserLogContext(accountId, uuid, OVERRIDE_ERROR)) {
      log.info("Authenticating via LDAP");
      if (!domainWhitelistCheckerService.isDomainWhitelisted(user, account)) {
        domainWhitelistCheckerService.throwDomainWhitelistFilterException();
      }
      LdapSettings settings = ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
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

      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settings.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      LdapResponse authenticationResponse = delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
                                                .authenticate(LdapSettingsMapper.ldapSettingsDTO(settings),
                                                    settingsEncryptedDataDetail, username, passwordEncryptedDataDetail);
      if (authenticationResponse.getStatus() == Status.SUCCESS) {
        return new AuthenticationResponse(user);
      }
      throw new WingsException(INVALID_CREDENTIAL, USER);
    }
  }

  @Override
  public io.harness.ng.core.account.AuthenticationMechanism getAuthenticationMechanism() {
    return AuthenticationMechanism.LDAP;
  }

  protected User getUser(String email) {
    return userService.getUserByEmail(email);
  }
}
