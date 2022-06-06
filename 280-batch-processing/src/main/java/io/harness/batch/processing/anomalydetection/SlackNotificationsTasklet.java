/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import io.harness.batch.processing.anomalydetection.alerts.service.itfc.AnomalyAlertsService;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.commons.beans.JobConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class SlackNotificationsTasklet implements Tasklet {
  @Autowired @Inject private AnomalyAlertsService alertsService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);

    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    // alertsService.sendAnomalyDailyReport(jobConstants.getAccountId(), startTime);
    return null;
  }
}
