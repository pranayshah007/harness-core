/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.dao.intfc.BatchJobIntervalDao;
import io.harness.batch.processing.service.intfc.BatchJobIntervalService;
import io.harness.ccm.commons.entities.batch.BatchJobInterval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BatchJobIntervalServiceImpl implements BatchJobIntervalService {
  @Autowired private BatchJobIntervalDao batchJobIntervalDao;

  @Override
  public BatchJobInterval fetchBatchJobInterval(String accountId, BatchJobType batchJobType) {
    return batchJobIntervalDao.fetchBatchJobInterval(accountId, batchJobType.name());
  }
}
