package software.wings.scheduler.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.audit.AuditHeader;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class AuditRetentionHandler implements Handler<AuditHeader> {
  @Inject private AuditService auditService;
  @Inject private AccountService accountService;
  @Inject private MorphiaPersistenceProvider<AuditHeader> persistenceProvider;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;

  private static int retentionTimeInMonths = 18;

  @Override
  public void handle(AuditHeader auditHeader) {
    log.info("Audit retention scheduler has started");
    long toBeDeletedTillTimestamp = Instant.now().minus(retentionTimeInMonths, ChronoUnit.MONTHS).toEpochMilli();
    auditService.deleteAuditRecords(toBeDeletedTillTimestamp);
    log.info("Audit retention scheduler has ended");
  }
  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("AuditRetentionProcessor")
            .poolSize(2)
            .interval(ofMinutes(2))
            .build(),
        AuditRetentionHandler.class,
        MongoPersistenceIterator.<AuditHeader, MorphiaFilterExpander<AuditHeader>>builder()
            .clazz(AuditHeader.class)
            .fieldName(AuditHeader.AuditHeaderKeys.nextIteration)
            .targetInterval(ofHours(12))
            .acceptableNoAlertDelay(ofHours(14))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }
}
