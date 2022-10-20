/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.ng.core.account.AuthenticationMechanism;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.loginSettings.PasswordExpirationPolicy;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.quartz.DisallowConcurrentExecution;

/**
 * Have to add the password expiration double check case.
 */
@Slf4j
@DisallowConcurrentExecution
@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
public class AccountPasswordExpirationHandler implements Handler<User> {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserServiceImpl userService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AccountService accountService;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<User> persistenceProvider;

  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("AccountPasswordExpirationHandler")
            .poolSize(threadPoolSize)
            //.interval(Duration.ofHours(12)) // The job has to run 12 every hours
            .interval(Duration.ofMinutes(10)) // TODO For testing in PR env - replace with above
            .build(),
        User.class,
        MongoPersistenceIterator.<User, MorphiaFilterExpander<User>>builder()
            .clazz(User.class)
            .fieldName(UserKeys.passwordExpiryIteration)
            //.targetInterval(Duration.ofHours(10)) // This will increment the passwordExpiryIteration to 10 hours
            .targetInterval(Duration.ofMinutes(10)) // TODO For testing in PR env - replace with above
            .acceptableNoAlertDelay(Duration.ofHours(12))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(User user) {
    Account account;
    try {
      account = accountService.get(user.getDefaultAccountId());
      if (featureFlagService.isNotEnabled(FeatureName.USE_ACCOUNT_PASSWORD_EXPIRY_ITERATOR_FW, account.getUuid())) {
        log.info(String.format(
            "Feature flag %s is not enabled for account %s - account password expiry task will be handled by legacy quartz job",
            FeatureName.USE_ACCOUNT_PASSWORD_EXPIRY_ITERATOR_FW, account.getUuid()));
        return;
      }
      LoginSettings loginSettings = loginSettingsService.getLoginSettings(account.getUuid());
      PasswordExpirationPolicy passwordExpirationPolicy = loginSettings.getPasswordExpirationPolicy();

      // Mails will only be sent if the login mechanism of the account is USER_PASSWORD.
      if (account.getAuthenticationMechanism() == null
          || account.getAuthenticationMechanism() == AuthenticationMechanism.USER_PASSWORD) {
        log.info("For account %s checking for password expiry through iterator framework", account.getUuid());
        if (!passwordExpirationPolicy.isEnabled()) {
          return;
        }
        checkForPasswordExpiration(passwordExpirationPolicy, user);
      } else {
        log.info(
            "Skipping AccountPasswordExpirationCheckJob for accountId {}, userId {} because auth mechanism is not User password",
            account.getUuid(), user.getUuid());
      }
    } catch (Exception ex) {
      log.error("CheckAccountPasswordExpiration failed for User: {}", user.getEmail(), ex);
    }
  }

  private void checkForPasswordExpiration(PasswordExpirationPolicy passwordExpirationPolicy, User user) {
    log.info("AccountPasswordExpirationJob: processing user: {}", user.getEmail());
    long passwordChangedAt = user.getPasswordChangedAt();

    // for someone who has never changed his password, this value will be 0.
    if (passwordChangedAt == 0) {
      passwordChangedAt = user.getCreatedAt();
    }

    long passwordAgeInDays = Instant.ofEpochMilli(passwordChangedAt).until(Instant.now(), ChronoUnit.DAYS);
    if (hasPasswordExpired(passwordAgeInDays, passwordExpirationPolicy)) {
      markPasswordAsExpired(user);
      userService.sendPasswordExpirationMail(user.getEmail());
    } else if (isPasswordAboutToExpire(passwordAgeInDays, passwordExpirationPolicy)) {
      userService.sendPasswordExpirationWarning(
          user.getEmail(), passwordExpirationPolicy.getDaysBeforeUserNotifiedOfPasswordExpiration());
    }
  }

  private boolean hasPasswordExpired(long passwordAgeInDays, PasswordExpirationPolicy passwordExpirationPolicy) {
    return passwordAgeInDays >= passwordExpirationPolicy.getDaysBeforePasswordExpires();
  }

  private boolean isPasswordAboutToExpire(long passwordAgeInDays, PasswordExpirationPolicy passwordExpirationPolicy) {
    long passwordAgeForSendingWaringMail = passwordExpirationPolicy.getDaysBeforePasswordExpires()
        - passwordExpirationPolicy.getDaysBeforeUserNotifiedOfPasswordExpiration() - 1;
    return passwordAgeInDays == passwordAgeForSendingWaringMail;
  }

  private void markPasswordAsExpired(User user) {
    UpdateOperations<User> operations = wingsPersistence.createUpdateOperations(User.class);
    setUnset(operations, "passwordExpired", true);
    update(user, operations);
  }

  private void update(User user, UpdateOperations<User> operations) {
    Query<User> query = wingsPersistence.createQuery(User.class).filter(ID_KEY, user.getUuid());
    wingsPersistence.update(query, operations);
  }
}
