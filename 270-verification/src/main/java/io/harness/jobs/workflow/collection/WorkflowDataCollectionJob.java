/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.workflow.collection;

import static software.wings.common.VerificationConstants.DELAY_MINUTES;
import static software.wings.common.VerificationConstants.WORKFLOW_CV_COLLECTION_CRON_GROUP;

import io.harness.beans.FeatureName;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.scheduler.PersistentScheduler;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.ContinuousVerificationService;

import software.wings.service.impl.analysis.AnalysisContext;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;

/**
 * Created by Pranjal on 02/06/2019
 */
@Slf4j
public class WorkflowDataCollectionJob implements Job, Handler<AnalysisContext> {
  @Inject private VerificationManagerClientHelper verificationManagerClientHelper;
  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    log.info("Triggering Data collection job");
    try {
      String params = jobExecutionContext.getMergedJobDataMap().getString("jobParams");
      AnalysisContext context = JsonUtils.asObject(params, AnalysisContext.class);
      if (!verificationManagerClientHelper
               .callManagerWithRetry(verificationManagerClient.isFeatureEnabled(
                   FeatureName.WORKFLOW_DATA_COLLECTION_ITERATOR, context.getAccountId()))
               .getResource()) {
        log.info("Executing Workflow data collection job with params : {} and context : {}", params, context);
        boolean jobTriggered = continuousVerificationService.triggerWorkflowDataCollection(context);
        if (!jobTriggered) {
          deleteJob(jobExecutionContext);
        }
        log.info("Triggering scheduled job with params {}", params);
      } else {
        log.info("for {} the iterator will handle data collection", context.getStateExecutionId());
      }
    } catch (Exception ex) {
      log.error("Data Collection cron failed with error", ex);
    }
  }

  @Override
  public void handle(AnalysisContext context) {
    if (!verificationManagerClientHelper
             .callManagerWithRetry(verificationManagerClient.isFeatureEnabled(
                 FeatureName.WORKFLOW_DATA_COLLECTION_ITERATOR, context.getAccountId()))
             .getResource()) {
      log.info("for {} the cron will handle data collection", context.getStateExecutionId());
      return;
    }
    if (Instant.now().isBefore(Instant.ofEpochMilli(context.getCreatedAt()).plus(DELAY_MINUTES, ChronoUnit.MINUTES))) {
      log.info("for {} the delay of {} mins hasn't reached yet", context.getStateExecutionId(), DELAY_MINUTES);
      return;
    }

    log.info("Executing Workflow data collection iterator for context : {}", context);
    boolean jobTriggered = continuousVerificationService.triggerWorkflowDataCollection(context);
    if (!jobTriggered) {
      continuousVerificationService.markWorkflowDataCollectionDone(context);
      jobScheduler.deleteJob(context.getStateExecutionId(),
          context.getStateType().name().toUpperCase() + WORKFLOW_CV_COLLECTION_CRON_GROUP);
    }
  }

  private void deleteJob(JobExecutionContext jobExecutionContext) {
    try {
      String params = jobExecutionContext.getMergedJobDataMap().getString("jobParams");
      AnalysisContext context = JsonUtils.asObject(params, AnalysisContext.class);
      jobExecutionContext.getScheduler().deleteJob(jobExecutionContext.getJobDetail().getKey());
      continuousVerificationService.markWorkflowDataCollectionDone(context);
      log.info("Deleting Data Collection job for context {}", jobExecutionContext);
    } catch (SchedulerException e) {
      log.error("Unable to clean up cron", e);
    }
  }
}
