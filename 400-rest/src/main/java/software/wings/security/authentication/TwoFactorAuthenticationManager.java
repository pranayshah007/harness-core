/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.User;
import software.wings.beans.loginSettings.events.LoginSettingsTwoFactorAuthEvent;
import software.wings.beans.loginSettings.events.TwoFactorAuthYamlDTO;
import software.wings.features.TwoFactorAuthenticationFeature;
import software.wings.features.api.AccountId;
import software.wings.features.api.PremiumFeature;
import software.wings.features.api.RestrictedApi;
import software.wings.security.JWT_CATEGORY;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class TwoFactorAuthenticationManager {
  @Inject private TOTPAuthHandler totpHandler;
  @Inject private UserService userService;
  @Inject private AccountService accountService;
  @Inject private AuthService authService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject @Named(TwoFactorAuthenticationFeature.FEATURE_NAME) private PremiumFeature twoFactorAuthenticationFeature;
  @Inject private OutboxService outboxService;

  public TwoFactorAuthHandler getTwoFactorAuthHandler(TwoFactorAuthenticationMechanism mechanism) {
    switch (mechanism) {
      default:
        return totpHandler;
    }
  }

  public User authenticate(String jwtTokens) {
    String[] decryptedData = decodeBase64ToString(jwtTokens).split(":");
    if (decryptedData.length < 2) {
      throw new WingsException(ErrorCode.INVALID_CREDENTIAL);
    }
    String jwtToken = decryptedData[0];
    String passcode = decryptedData[1];

    User user = userService.verifyJWTToken(jwtToken, JWT_CATEGORY.MULTIFACTOR_AUTH);
    if (user == null) {
      throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST);
    }
    List<String> accountIds = user.getAccountIds();

    User loggedInUser =
        getTwoFactorAuthHandler(user.getTwoFactorAuthenticationMechanism()).authenticate(user, passcode);
    authService.auditLogin2FA(accountIds, loggedInUser);
    authService.auditLogin2FAToNg(accountIds, loggedInUser);
    return authService.generateBearerTokenForUser(user);
  }

  public TwoFactorAuthenticationSettings createTwoFactorAuthenticationSettings(
      User user, TwoFactorAuthenticationMechanism mechanism) {
    Account account = accountService.get(user.getDefaultAccountId());
    return getTwoFactorAuthHandler(mechanism).createTwoFactorAuthenticationSettings(user, account);
  }

  public User enableTwoFactorAuthenticationSettings(User user, TwoFactorAuthenticationSettings settings) {
    if (settings.getMechanism() == null) {
      throw new WingsException(ErrorCode.INVALID_TWO_FACTOR_AUTHENTICATION_CONFIGURATION, USER);
    }
    getDefaultAccount(user).ifPresent(account -> checkIfOperationIsAllowed(account.getUuid()));

    settings.setTwoFactorAuthenticationEnabled(true);
    if (isNotEmpty(user.getAccounts())) {
      user.getAccounts().forEach(account -> {
        auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Event.Type.ENABLE_2FA);
        ngAuditLoginSettings(
            account.getUuid(), user.isTwoFactorAuthenticationEnabled(), settings.isTwoFactorAuthenticationEnabled());
        log.info("Auditing enabling of 2FA for user={} in account={}", user.getName(), account.getAccountName());
      });
    }

    return applyTwoFactorAuthenticationSettings(user, settings);
  }

  private User applyTwoFactorAuthenticationSettings(User user, TwoFactorAuthenticationSettings settings) {
    return getTwoFactorAuthHandler(settings.getMechanism()).applyTwoFactorAuthenticationSettings(user, settings);
  }

  public User disableTwoFactorAuthentication(User user) {
    // disable 2FA only if admin has not enforced 2FA.
    if (isAllowed2FADisable(user)) {
      if (user.isTwoFactorAuthenticationEnabled() && user.getTwoFactorAuthenticationMechanism() != null) {
        log.info("Disabling 2FA for User={}, tfEnabled={}, tfMechanism={}", user.getEmail(),
            user.isTwoFactorAuthenticationEnabled(), user.getTwoFactorAuthenticationMechanism());
        if (isNotEmpty(user.getAccounts())) {
          user.getAccounts().forEach(account -> {
            auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Event.Type.DISABLE_2FA);
            ngAuditLoginSettings(
                account.getUuid(), user.isTwoFactorAuthenticationEnabled(), !user.isTwoFactorAuthenticationEnabled());
            log.info("Auditing disabling of 2FA for user={} in account={}", user.getName(), account.getAccountName());
          });
        }
        return getTwoFactorAuthHandler(user.getTwoFactorAuthenticationMechanism()).disableTwoFactorAuthentication(user);
      }
    } else {
      log.info("Could not disable 2FA for User={}, tfEnabled={}, tfMechanism={}", user.getEmail(),
          user.isTwoFactorAuthenticationEnabled(), user.getTwoFactorAuthenticationMechanism());
    }
    return user;
  }

  private void ngAuditLoginSettings(
      String accountIdentifier, boolean oldTwoFactorAuthEnabled, boolean newTwoFactorAuthEnabled) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsTwoFactorAuthEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldTwoFactorAuthYamlDTO(
                  TwoFactorAuthYamlDTO.builder().isTwoFactorAuthEnabled(oldTwoFactorAuthEnabled).build())
              .newTwoFactorAuthYamlDTO(
                  TwoFactorAuthYamlDTO.builder().isTwoFactorAuthEnabled(newTwoFactorAuthEnabled).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsTwoFactorAuthEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsTwoFactorAuthEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  private boolean isAllowed2FADisable(User user) {
    return !isEmpty(user.getAccounts());
  }

  private Optional<Account> getDefaultAccount(User user) {
    String defaultAccountId = user.getDefaultAccountId();
    if (isEmpty(defaultAccountId)) {
      if (user.getAccounts() != null) {
        defaultAccountId = user.getAccounts().get(0).getUuid();
      } else {
        throw new InvalidRequestException("No account exists for the user");
      }
    }

    // PL-2771: Need to look up from DB to get up-to-date account settings including whether account-level 2FA is
    // enabled.
    return Optional.of(accountService.get(defaultAccountId));
  }

  public boolean getTwoFactorAuthAdminEnforceInfo(String accountId) {
    return accountService.getTwoFactorEnforceInfo(accountId);
  }

  public boolean isTwoFactorEnabled(String accountId, User user) {
    boolean twoFactorEnabled = false;
    if (accountId != null && user != null) {
      twoFactorEnabled = userService.isTwoFactorEnabled(accountId, user.getUuid());
    }
    return twoFactorEnabled;
  }

  @RestrictedApi(TwoFactorAuthenticationFeature.class)
  public boolean overrideTwoFactorAuthentication(@AccountId String accountId, TwoFactorAdminOverrideSettings settings) {
    try {
      // Update 2FA enforce flag
      accountService.updateTwoFactorEnforceInfo(accountId, settings.isAdminOverrideTwoFactorEnabled());

      // Enable 2FA for all users if admin enforced
      if (settings.isAdminOverrideTwoFactorEnabled()) {
        log.info("Enabling 2FA for all users in the account who have 2FA disabled ={}", accountId);
        boolean success =
            userService.overrideTwoFactorforAccount(accountId, settings.isAdminOverrideTwoFactorEnabled());
        if (success) {
          eventPublishHelper.publishSetup2FAEvent(accountId);
        }
        return success;
      }
    } catch (Exception ex) {
      throw new WingsException(GENERAL_ERROR, USER)
          .addParam("message", "Exception occurred while enforcing Two factor authentication for users");
    }

    return false;
  }

  public boolean sendTwoFactorAuthenticationResetEmail(String userId) {
    User user = userService.get(userId);
    if (user.isTwoFactorAuthenticationEnabled()) {
      return getTwoFactorAuthHandler(user.getTwoFactorAuthenticationMechanism()).resetAndSendEmail(user);
    } else {
      log.warn("Two Factor authentication is not enabled for user [{}]", userId);
      return false;
    }
  }

  public boolean disableTwoFactorAuthentication(String accountId) {
    accountService.updateTwoFactorEnforceInfo(accountId, false);
    userService.getUsersWithThisAsPrimaryAccount(accountId).forEach(user -> {
      totpHandler.disableTwoFactorAuthentication(user);
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Event.Type.DISABLE_2FA);
      log.info("Auditing disabling of 2FA for user={} in accountId={}", user.getName(), accountId);
    });
    return true;
  }

  private void checkIfOperationIsAllowed(String accountId) {
    if (!twoFactorAuthenticationFeature.isAvailableForAccount(accountId)) {
      throw new InvalidRequestException(String.format("Operation not permitted for account [%s]", accountId), USER);
    }
  }
}
