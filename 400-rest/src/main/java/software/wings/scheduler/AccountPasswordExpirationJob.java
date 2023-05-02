/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.User;
import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.loginSettings.PasswordExpirationPolicy;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

/**
 * Have to add the password expiration double check case.
 */
@Slf4j
@DisallowConcurrentExecution
@OwnedBy(CDC)
@TargetModule(_360_CG_MANAGER)
public class AccountPasswordExpirationJob implements Job {
  public static final String NAME = "LOGIN_SETTINGS_CRON";
  public static final String GROUP = "PASSWORD_POLICY_EXPIRATION";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserServiceImpl userService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AccountService accountService;
  @Inject private LoginSettingsService loginSettingsService;

  public static void add(PersistentScheduler jobScheduler) {
    JobDetail job = JobBuilder.newJob(AccountPasswordExpirationJob.class).withIdentity(NAME, GROUP).build();

    // the job should run daily at 12 hours 0 mins.
    Trigger trigger = TriggerBuilder.newTrigger()
                          .withIdentity(NAME, GROUP)
                          .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(12, 0))
                          .build();

    jobScheduler.ensureJob__UnderConstruction(job, trigger);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    checkAccountPasswordExpiration();
  }

  public void checkAccountPasswordExpiration() {
    try (HIterator<User> userIterator =
             new HIterator<>(wingsPersistence.createQuery(User.class, excludeAuthority).limit(NO_LIMIT).fetch())) {
      Account account;
      for (User user : userIterator) {
        try {
          account = accountService.get(user.getDefaultAccountId());
          LoginSettings loginSettings = loginSettingsService.getLoginSettings(account.getUuid());
          PasswordExpirationPolicy passwordExpirationPolicy = loginSettings.getPasswordExpirationPolicy();

          // Skip inactive accounts.
          if (account.getLicenseInfo() != null
              && !account.getLicenseInfo().getAccountStatus().equals(AccountStatus.ACTIVE)) {
            continue;
          }

          // Mails will only be sent if the login mechanism of the account is USER_PASSWORD.
          if (account.getAuthenticationMechanism() == null
              || account.getAuthenticationMechanism() == AuthenticationMechanism.USER_PASSWORD) {
            if (!passwordExpirationPolicy.isEnabled()) {
              continue;
            }
            checkForPasswordExpiration(passwordExpirationPolicy, user);
          } else {
            log.info(
                "Skipping AccountPasswordExpirationCheckJob for accountId {} because auth mechanism is not User password",
                account.getUuid());
          }
        } catch (Exception ex) {
          log.error("CheckAccountPasswordExpiration failed for User: {}", user.getEmail(), ex);
        }
      }
    } catch (Exception ex) {
      log.error("Error while running AccountPasswordExpirationCheckJob", ex);
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
