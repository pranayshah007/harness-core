/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
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
public class K8sGranularUtilizationMetricsReader implements ItemReader<List<String>> {
  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;

  private AtomicBoolean runOnlyOnce;
  private JobParameters parameters;

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
    runOnlyOnce = new AtomicBoolean(false);
  }

  @Override
  public List<String> read() {
    List<String> distinctInstanceIds = null;
    if (!runOnlyOnce.getAndSet(true)) {
      String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
      long startDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_START_DATE));
      long endDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_END_DATE));
      distinctInstanceIds = k8sUtilizationGranularDataService.getDistinctInstantIds(accountId, startDate, endDate);
    }
    return distinctInstanceIds;
  }
}
