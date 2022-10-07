package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.threading.Morpheus.sleep;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;
import io.harness.scheduler.PersistentScheduler;

import software.wings.scheduler.AlertCheckJob;
import software.wings.scheduler.InstanceStatsCollectorJob;
import software.wings.scheduler.LimitVicinityCheckerJob;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SimpleTrigger;

@OwnedBy(HarnessTeam.SPG)
@Slf4j
public class CorrectingQuartzTriggerFrequency implements Migration {
  @Inject private HPersistence persistence;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler persistentScheduler;

  private static final String COLLECTION_NAME = "quartz_triggers";
  private static final String STATE = "waiting";
  private static final String DEBUG_LINE = "QUARTZ_FREQUENCY_CORRECTION_MIGRATION";

  private static final String ALERT_CHECK_CRON_GROUP = "ALERT_CHECK_CRON_GROUP";
  private static final String INSTANCE_STATS_COLLECT_CRON_GROUP = "INSTANCE_STATS_COLLECT_CRON_GROUP";

  private static final String LIMIT_VICINITY_CHECKER_CRON_GROUP = "LIMIT_VICINITY_CHECKER_CRON_GROUP";

  private static final Long THRESHOLD_DELAY = 10000L;

  @Override
  public void migrate() {
    log.info("{}: Starting migration", DEBUG_LINE);

    DBCollection collection = persistence.getCollection(DEFAULT_STORE, COLLECTION_NAME);
    BulkWriteOperation bulkOperation = collection.initializeUnorderedBulkOperation();
    BasicDBObject matchCondition = new BasicDBObject("state", STATE);
    BasicDBObject projection = new BasicDBObject("_id", true)
                                   .append("nextFireTime", true)
                                   .append("previousFireTime", true)
                                   .append("repeatInterval", true)
                                   .append("keyName", true)
                                   .append("keyGroup", true);
    DBCursor dataRecords = collection.find(matchCondition, projection).limit(1000);
    int counter = 0;
    try {
      while (dataRecords.hasNext()) {
        DBObject dataRecord = dataRecords.next();
        Date nextFireTime = (Date) dataRecord.get("nextFireTime");
        Date previousFireTime = (Date) dataRecord.get("previousFireTime");
        Long repeatInterval = (Long) dataRecord.get("repeatInterval");
        counter++;
        // Difference between previousFireTime and nextFireTime should match with interval timing (max allowed
        // difference is 10s) also Next fireTime should always be in the future, if any of these condition fails, we
        // reschedule the quartz job
        if (previousFireTime != null && nextFireTime != null
            && (((nextFireTime.getTime() - previousFireTime.getTime()) - repeatInterval > THRESHOLD_DELAY)
                || nextFireTime.before(Date.from(Instant.now())))) {
          SimpleTrigger trigger = null;
          String accountId = dataRecord.get("keyName").toString();
          switch (dataRecord.get("keyGroup").toString()) {
            case ALERT_CHECK_CRON_GROUP:
              trigger = AlertCheckJob.alertTriggerBuilder(accountId).build();
              break;
            case INSTANCE_STATS_COLLECT_CRON_GROUP:
              trigger = InstanceStatsCollectorJob.instanceStatsTriggerBuilder(accountId).build();
              break;
            case LIMIT_VICINITY_CHECKER_CRON_GROUP:
              trigger = LimitVicinityCheckerJob.vicinityTriggerBuilder(accountId).build();
              break;
            default:
              continue;
          }
          if (trigger != null) {
            persistentScheduler.rescheduleJob(trigger.getKey(), trigger);
          }
        }
        if (counter != 0 && counter % 1000 == 0) {
          bulkOperation.execute();
          sleep(Duration.ofMillis(200));
          bulkOperation = collection.initializeUnorderedBulkOperation();
          dataRecords = collection.find(matchCondition, projection).limit(1000);
        }
      }
    } catch (Exception e) {
      log.error("{} failed with exception {}", DEBUG_LINE, e.getMessage());
    }
  }
}
