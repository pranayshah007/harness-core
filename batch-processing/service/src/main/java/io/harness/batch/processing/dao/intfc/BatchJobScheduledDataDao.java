/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.intfc;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest;

import java.time.Instant;
import java.util.List;

public interface BatchJobScheduledDataDao {
  boolean create(BatchJobScheduledData batchJobScheduledData);

  BatchJobScheduledData fetchLastBatchJobScheduledData(String accountId, BatchJobType batchJobType);

  void invalidateJobs(CEDataCleanupRequest ceDataCleanupRequest);

  void invalidateJobs(String accountId, List<String> batchJobTypes, Instant instant);
}
