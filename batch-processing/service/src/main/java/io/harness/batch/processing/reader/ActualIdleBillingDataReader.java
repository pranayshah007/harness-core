/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import io.harness.batch.processing.billing.timeseries.service.impl.ActualIdleBillingDataServiceImpl;
import io.harness.batch.processing.ccm.ActualIdleCostBatchJobData;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActualIdleBillingDataReader implements ItemReader<ActualIdleCostBatchJobData> {
  @Autowired private ActualIdleBillingDataServiceImpl actualIdleBillingDataService;

  private AtomicBoolean runOnlyOnce;
  private JobParameters parameters;

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
    runOnlyOnce = new AtomicBoolean(false);
  }

  @Override
  public ActualIdleCostBatchJobData read() {
    ActualIdleCostBatchJobData actualIdleCostBatchJobData = null;
    if (!runOnlyOnce.getAndSet(true)) {
      String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
      BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters);
      long startDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_START_DATE));
      long endDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_END_DATE));
      List<ActualIdleCostData> nodeData =
          actualIdleBillingDataService.getActualIdleCostDataForNodes(accountId, startDate, endDate, batchJobType);
      List<ActualIdleCostData> podData =
          actualIdleBillingDataService.getActualIdleCostDataForPods(accountId, startDate, endDate, batchJobType);
      actualIdleCostBatchJobData = ActualIdleCostBatchJobData.builder().nodeData(nodeData).podData(podData).build();
    }
    return actualIdleCostBatchJobData;
  }
}
