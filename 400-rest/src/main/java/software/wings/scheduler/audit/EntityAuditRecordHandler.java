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

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.audit.AuditRecord;
import software.wings.audit.AuditRecord.AuditRecordKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(PL)
public class EntityAuditRecordHandler implements Handler<AuditRecord> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AuditService auditService;
  @Inject private MorphiaPersistenceProvider<AuditRecord> persistenceProvider;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PumpExecutorOptions.builder().name("EntityAuditRecordProcessor").poolSize(2).interval(ofSeconds(30)).build(),
        EntityAuditRecordHandler.class,
        MongoPersistenceIterator.<AuditRecord, MorphiaFilterExpander<AuditRecord>>builder()
            .clazz(AuditRecord.class)
            .fieldName(AuditRecordKeys.nextIteration)
            .targetInterval(ofMinutes(30))
            .acceptableNoAlertDelay(ofSeconds(45))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
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

    List<AuditRecord> auditRecords =
        auditService.fetchEntityAuditRecordsOlderThanGivenTime(entity.getAuditHeaderId(), entity.getCreatedAt());

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
