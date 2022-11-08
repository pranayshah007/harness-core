/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.scheduler.BackgroundSchedulerLocker;
import io.harness.scheduler.PersistentScheduler;

import software.wings.service.intfc.limits.LimitVicinityHandler;

import com.google.inject.Inject;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;

/**
 * Checks if an account's usage of resources is close to limits and alerts customers regarding same.
 */
@Slf4j
@DisallowConcurrentExecution
public class LimitVicinityCheckerJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  public static final String GROUP = "LIMIT_VICINITY_CHECKER_CRON_GROUP";

  private static final int SYNC_INTERVAL_IN_MINUTES = 30;

  @Inject private ExecutorService executorService;
  @Inject private BackgroundSchedulerLocker persistentLocker;
  @Inject private LimitVicinityHandler limitVicinityHandler;

  private static TriggerBuilder<SimpleTrigger> vicinityTriggerBuilder(String accountId) {
    return TriggerBuilder.newTrigger()
        .withIdentity(accountId, GROUP)
        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                          .withIntervalInMinutes(SYNC_INTERVAL_IN_MINUTES)
                          .repeatForever()
                          .withMisfireHandlingInstructionFireNow());
  }

  public TriggerBuilder<SimpleTrigger> getLimitVicinityTriggerBuilder(String accountId) {
    return vicinityTriggerBuilder(accountId);
  }

  public static void addWithDelay(PersistentScheduler jobScheduler, String accountId) {
    // Add some randomness in the trigger start time to avoid overloading quartz by firing jobs at the same time.
    long startTime =
        System.currentTimeMillis() + random.nextInt((int) TimeUnit.MINUTES.toMillis(SYNC_INTERVAL_IN_MINUTES));
    addInternal(jobScheduler, accountId, new Date(startTime));
  }

  public static void add(PersistentScheduler jobScheduler, String accountId) {
    addInternal(jobScheduler, accountId, null);
  }

  private static void addInternal(PersistentScheduler jobScheduler, String accountId, Date triggerStartTime) {
    JobDetail job = JobBuilder.newJob(LimitVicinityCheckerJob.class)
                        .withIdentity(accountId, GROUP)
                        .usingJobData(ACCOUNT_ID_KEY, accountId)
                        .build();

    TriggerBuilder triggerBuilder = vicinityTriggerBuilder(accountId);
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
    executorService.submit(() -> {
      String accountId = (String) jobExecutionContext.getJobDetail().getJobDataMap().get(ACCOUNT_ID_KEY);
      Objects.requireNonNull(accountId, "[LimitVicinityCheckerJob] Account Id must be passed in job context");
      limitVicinityHandler.checkAndAct(accountId);
    });
  }
}
