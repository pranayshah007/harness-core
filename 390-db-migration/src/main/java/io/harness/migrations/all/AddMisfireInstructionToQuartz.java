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
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.AlertCheckJob;
import software.wings.scheduler.LimitVicinityCheckerJob;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SimpleTrigger;

@OwnedBy(HarnessTeam.SPG)
@Slf4j
public class AddMisfireInstructionToQuartz implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler persistentScheduler;

  @Inject private AlertCheckJob alertCheckJob;
  @Inject private LimitVicinityCheckerJob limitVicinityCheckerJob;

  private static final String COLLECTION_NAME = "quartz_triggers";
  private static final String DEBUG_LINE = "QUARTZ_FREQUENCY_MISFIRE_MIGRATION";
  private static final String ALERT_CHECK_CRON_GROUP = "ALERT_CHECK_CRON_GROUP";
  private static final String LIMIT_VICINITY_CHECKER_CRON_GROUP = "LIMIT_VICINITY_CHECKER_CRON_GROUP";

  @Override
  public void migrate() {
    log.info("{}: Starting migration", DEBUG_LINE);
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        log.info(DEBUG_LINE + " Starting Migration for correcting corrupt quartz jobs " + account.getUuid());
        try (AutoLogContext ignore1 = new AccountLogContext(account.getUuid(), OverrideBehavior.OVERRIDE_ERROR)) {
          changeMisfireInstructionForQuartz(account);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_LINE + " Exception while fetching Accounts", ex);
    }
    log.info("{}: Completed migration", DEBUG_LINE);
  }

  private void changeMisfireInstructionForQuartz(Account account) {
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, COLLECTION_NAME);
    BasicDBObject matchCondition =
        new BasicDBObject("keyName", account.getUuid())
            .append("keyGroup",
                new BasicDBObject("$in", Arrays.asList(ALERT_CHECK_CRON_GROUP, LIMIT_VICINITY_CHECKER_CRON_GROUP)));
    BasicDBObject projection = new BasicDBObject("_id", true).append("keyName", true).append("keyGroup", true);
    DBCursor dataRecords = collection.find(matchCondition, projection);
    log.info(DEBUG_LINE + " Migration started for account " + account.getUuid());
    try {
      while (dataRecords.hasNext()) {
        DBObject dataRecord = dataRecords.next();
        String accountId = dataRecord.get("keyName").toString();
        SimpleTrigger trigger = null;
        switch (dataRecord.get("keyGroup").toString()) {
          case ALERT_CHECK_CRON_GROUP:
            trigger = alertCheckJob.getAlertTriggerBuilder(accountId).build();
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
      log.info(DEBUG_LINE + " Migration completed for account " + account.getUuid());
    } catch (Exception e) {
      log.error("{} failed with for account {}", DEBUG_LINE, account.getUuid(), e);
    }
  }
}
