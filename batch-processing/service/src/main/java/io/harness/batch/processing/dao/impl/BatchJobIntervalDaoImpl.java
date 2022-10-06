/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import io.harness.batch.processing.dao.intfc.BatchJobIntervalDao;
import io.harness.ccm.commons.entities.batch.BatchJobInterval;
import io.harness.ccm.commons.entities.batch.BatchJobInterval.BatchJobIntervalKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class BatchJobIntervalDaoImpl implements BatchJobIntervalDao {
  @Autowired @Inject private HPersistence hPersistence;

  @Override
  public boolean create(BatchJobInterval batchJobInterval) {
    return hPersistence.save(batchJobInterval) != null;
  }

  @Override
  public BatchJobInterval fetchBatchJobInterval(String accountId, String batchJobType) {
    return hPersistence.createQuery(BatchJobInterval.class)
        .filter(BatchJobIntervalKeys.accountId, accountId)
        .filter(BatchJobIntervalKeys.batchJobType, batchJobType)
        .get();
  }
}
