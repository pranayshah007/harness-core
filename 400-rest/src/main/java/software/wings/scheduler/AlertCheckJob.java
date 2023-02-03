/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.logging.AccountLogContext;
import io.harness.scheduler.PersistentScheduler;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.InvalidSMTPConfigAlert;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateService;
import software.wings.utils.EmailHelperUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;

/**
 * @author brett on 10/17/17
 */
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AlertCheckJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  public static final String GROUP = "ALERT_CHECK_CRON_GROUP";

  private static final int POLL_INTERVAL = 120;
  private static final int START_DELAY_TIME = 300;

  @Inject private DelegateDisconnectAlertHelper delegateDisconnectAlertHelper;
  @Inject private AlertService alertService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private EmailHelperUtils emailHelperUtils;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private DelegateService delegateService;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private Clock clock;

  @Inject private ExecutorService executorService;

  private static TriggerBuilder<SimpleTrigger> alertTriggerBuilder(String accountId) {
    return TriggerBuilder.newTrigger()
        .withIdentity(accountId, GROUP)
        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                          .withIntervalInSeconds(POLL_INTERVAL)
                          .repeatForever()
                          .withMisfireHandlingInstructionFireNow());
  }

  public TriggerBuilder<SimpleTrigger> getAlertTriggerBuilder(String accountId) {
    return alertTriggerBuilder(accountId);
  }
  public static void addWithDelay(PersistentScheduler jobScheduler, String accountId) {
    // Add some randomness in the trigger start time to avoid overloading quartz by firing jobs at the same time.
    long startTime = System.currentTimeMillis() + random.nextInt((int) TimeUnit.SECONDS.toMillis(START_DELAY_TIME));
    addInternal(jobScheduler, accountId, new Date(startTime));
  }

  public static void add(PersistentScheduler jobScheduler, String accountId) {
    addInternal(jobScheduler, accountId, null);
  }

  private static void addInternal(PersistentScheduler jobScheduler, String accountId, Date triggerStartTime) {
    JobDetail job = JobBuilder.newJob(AlertCheckJob.class)
                        .withIdentity(accountId, GROUP)
                        .usingJobData(ACCOUNT_ID_KEY, accountId)
                        .build();
    TriggerBuilder triggerBuilder = alertTriggerBuilder(accountId);
    if (triggerStartTime != null) {
      triggerBuilder.startAt(triggerStartTime);
    }

    jobScheduler.ensureJob__UnderConstruction(job, triggerBuilder.build());
  }

  public static void delete(PersistentScheduler jobScheduler, String accountId) {
    jobScheduler.deleteJob(accountId, GROUP);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    executorService.submit(
        () -> executeInternal((String) jobExecutionContext.getJobDetail().getJobDataMap().get(ACCOUNT_ID_KEY)));
  }

  @VisibleForTesting
  void executeInternal(String accountId) {
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Checking account " + accountId + " for alert conditions.");
      List<Delegate> delegates = delegateService.getNonDeletedDelegatesForAccount(accountId);

      if (isEmpty(delegates)) {
        Account account = wingsPersistence.get(Account.class, accountId);
        if (account == null) {
          jobScheduler.deleteJob(accountId, GROUP);
          return;
        }
      }
      if (!isEmpty(delegates)) {
        delegateDisconnectAlertHelper.checkIfAnyDelegatesAreDown(accountId, delegates);
      }
      checkForInvalidValidSMTP(accountId);
    } catch (Exception e) {
      log.error("Exception happened in alert check for account {}", accountId);
    }
  }

  @VisibleForTesting
  void checkForInvalidValidSMTP(String accountId) {
    if (!emailHelperUtils.isSmtpConfigValid(mainConfiguration.getSmtpConfig())
        && !emailHelperUtils.isSmtpConfigValid(emailHelperUtils.getSmtpConfig(accountId, false))) {
      alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.INVALID_SMTP_CONFIGURATION,
          InvalidSMTPConfigAlert.builder().accountId(accountId).build());
    } else {
      alertService.closeAlertsOfType(accountId, GLOBAL_APP_ID, AlertType.INVALID_SMTP_CONFIGURATION);
    }
  }
}
