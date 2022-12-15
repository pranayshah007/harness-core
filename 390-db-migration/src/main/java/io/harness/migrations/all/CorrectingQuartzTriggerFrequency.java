/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.AlertCheckJob;
import software.wings.scheduler.InstanceStatsCollectorJob;
import software.wings.scheduler.LimitVicinityCheckerJob;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SimpleTrigger;

@OwnedBy(HarnessTeam.SPG)
@Slf4j
public class CorrectingQuartzTriggerFrequency implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler persistentScheduler;

  @Inject private InstanceStatsCollectorJob instanceStatsCollectorJob;

  @Inject private AlertCheckJob alertCheckJob;
  @Inject private LimitVicinityCheckerJob limitVicinityCheckerJob;

  private static final String COLLECTION_NAME = "quartz_triggers";
  private static final String STATE = "waiting";
  private static final String DEBUG_LINE = "QUARTZ_FREQUENCY_CORRECTION_MIGRATION";

  private static final String ALERT_CHECK_CRON_GROUP = "ALERT_CHECK_CRON_GROUP";
  private static final String INSTANCE_STATS_COLLECT_CRON_GROUP = "INSTANCE_STATS_COLLECT_CRON_GROUP";

  private static final String LIMIT_VICINITY_CHECKER_CRON_GROUP = "LIMIT_VICINITY_CHECKER_CRON_GROUP";

  @Override
  public void migrate() {
    log.info("{}: Starting migration", DEBUG_LINE);
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        log.info(String.join(
            DEBUG_LINE, " Starting Migration for correcting corrupt quartz jobs", account.getAccountName()));
        migrateCorruptQuartzJobsForAnAccount(account);
      }
    } catch (Exception ex) {
      log.info(String.join(DEBUG_LINE, " Exception while fetching Accounts"));
    }
  }

  private void migrateCorruptQuartzJobsForAnAccount(Account account) {
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, COLLECTION_NAME);
    BasicDBObject matchCondition = new BasicDBObject("keyName", account.getUuid()).append("state", STATE);
    BasicDBObject projection = new BasicDBObject("_id", true)
                                   .append("nextFireTime", true)
                                   .append("previousFireTime", true)
                                   .append("repeatInterval", true)
                                   .append("keyName", true)
                                   .append("keyGroup", true);
    DBCursor dataRecords = collection.find(matchCondition, projection);
    log.info(String.join(DEBUG_LINE, " Migration started for account ", account.getUuid()));
    try {
      while (dataRecords.hasNext()) {
        DBObject dataRecord = dataRecords.next();
        Date nextFireTime = (Date) dataRecord.get("nextFireTime");
        Date previousFireTime = (Date) dataRecord.get("previousFireTime");
        Long repeatInterval = (Long) dataRecord.get("repeatInterval");

        if (previousFireTime != null && nextFireTime != null
            && (((nextFireTime.getTime() - previousFireTime.getTime()) != repeatInterval)
                || nextFireTime.before(Date.from(Instant.now())))) {
          SimpleTrigger trigger = null;
          String accountId = dataRecord.get("keyName").toString();
          switch (dataRecord.get("keyGroup").toString()) {
            case ALERT_CHECK_CRON_GROUP:
              trigger = alertCheckJob.getAlertTriggerBuilder(accountId).build();
              break;
            case INSTANCE_STATS_COLLECT_CRON_GROUP:
              trigger = instanceStatsCollectorJob.getInstanceStatsTriggerBuilder(accountId).build();
              break;
            case LIMIT_VICINITY_CHECKER_CRON_GROUP:
              trigger = limitVicinityCheckerJob.getLimitVicinityTriggerBuilder(accountId).build();
              break;
            default:
              continue;
          }
          if (trigger != null) {
            persistentScheduler.rescheduleJob(trigger.getKey(), trigger);
          }
        }
      }
      log.info(String.join(DEBUG_LINE, " Migration completed for account ", account.getUuid()));
    } catch (Exception e) {
      log.error("{} failed with exception {}, for account {}", DEBUG_LINE, e.getMessage(), account.getUuid());
    }
  }
}
