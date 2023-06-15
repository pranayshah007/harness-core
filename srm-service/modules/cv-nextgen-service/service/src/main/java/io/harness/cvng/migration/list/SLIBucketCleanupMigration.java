/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UuidAware;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIBucketCleanupMigration implements CVNGMigration {
  @Inject HPersistence hPersistence;

  private static final int BATCH_SIZE = 1000;
  @Override
  public void migrate() {
    log.info("Starting cleanup for sli Bucket records");
    Query<SLIRecordBucket> queryToDeleteOlderBucketRecords =
        hPersistence.createQuery(SLIRecordBucket.class).project(UuidAware.UUID_KEY, true);
    while (true) {
      List<SLIRecordBucket> recordsToBeDeleted =
          queryToDeleteOlderBucketRecords.find(new FindOptions().limit(BATCH_SIZE)).toList();
      Set<?> recordIdsTobeDeleted = recordsToBeDeleted.stream()
                                        .map(recordToBeDeleted -> ((UuidAware) recordToBeDeleted).getUuid())
                                        .map(CVNGObjectUtils::convertToObjectIdIfRequired)
                                        .collect(Collectors.toSet());
      if (recordIdsTobeDeleted.size() == 0) {
        break;
      }
      hPersistence.delete(
          hPersistence.createQuery(SLIRecordBucket.class).field(UuidAware.UUID_KEY).in(recordIdsTobeDeleted));
      log.info("[SLI Bucket Migration] Deleted {} sliBucket records", recordsToBeDeleted.size());
    }
    log.info("[SLI Bucket Migration] Deleted all sli bucket records");
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
