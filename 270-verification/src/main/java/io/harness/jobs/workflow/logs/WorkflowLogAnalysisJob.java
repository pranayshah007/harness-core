/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.workflow.logs;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.exception.WingsException;
import io.harness.jobs.LogMLAnalysisGenerator;
import io.harness.jobs.LogMLClusterGenerator;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.LogRequest;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CVActivityLogService;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

@DisallowConcurrentExecution
@Slf4j
public class WorkflowLogAnalysisJob implements Handler<AnalysisContext> {
  @Transient @Inject private LogAnalysisService analysisService;

  @Transient @Inject private LearningEngineService learningEngineService;

  @Inject private VerificationManagerClient verificationManagerClient;
  @Transient @Inject private VerificationManagerClientHelper managerClientHelper;
  @Transient @Inject private VerificationManagerClient managerClient;

  @Inject private DataStoreService dataStoreService;
  @Inject private CVActivityLogService cvActivityLogService;

  @Override
  public void handle(AnalysisContext analysisContext) {
    if (ExecutionStatus.QUEUED == analysisContext.getExecutionStatus()) {
      learningEngineService.markJobStatus(analysisContext, ExecutionStatus.RUNNING);
    }

    new WorkflowLogAnalysisJob
        .LogAnalysisTask(analysisService, analysisContext, Optional.empty(), learningEngineService, managerClient,
            managerClientHelper, dataStoreService, cvActivityLogService)
        .call();
  }

  @AllArgsConstructor
  public static class LogAnalysisTask implements Callable<Long> {
    private LogAnalysisService analysisService;

    private AnalysisContext context;
    private Optional<JobExecutionContext> jobExecutionContext;
    private LearningEngineService learningEngineService;
    private VerificationManagerClient managerClient;
    private VerificationManagerClientHelper managerClientHelper;
    private DataStoreService dataStoreService;
    private CVActivityLogService cvActivityLogService;

    protected void preProcess(long logAnalysisMinute, String query, Set<String> nodes) {
      if (context.getTestNodes() == null) {
        throw new RuntimeException("Test nodes empty! " + JsonUtils.asJson(context));
      }

      LogRequest logRequest = LogRequest.builder()
                                  .query(query)
                                  .applicationId(context.getAppId())
                                  .stateExecutionId(context.getStateExecutionId())
                                  .workflowId(context.getWorkflowId())
                                  .serviceId(context.getServiceId())
                                  .nodes(nodes)
                                  .logCollectionMinute(logAnalysisMinute)
                                  .build();

      switch (context.getStateType()) {
        case ELK:
        case SUMO:
        case DATA_DOG_LOG:
        case STACK_DRIVER_LOG:
        case SCALYR:
          new LogMLClusterGenerator(learningEngineService, context.getClusterContext(), ClusterLevel.L1,
              ClusterLevel.L2, logRequest, (int) context.getStartDataCollectionMinute())
              .run();
          break;
        case LOGZ:
        case BUG_SNAG:
        case LOG_VERIFICATION:
          new LogMLClusterGenerator(
              learningEngineService, context.getClusterContext(), ClusterLevel.L1, ClusterLevel.L2, logRequest, 0)
              .run();
          break;
        case SPLUNKV2:
          break;
        default:
          throw new RuntimeException("Unknown verification state " + context.getStateType());
      }
    }

