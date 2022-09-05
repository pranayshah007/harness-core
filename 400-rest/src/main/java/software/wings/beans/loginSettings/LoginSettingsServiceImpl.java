/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.common.NotificationMessageResolver.NotificationMessageType.USER_LOCKED_NOTIFICATION;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.outbox.api.OutboxService;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.loginSettings.LoginSettings.LoginSettingKeys;
import software.wings.beans.loginSettings.events.LoginSettingsHarnessUsernamePasswordUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsYamlDTO;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.notifications.UserGroupBasedDispatcher;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.passay.PasswordData;

@Slf4j
@Singleton
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class LoginSettingsServiceImpl implements LoginSettingsService {
  static final int DEFAULT_LOCK_OUT_PERIOD = 24;
  static final boolean NOTIFY_USER = true;
  static final int NUMBER_OF_FAILED_ATTEMPTS_ALLOWED_BEFORE_LOCKOUT = 5;
  static final int DAYS_BEFORE_USER_NOTIFIED = 5;
  static final int DAYS_BEFORE_PASSWORD_EXPIRES = 90;

  @Inject WingsPersistence wingsPersistence;
  @Inject UserService userService;
  @Inject UserGroupBasedDispatcher userGroupBasedDispatcher;
  @Inject UserGroupService userGroupService;
  @Inject AuditServiceHelper auditServiceHelper;
  @Inject OutboxService outboxService;

  private PasswordStrengthPolicy getDefaultPasswordStrengthPolicy() {
    return PasswordStrengthPolicy.builder().enabled(false).build();
  }

  private PasswordExpirationPolicy getDefaultPasswordExpirationPolicy() {
    return PasswordExpirationPolicy.builder()
        .enabled(false)
        .daysBeforeUserNotifiedOfPasswordExpiration(DAYS_BEFORE_USER_NOTIFIED)
        .daysBeforePasswordExpires(DAYS_BEFORE_PASSWORD_EXPIRES)
        .build();
  }

  private UserLockoutPolicy getDefaultUserLockoutPolicy() {
    return UserLockoutPolicy.builder()
        .enableLockoutPolicy(false)
        .lockOutPeriod(DEFAULT_LOCK_OUT_PERIOD)
        .notifyUser(NOTIFY_USER)
        .numberOfFailedAttemptsBeforeLockout(NUMBER_OF_FAILED_ATTEMPTS_ALLOWED_BEFORE_LOCKOUT)
        .build();
  }

  @Override
  public void createDefaultLoginSettings(Account account) {
    LoginSettings loginSettings = LoginSettings.builder()
                                      .passwordExpirationPolicy(getDefaultPasswordExpirationPolicy())
                                      .userLockoutPolicy(getDefaultUserLockoutPolicy())
                                      .passwordStrengthPolicy(getDefaultPasswordStrengthPolicy())
                                      .accountId(account.getUuid())
                                      .build();
    wingsPersistence.save(loginSettings);
  }

  @Override
  public LoginSettings getLoginSettings(String accountId) {
    LoginSettings loginSettings =
        wingsPersistence.createQuery(LoginSettings.class).field(LoginSettingKeys.accountId).equal(accountId).get();
    if (loginSettings == null) {
      throw new WingsException(String.format("Login settings not found for account Id: %s", accountId), USER);
    }
    return loginSettings;
  }

  public LoginSettings getLoginSettingsWithId(String loginSettingsId) {
    LoginSettings loginSettings =
        wingsPersistence.createQuery(LoginSettings.class).field(LoginSettingKeys.uuid).equal(loginSettingsId).get();
    if (loginSettings == null) {
      throw new WingsException(
          String.format("Login settings not found for loginSettingsId: %s", loginSettingsId), USER);
    }
    return loginSettings;
  }

  @Override
  public LoginSettings updatePasswordExpirationPolicy(
      String accountId, PasswordExpirationPolicy newPasswordExpirationPolicy) {
    validatePasswordExpirationPolicy(newPasswordExpirationPolicy);
    UpdateOperations<LoginSettings> operations = wingsPersistence.createUpdateOperations(LoginSettings.class);
    setUnset(operations, LoginSettingKeys.passwordExpirationPolicy, newPasswordExpirationPolicy);
    LoginSettings loginSettings = updateAndGetLoginSettings(accountId, operations);
    auditPasswordExpirationPolicy(accountId, loginSettings);
    log.info("Auditing updation of Password Expiration Policy for account={}", accountId);
    return loginSettings;
  }

  @Override
  public LoginSettings updatePasswordStrengthPolicy(String accountId, PasswordStrengthPolicy passwordStrengthPolicy) {
    validatePasswordStrengthPolicy(passwordStrengthPolicy);
    UpdateOperations<LoginSettings> operations = wingsPersistence.createUpdateOperations(LoginSettings.class);
    setUnset(operations, LoginSettingKeys.passwordStrengthPolicy, passwordStrengthPolicy);
    LoginSettings loginSettings = updateAndGetLoginSettings(accountId, operations);
    auditPasswordStrengthPolicy(accountId, loginSettings);
    log.info("Auditing updation of Password Strength Policy for account={}", accountId);
    return loginSettings;
  }

  private void auditPasswordStrengthPolicy(String accountId, LoginSettings loginSettings) {
    LoginSettings auditLoginSettings = loginSettings;
    PasswordExpirationPolicy passwordExpirationPolicy = loginSettings.getPasswordExpirationPolicy();
    auditLoginSettings.setPasswordExpirationPolicy(null);
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, auditLoginSettings, Event.Type.UPDATE);
    loginSettings.setPasswordExpirationPolicy(passwordExpirationPolicy);
  }

  private void auditPasswordExpirationPolicy(String accountId, LoginSettings loginSettings) {
    LoginSettings auditLoginSettings = loginSettings;
    PasswordStrengthPolicy passwordStrengthPolicy = loginSettings.getPasswordStrengthPolicy();
    auditLoginSettings.setPasswordStrengthPolicy(null);
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, auditLoginSettings, Event.Type.UPDATE);
    loginSettings.setPasswordStrengthPolicy(passwordStrengthPolicy);
  }

  private void auditLoginSettings(String accountId, LoginSettings loginSettings) {
    PasswordStrengthPolicy passwordStrengthPolicy = loginSettings.getPasswordStrengthPolicy();
    PasswordExpirationPolicy passwordExpirationPolicy = loginSettings.getPasswordExpirationPolicy();
    loginSettings.setPasswordStrengthPolicy(null);
    loginSettings.setPasswordExpirationPolicy(null);
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, loginSettings, Event.Type.UPDATE);
    loginSettings.setPasswordStrengthPolicy(passwordStrengthPolicy);
    loginSettings.setPasswordExpirationPolicy(passwordExpirationPolicy);
  }

  private void ngAuditLoginSettings(
      String accountIdentifier, LoginSettings oldLoginSettings, LoginSettings newLoginSettings) {
    try {
      outboxService.save(
          LoginSettingsHarnessUsernamePasswordUpdateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .loginSettingsId(newLoginSettings.getUuid())
              .oldLoginSettingsYamlDTO(LoginSettingsYamlDTO.builder().loginSettings(oldLoginSettings).build())
              .newLoginSettingsYamlDTO(LoginSettingsYamlDTO.builder().loginSettings(newLoginSettings).build())
              .build());
    } catch (Exception ex) {
      log.error(
          "For account {} Audit trails for LoginSettings update event failed with exception: ", accountIdentifier, ex);
    }
  }

  @Override
  public LoginSettings updateUserLockoutPolicy(String accountId, UserLockoutPolicy newUserLockoutPolicy) {
    validateUserLockoutPolicy(newUserLockoutPolicy);
    UpdateOperations<LoginSettings> operations = wingsPersistence.createUpdateOperations(LoginSettings.class);
    setUnset(operations, LoginSettingKeys.userLockoutPolicy, newUserLockoutPolicy);
    return updateAndGetLoginSettings(accountId, operations);
  }

  private LoginSettings updateAndGetLoginSettings(String accountId, UpdateOperations<LoginSettings> operations) {
    updateLoginSettings(accountId, operations);
    return getLoginSettings(accountId);
  }

  private LoginSettings updateAndGetLoginSettings(
      String loginSettingsId, String accountId, UpdateOperations<LoginSettings> operations) {
    updateLoginSettingsWithId(loginSettingsId, operations);
    return getLoginSettingsWithId(loginSettingsId);
  }

  private void updateLoginSettingsWithId(String loginSettingsId, UpdateOperations<LoginSettings> operations) {
    Query<LoginSettings> query =
        wingsPersistence.createQuery(LoginSettings.class).filter(LoginSettingKeys.uuid, loginSettingsId);
    wingsPersistence.update(query, operations);
  }

  private void updateLoginSettings(String accountId, UpdateOperations<LoginSettings> operations) {
    update(accountId, operations);
  }

  private void update(String accountId, UpdateOperations<LoginSettings> operations) {
    Query<LoginSettings> query =
        wingsPersistence.createQuery(LoginSettings.class).filter(LoginSettingKeys.accountId, accountId);
    wingsPersistence.update(query, operations);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(LoginSettings.class).filter(LoginSettingKeys.accountId, accountId));
  }

  @Override
  public boolean verifyPasswordStrength(Account account, char[] password) {
    LoginSettings loginSettings = getLoginSettings(account.getUuid());
    PasswordStrengthPolicy passwordStrengthPolicy = loginSettings.getPasswordStrengthPolicy();
    if (passwordStrengthPolicy.isEnabled()) {
      List<PasswordStrengthViolation> passwordPolicyViolations =
          getPasswordStrengthCheckViolationsInternal(passwordStrengthPolicy, password);
      if (!passwordPolicyViolations.isEmpty()) {
        log.info(String.format("Password violates strength policy associated with the account: %s", account.getUuid()));
        throw new WingsException(
            "Password violates strength policy associated with the account. Please contact your Account Administrator.",
            WingsException.USER);
      }
      validatePasswordStrength(passwordStrengthPolicy, password);
    }
    return true;
  }

  @Override
  public PasswordStrengthViolations getPasswordStrengthCheckViolations(Account account, char[] password) {
    LoginSettings loginSettings = getLoginSettings(account.getUuid());
    PasswordStrengthPolicy passwordStrengthPolicy = loginSettings.getPasswordStrengthPolicy();
    List<PasswordStrengthViolation> passwordStrengthViolationsList = null;
    if (passwordStrengthPolicy.isEnabled()) {
      passwordStrengthViolationsList = getPasswordStrengthCheckViolationsInternal(passwordStrengthPolicy, password);
      return new PasswordStrengthViolations(passwordStrengthViolationsList, true);
    }
    return new PasswordStrengthViolations(new ArrayList<>(), false);
  }

  @Override
  public LoginSettings updateLoginSettings(String loginSettingsId, String accountId, LoginSettings loginSettings) {
    validatePasswordExpirationPolicy(loginSettings.getPasswordExpirationPolicy());
    validatePasswordStrengthPolicy(loginSettings.getPasswordStrengthPolicy());
    validateUserLockoutPolicy(loginSettings.getUserLockoutPolicy());

    UpdateOperations<LoginSettings> operations = wingsPersistence.createUpdateOperations(LoginSettings.class);
    setUnset(operations, LoginSettingKeys.passwordExpirationPolicy, loginSettings.getPasswordExpirationPolicy());
    setUnset(operations, LoginSettingKeys.passwordStrengthPolicy, loginSettings.getPasswordStrengthPolicy());
    setUnset(operations, LoginSettingKeys.userLockoutPolicy, loginSettings.getUserLockoutPolicy());
    LoginSettings oldLoginSettings = getLoginSettingsWithId(loginSettingsId);
    LoginSettings newLoginSettings = updateAndGetLoginSettings(loginSettingsId, accountId, operations);
    auditLoginSettings(accountId, newLoginSettings);
    ngAuditLoginSettings(accountId, oldLoginSettings, newLoginSettings);
    log.info("Auditing updation of LoginSettings for account={}", accountId);
    return newLoginSettings;
  }

  @Override
  public PasswordStrengthPolicy getPasswordStrengthPolicy(String accountId) {
    LoginSettings loginSettings = getLoginSettings(accountId);
    return loginSettings.getPasswordStrengthPolicy();
  }

  private List<PasswordStrengthViolation> getPasswordStrengthCheckViolationsInternal(
      PasswordStrengthPolicy passwordStrengthPolicy, char[] password) {
    PasswordData passwordData = new PasswordData(new String(password));
    return EnumSet.allOf(PasswordStrengthChecks.class)
        .stream()
        .filter(passwordStrengthCheck -> !passwordStrengthCheck.validate(passwordData, passwordStrengthPolicy))
        .map(passwordStrengthChecks
            -> new PasswordStrengthViolation(
                passwordStrengthChecks, passwordStrengthChecks.getMinimumCount(passwordStrengthPolicy)))
        .collect(Collectors.toList());
  }

  @Override
  public void updateUserLockoutInfo(User user, Account account, int newCountOfFailedLoginAttempts) {
    UserLockoutPolicy userLockoutPolicy = getLoginSettings(account.getUuid()).getUserLockoutPolicy();
    log.info("Updating user lockout info: {} for user: {}, new failedCount: {}",
        userLockoutPolicy.isEnableLockoutPolicy(), user.getEmail(), newCountOfFailedLoginAttempts);

    createUserLockoutInfoOperationsAndUpdateUser(user, newCountOfFailedLoginAttempts, userLockoutPolicy);
  }

  private void createUserLockoutInfoOperationsAndUpdateUser(
      User user, int newCountOfFailedLoginAttempts, UserLockoutPolicy userLockoutPolicy) {
    UpdateOperations<User> operations = wingsPersistence.createUpdateOperations(User.class);
    updateUserLockoutInfoOperations(user, newCountOfFailedLoginAttempts, userLockoutPolicy, operations);
    userService.updateUser(user.getUuid(), operations);
  }

  private void updateUserLockoutInfoOperations(User user, int newCountOfFailedLoginAttempts,
      UserLockoutPolicy userLockoutPolicy, UpdateOperations<User> operations) {
    boolean shouldLockUser = userLockoutPolicy.isEnableLockoutPolicy()
        && newCountOfFailedLoginAttempts >= userLockoutPolicy.getNumberOfFailedAttemptsBeforeLockout();

    //    boolean shouldUnlockUser =
    //        newCountOfFailedLoginAttempts < userLockoutPolicy.getNumberOfFailedAttemptsBeforeLockout();

    if (shouldLockUser) {
      log.info("Locking user: [{}] because of {} incorrect password attempts", user.getUuid(),
          newCountOfFailedLoginAttempts);
      UserLockoutInfo userLockoutInfo =
          createUserLockoutInfoInstance(newCountOfFailedLoginAttempts, System.currentTimeMillis());
      updateUserLockoutOperations(operations, true, userLockoutInfo);
      sendNotifications(user, userLockoutPolicy);
      if (isNotEmpty(user.getAccounts())) {
        user.getAccounts().forEach(account -> {
          auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Event.Type.LOCK);
          log.info("Auditing locking of user={} in account={}", user.getName(), account.getAccountName());
        });
      }
    } else {
      log.info("Unlocking user: {}, current lock state = {}", user.getUuid(), user.isUserLocked());
      UserLockoutInfo userLockoutInfo =
          createUserLockoutInfoInstance(newCountOfFailedLoginAttempts, user.getUserLockoutInfo().getUserLockedAt());
      updateUserLockoutOperations(operations, false, userLockoutInfo);
    }
  }

  private void sendNotifications(User user, UserLockoutPolicy userLockoutPolicy) {
    if (userLockoutPolicy.isNotifyUser()) {
      userService.sendAccountLockedNotificationMail(user, userLockoutPolicy.getLockOutPeriod());
    }

    List<UserGroup> userGroupsToNotify = userLockoutPolicy.getUserGroupsToNotify();
    if (userGroupsToNotify != null) {
      Notification notification = InformationNotification.builder()
                                      .notificationTemplateId(USER_LOCKED_NOTIFICATION.name())
                                      .notificationTemplateVariables(ImmutableMap.of("lockedUser", user.getEmail()))
                                      .accountId(user.getDefaultAccountId())
                                      .build();

      userGroupsToNotify.forEach(userGroup -> {
        UserGroup userGroupToBeNotified = getCompleteUserGroup(user.getDefaultAccountId(), userGroup.getUuid());
        userGroupBasedDispatcher.dispatch(Arrays.asList(notification), userGroupToBeNotified);
      });
    }
  }

  private UserGroup getCompleteUserGroup(String accountId, String userGroupId) {
    return userGroupService.get(accountId, userGroupId, true);
  }

  private UserLockoutInfo createUserLockoutInfoInstance(int newCountOfFailedLoginAttempts, long userLockedAt) {
    return UserLockoutInfo.builder()
        .numberOfFailedLoginAttempts(newCountOfFailedLoginAttempts)
        .userLockedAt(userLockedAt)
        .build();
  }

  private void updateUserLockoutOperations(
      UpdateOperations<User> operations, boolean isUserLocked, @Nonnull UserLockoutInfo userLockoutInfo) {
    operations.set(UserKeys.userLocked, isUserLocked);
    operations.set(UserKeys.userLockoutInfo, userLockoutInfo);
  }

  @Override
  public boolean isUserLocked(User user, Account account) {
    UserLockoutPolicy userLockoutPolicy = getLoginSettings(account.getUuid()).getUserLockoutPolicy();
    // if the settings are not enforced or user is not locked return false
    if (!userLockoutPolicy.isEnableLockoutPolicy() || !user.isUserLocked()) {
      return false;
    }
    int maxLockOutPeriod = userLockoutPolicy.getLockOutPeriod();
    long userLockoutPeriod =
        Instant.ofEpochMilli(user.getUserLockoutInfo().getUserLockedAt()).until(Instant.now(), ChronoUnit.HOURS);

    // auto unlock
    return userLockoutPeriod < maxLockOutPeriod;
  }

  private void validatePasswordStrength(PasswordStrengthPolicy passwordStrengthPolicy, char[] password) {
    EnumSet.allOf(PasswordStrengthChecks.class).forEach(passwordStrengthChecks -> {
      PasswordData passwordData = new PasswordData(new String(password));

      if (!passwordStrengthChecks.validate(passwordData, passwordStrengthPolicy)) {
        throw new WingsException(
            String.format("Password validation for check %s failed.", passwordStrengthChecks), WingsException.USER);
      }
    });
  }

  private void validatePasswordExpirationPolicy(PasswordExpirationPolicy passwordExpirationPolicy) {
    if (passwordExpirationPolicy.isEnabled()) {
      if (passwordExpirationPolicy.getDaysBeforePasswordExpires() <= 0) {
        throw new InvalidArgumentsException("Days before password expires must be greater than 0.");
      }
      if (passwordExpirationPolicy.getDaysBeforeUserNotifiedOfPasswordExpiration() <= 0) {
        throw new InvalidArgumentsException(
            "Days before user is notified of password expiration must be greater than 0.");
      }
    }
  }

  private void validatePasswordStrengthPolicy(PasswordStrengthPolicy passwordStrengthPolicy) {
    if (passwordStrengthPolicy.isEnabled()) {
      if (passwordStrengthPolicy.getMinNumberOfCharacters() <= 0) {
        throw new InvalidArgumentsException("Minimum length must be greater than 0.");
      }
    }
  }

  private void validateUserLockoutPolicy(UserLockoutPolicy newUserLockoutPolicy) {
    if (newUserLockoutPolicy.isEnableLockoutPolicy()) {
      if (newUserLockoutPolicy.getLockOutPeriod() <= 0
          || newUserLockoutPolicy.getNumberOfFailedAttemptsBeforeLockout() <= 0) {
        throw new InvalidArgumentsException(
            "Lockout period and number of failed attempts before lockout must be greater than 0.");
      }
    }
  }
}
