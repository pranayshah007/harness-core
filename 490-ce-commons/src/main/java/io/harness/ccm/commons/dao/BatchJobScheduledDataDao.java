/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData.BatchJobScheduledDataKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Sort;
import javax.annotation.Nullable;

@Singleton
@OwnedBy(CE)
public class BatchJobScheduledDataDao {
  private final HPersistence hPersistence;

  @Inject
  public BatchJobScheduledDataDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  @Nullable
  public BatchJobScheduledData fetchLastBatchJobScheduledData(String accountId, String batchJobType) {
    return hPersistence.createQuery(BatchJobScheduledData.class)
        .filter(BatchJobScheduledDataKeys.accountId, accountId)
        .filter(BatchJobScheduledDataKeys.batchJobType, batchJobType)
        .order(Sort.descending(BatchJobScheduledDataKeys.endAt))
        .get();
  }
}
