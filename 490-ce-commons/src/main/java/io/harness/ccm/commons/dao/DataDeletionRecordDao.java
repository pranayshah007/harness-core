/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.INCOMPLETE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord.DataDeletionRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;

@OwnedBy(CE)
public class DataDeletionRecordDao {
  private final HPersistence hPersistence;

  @Inject
  public DataDeletionRecordDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  public boolean save(DataDeletionRecord dataDeletionRecord) {
    return hPersistence.save(dataDeletionRecord) != null;
  }

  public DataDeletionRecord getByAccountId(String accountId) {
    return hPersistence.createQuery(DataDeletionRecord.class)
        .field(DataDeletionRecordKeys.accountId)
        .equal(accountId)
        .get();
  }

  public List<DataDeletionRecord> getRecordsToProcess(Integer maxRetryCount, Long timestampThreshold) {
    Query<DataDeletionRecord> query = hPersistence.createQuery(DataDeletionRecord.class)
                                          .field(DataDeletionRecordKeys.status)
                                          .equal(INCOMPLETE)
                                          .field(DataDeletionRecordKeys.retryCount)
                                          .lessThan(maxRetryCount);
    query.or(query.criteria(DataDeletionRecordKeys.lastProcessedAt).doesNotExist(),
        query.criteria(DataDeletionRecordKeys.lastProcessedAt).equal(null),
        query.criteria(DataDeletionRecordKeys.lastProcessedAt).lessThan(timestampThreshold));
    return query.asList();
  }
}
