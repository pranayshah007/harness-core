/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.audit.AuditRecord;
import software.wings.audit.AuditRecord.AuditRecordKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;

@OwnedBy(PL)
public class EntityAuditRecordHandler extends IteratorPumpAndRedisModeHandler implements Handler<AuditRecord> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofSeconds(45);

  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AuditService auditService;
  @Inject private MorphiaPersistenceRequiredProvider<AuditRecord> persistenceProvider;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<AuditRecord, MorphiaFilterExpander<AuditRecord>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       EntityAuditRecordHandler.class,
                       MongoPersistenceIterator.<AuditRecord, MorphiaFilterExpander<AuditRecord>>builder()
                           .clazz(AuditRecord.class)
                           .fieldName(AuditRecordKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<AuditRecord, MorphiaFilterExpander<AuditRecord>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       EntityAuditRecordHandler.class,
                       MongoPersistenceIterator.<AuditRecord, MorphiaFilterExpander<AuditRecord>>builder()
                           .clazz(AuditRecord.class)
                           .fieldName(AuditRecordKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "EntityAuditRecordProcessor";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(AuditRecord entity) {
    // if not last with match auditHeaderId, EXIT
    AuditRecord mostRecentAuditRecord = auditService.fetchMostRecentAuditRecord(entity.getAuditHeaderId());
    if (mostRecentAuditRecord != null && mostRecentAuditRecord.getUuid() != null
        && !mostRecentAuditRecord.getUuid().equals(entity.getUuid())) {
      // Seems there are more recent records added in "AuditRecord" collection for this AuditHeaderId.
      // existing now, when newest record will be processed, these entityAuditRecords will be copied to actual
      // "AuditHeader" record.
      return;
    }

    while (true) {
      List<AuditRecord> auditRecords = auditService.fetchLimitedEntityAuditRecordsOlderThanGivenTime(
          entity.getAuditHeaderId(), entity.getCreatedAt(), 10000);

      if (isEmpty(auditRecords)) {
        return;
      }

      List<EntityAuditRecord> recordsToBeAdded =
          auditRecords.stream().map(AuditRecord::getEntityAuditRecord).collect(toList());

      if (isNotEmpty(recordsToBeAdded)) {
        auditService.addEntityAuditRecordsToSet(recordsToBeAdded, entity.getAccountId(), entity.getAuditHeaderId());
        auditService.deleteTempAuditRecords(auditRecords.stream().map(AuditRecord::getUuid).collect(toList()));
      }
    }
  }
}