    @Override
    public Long call() {
      boolean completeCron = false;
      boolean error = false;
      String errorMsg = "";
      long logAnalysisMinute = -1;
      try {
        log.info("running log ml analysis for " + context.getStateExecutionId());
        /*
         * Work flow is invalid
         * exit immediately
         */
        boolean createExperiment;
        if (!learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
          log.warn(" log ml analysis : state is not valid " + context.getStateExecutionId());
          learningEngineService.markJobStatus(context, ExecutionStatus.SUCCESS);
          completeCron = true;
          return -1L;
        }

        if (managerClientHelper.isFeatureFlagEnabled(FeatureName.OUTAGE_CV_DISABLE, context.getAccountId())) {
          cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionId())
              .info("Continuous Verification is disabled for your account. Please contact harness support.");
          completeCron = true;
        }

        if (analysisService.isProcessingComplete(context.getQuery(), context.getAppId(), context.getStateExecutionId(),
                context.getStateType(), context.getTimeDuration(), context.getStartDataCollectionMinute(),
                context.getAccountId())) {
          completeCron = true;

        } else {
          long logAnalysisClusteringTestMinute = analysisService.getCollectionMinuteForLevel(context.getQuery(),
              context.getAppId(), context.getStateExecutionId(), context.getStateType(), ClusterLevel.L1,
              analysisService.getCollectedNodes(context, ClusterLevel.L1));
          log.info("For {} logAnalysisClusteringTestMinute is {}", context.getStateExecutionId(),
              logAnalysisClusteringTestMinute);
          if (logAnalysisClusteringTestMinute != -1) {
            boolean hasTestRecords =
                analysisService.hasDataRecords(context.getQuery(), context.getAppId(), context.getStateExecutionId(),
                    context.getStateType(), analysisService.getCollectedNodes(context, ClusterLevel.L1),
                    ClusterLevel.L1, logAnalysisClusteringTestMinute);
            log.info("For {} hasTestRecords is {}", context.getStateExecutionId(), hasTestRecords);
            if (hasTestRecords) {
              preProcess(logAnalysisClusteringTestMinute, context.getQuery(),
                  analysisService.getCollectedNodes(context, ClusterLevel.L1));
            } else {
              analysisService.bumpClusterLevel(context.getStateType(), context.getStateExecutionId(),
                  context.getAppId(), context.getQuery(), analysisService.getCollectedNodes(context, ClusterLevel.L1),
                  logAnalysisClusteringTestMinute, ClusterLevel.getHeartBeatLevel(ClusterLevel.L1),
                  ClusterLevel.getHeartBeatLevel(ClusterLevel.L2));
            }
          }

          logAnalysisMinute = analysisService.getCollectionMinuteForLevel(context.getQuery(), context.getAppId(),
              context.getStateExecutionId(), context.getStateType(), ClusterLevel.L2,
              analysisService.getCollectedNodes(context, ClusterLevel.L2));
          if (logAnalysisMinute != -1) {
            log.info("For {} logAnalysisMinute is {}", context.getStateExecutionId(), logAnalysisMinute);
            if (learningEngineService.hasAnalysisTimedOut(
                    context.getAppId(), context.getWorkflowExecutionId(), context.getStateExecutionId())) {
              learningEngineService.markStatus(context.getWorkflowExecutionId(), context.getStateExecutionId(),
                  logAnalysisMinute, ExecutionStatus.FAILED);
              throw new WingsException("Error running log analysis. Finished all retries. stateExecutionId: "
                  + context.getStateExecutionId());
            }

            /*
             * Run even if we don't have test data, since we may have control data for this minute.
             * If not, then the control data for this minute will be lost forever. If control is present
             * and no test, the control data is processed and added to the result. If test is present, but no control,
             * the test events are saved for future processing.
             */
            createExperiment = logAnalysisMinute >= context.getTimeDuration() - 1;
            new LogMLAnalysisGenerator(context, logAnalysisMinute, createExperiment, analysisService,
                learningEngineService, managerClient, managerClientHelper, MLAnalysisType.LOG_ML)
                .run();

          } else {
            log.info("No data for log ml analysis " + context.getStateExecutionId());
          }
        }

        // if no data generated till this time, create a dummy summary so UI can get a response
        if (!analysisService.isAnalysisPresent(context.getStateExecutionId(), context.getAppId())) {
          analysisService.createAndSaveSummary(context.getStateType(), context.getAppId(),
              context.getStateExecutionId(), context.getQuery(), "No data found for the given queries.",
              context.getAccountId());
        }

        return logAnalysisMinute;
      } catch (Exception ex) {
        error = true;
        log.info("Verification Analysis failed for {}", context.getStateExecutionId(), ex);
      } finally {
        try {
          // send notification to state manager and delete cron.
          if (completeCron || !learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
            try {
              log.info(
                  "send notification to state manager and delete cron with error : {} errorMsg : {}", error, errorMsg);
              if (managerClientHelper
                      .callManagerWithRetry(
                          managerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, context.getAccountId()))
                      .getResource()) {
                new LogMLAnalysisGenerator(context, logAnalysisMinute, false, analysisService, learningEngineService,
                    managerClient, managerClientHelper, MLAnalysisType.LOG_ML)
                    .sendStateNotification(context, error, errorMsg, (int) logAnalysisMinute);
                try {
                  if (jobExecutionContext.isPresent()) {
                    jobExecutionContext.get().getScheduler().deleteJob(
                        jobExecutionContext.get().getJobDetail().getKey());
                  }
                } catch (Exception e) {
                  log.error("for {} Delete cron failed", context.getStateExecutionId(), e);
                }
              }
            } catch (Exception e) {
              log.error("Send notification failed for {} log analysis manager", context.getStateExecutionId(), e);
            }
          }
        } catch (Exception ex) {
          log.error("analysis failed", ex);
        }
      }

      return -1L;
    }
  }
}
