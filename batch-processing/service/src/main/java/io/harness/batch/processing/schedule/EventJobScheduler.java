/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.batch.processing.YamlPropertyLoaderFactory;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.PodCountComputationServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.WeeklyReportServiceImpl;
import io.harness.batch.processing.budgets.service.impl.BudgetAlertsServiceImpl;
import io.harness.batch.processing.budgets.service.impl.BudgetCostUpdateService;
import io.harness.batch.processing.ccm.BatchJobBucket;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.cleanup.CEDataCleanupRequestService;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.GcpScheduledQueryTriggerAction;
import io.harness.batch.processing.connectors.ConnectorsHealthUpdateService;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.batch.processing.governance.GovernanceRecommendationService;
import io.harness.batch.processing.metrics.ProductMetricsService;
import io.harness.batch.processing.reports.ScheduledReportServiceImpl;
import io.harness.batch.processing.service.AccountExpiryCleanupService;
import io.harness.batch.processing.service.AwsAccountTagsCollectionService;
import io.harness.batch.processing.service.impl.BatchJobBucketLogContext;
import io.harness.batch.processing.service.impl.BatchJobRunningModeContext;
import io.harness.batch.processing.service.impl.BatchJobTypeLogContext;
import io.harness.batch.processing.service.impl.InstanceDataServiceImpl;
import io.harness.batch.processing.service.intfc.BillingDataPipelineHealthStatusService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.tasklet.support.HarnessServiceInfoFetcher;
import io.harness.batch.processing.tasklet.support.K8SWorkloadService;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.batch.processing.view.CEMetaDataRecordUpdateService;
import io.harness.batch.processing.view.ViewCostUpdateService;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@PropertySource(value = "file:batch-processing-config.yml", factory = YamlPropertyLoaderFactory.class)
public class EventJobScheduler {
  @Autowired private List<Job> jobs;
  @Autowired private BatchJobRunner batchJobRunner;
  @Autowired private RecentlyAddedAccountJobRunner recentlyAddedAccountJobRunner;
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;
  @Autowired private WeeklyReportServiceImpl weeklyReportService;
  @Autowired private ScheduledReportServiceImpl scheduledReportService;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private BillingDataPipelineHealthStatusService billingDataPipelineHealthStatusService;
  @Autowired private GcpScheduledQueryTriggerAction gcpScheduledQueryTriggerAction;
  @Autowired private ProductMetricsService productMetricsService;
  @Autowired private BudgetAlertsServiceImpl budgetAlertsService;
  @Autowired private BudgetCostUpdateService budgetCostUpdateService;
  @Autowired private AccountExpiryCleanupService accountExpiryCleanupService;
  @Autowired private HarnessServiceInfoFetcher harnessServiceInfoFetcher;
  @Autowired private InstanceDataServiceImpl instanceDataService;
  @Autowired private K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  @Autowired private ViewCostUpdateService viewCostUpdateService;
  @Autowired private BatchMainConfig batchMainConfig;
  @Autowired private CEMetaDataRecordUpdateService ceMetaDataRecordUpdateService;
  @Autowired private CEDataCleanupRequestService ceDataCleanupRequestService;
  @Autowired private CfClient cfClient;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private ConnectorsHealthUpdateService connectorsHealthUpdateService;
  @Autowired private K8SWorkloadService k8SWorkloadService;
  @Autowired private AwsAccountTagsCollectionService awsAccountTagsCollectionService;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private PodCountComputationServiceImpl podCountComputationService;
  @Autowired private CostEventService costEventService;
  @Autowired private K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private GovernanceRecommendationService governanceRecommendationService;

  @PostConstruct
  public void orderJobs() {
    jobs.sort(Comparator.comparingInt(job -> BatchJobType.valueOf(job.getName()).getOrder()));
  }

  // this job runs every 1 hours "0 0 * ? * *". For debugging, run every minute "* * * ? * *"
  @Scheduled(cron = "0 */20 * * * ?")
  public void runCloudEfficiencyInClusterJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.IN_CLUSTER, true);
  }

  @Scheduled(cron = "0 */30 * * * ?")
  public void runCloudEfficiencyInClusterRecommendationsJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.IN_CLUSTER_RECOMMENDATION, true);
  }

  @Scheduled(cron = "0 0 */1 * * ?") // run every hour
  public void runCloudEfficiencyInClusterNodeRecommendationJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.IN_CLUSTER_NODE_RECOMMENDATION, true);
  }

  @Scheduled(cron = "0 */1 * * * ?")
  public void runRecentlyAddedAccountJob() {
    boolean masterPod = accountShardService.isMasterPod();
    if (masterPod) {
      try {
        recentlyAddedAccountJobRunner.runJobForRecentlyAddedAccounts();
      } catch (Exception ex) {
        log.error("Exception while running runRecentlyAddedAccountJob Job", ex);
      }
    }
  }

  @Scheduled(cron = "0 */15 * * * ?")
  public void runCloudEfficiencyInClusterBillingJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.IN_CLUSTER_BILLING, true);
  }

  @Scheduled(cron = "0 0 * ? * *") // 0 */10 * * * ?   for testing
  public void runCloudEfficiencyOutOfClusterJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.OUT_OF_CLUSTER, true);
  }

  @Scheduled(cron = "0 30 * ? * *") // 0 */10 * * * ?   for testing
  public void runCloudEfficiencyOutOfClusterECSJobs() {
    runCloudEfficiencyEventJobs(BatchJobBucket.OUT_OF_CLUSTER_ECS, true);
  }

  private void runCloudEfficiencyEventJobs(BatchJobBucket batchJobBucket, boolean runningMode) {
    accountShardService.getCeEnabledAccountIds().forEach(account
        -> jobs.stream()
               .filter(job -> BatchJobType.fromJob(job).getBatchJobBucket() == batchJobBucket)
               .forEach(job -> runJob(account, job, runningMode)));
  }

  @Scheduled(cron = "0 0 */6 ? * *")
  public void scanDelayedJobs() {
    log.info("Inside scanning delayed jobs !! ");
    Stream.of(BatchJobBucket.values()).forEach(batchJobBucket -> runCloudEfficiencyEventJobs(batchJobBucket, false));
  }

  // this job runs every 1 hour "0 0 */1 ? * *". For debugging, run every minute "0 * * ? * *"
  @Scheduled(cron = "0 0 */1 ? * *")
  public void sendSegmentEvents() {
    runCloudEfficiencyEventJobs(BatchJobBucket.OTHERS, true);
  }

  //  @Scheduled(cron = "0 * * ? * *")
  //  public void runGcpScheduledQueryJobs() {
  //    accountShardService.getCeEnabledAccountIds().forEach(account ->
  //    gcpScheduledQueryTriggerAction.execute(account));
  //  }

  @Scheduled(cron = "0 0 8 * * ?")
  public void runTimescalePurgeJob() {
    boolean masterPod = accountShardService.isMasterPod();
    if (masterPod) {
      try {
        k8sUtilizationGranularDataService.purgeOldKubernetesUtilData();
      } catch (Exception ex) {
        log.error("Exception while running runTimescalePurgeJob", ex);
      }

      try {
        utilizationDataService.purgeUtilisationData();
      } catch (Exception ex) {
        log.error("Exception while running runTimescalePurgeJob", ex);
      }

      try {
        podCountComputationService.purgeActivePodCount();
      } catch (Exception ex) {
        log.error("Exception while running runTimescalePurgeJob", ex);
      }

      try {
        billingDataService.purgeOldBillingData();
      } catch (Exception ex) {
        log.error("Exception while running runTimescalePurgeJob", ex);
      }

      try {
        billingDataService.purgeOldHourlyBillingData(BatchJobType.INSTANCE_BILLING_HOURLY);
      } catch (Exception ex) {
        log.error("Exception while running purgeOldHourlyBillingData Job", ex);
      }

      try {
        billingDataService.purgeOldHourlyBillingData(BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION);
      } catch (Exception ex) {
        log.error("Exception while running purgeOldHourlyBillingData Job", ex);
      }

      try {
        instanceInfoTimescaleDAO.purgePodInfo();
      } catch (Exception ex) {
        log.error("Exception while running purgePodInfo Job", ex);
      }

      try {
        instanceInfoTimescaleDAO.deleteWorkloadInfo();
      } catch (Exception ex) {
        log.error("Exception while running deleteWorkloadInfo Job", ex);
      }

      try {
        costEventService.purgeCostEventData();
      } catch (Exception ex) {
        log.error("Exception while running purgeCostEventData Job", ex);
      }

      try {
        k8sRecommendationDAO.deleteOldRecommendationData();
      } catch (Exception ex) {
        log.error("Exception while running deleteOldRecommendationData Job", ex);
      }
    }
  }

  @Scheduled(cron = "0 0 */1 ? * *") //  0 */10 * * * ? for testing
  public void runConnectorsHealthStatusJob() {
    boolean masterPod = accountShardService.isMasterPod();
    if (masterPod) {
      try {
        log.info("running billing data pipeline health status service job");
        billingDataPipelineHealthStatusService.processAndUpdateHealthStatus();
      } catch (Exception ex) {
        log.error("Exception while running runConnectorsHealthStatusJob {}", ex);
      }
    }
  }

  //  @Scheduled(cron = "0 0 6 * * ?")
  //  public void runAccountExpiryCleanup() {
  //    boolean masterPod = accountShardService.isMasterPod();
  //    if (masterPod) {
  //      try {
  //        accountExpiryCleanupService.execute();
  //      } catch (Exception ex) {
  //        log.error("Exception while running runAccountExpiryCleanup {}", ex);
  //      }
  //    }
  //  }

  @Scheduled(cron = "${scheduler-jobs-config.weeklyReportsJobCron}")
  public void runWeeklyReportJob() {
    try {
      weeklyReportService.generateAndSendWeeklyReport();
      log.info("Weekly billing report generated and send");
    } catch (Exception ex) {
      log.error("Exception while running weeklyReportJob", ex);
    }
  }

  @Scheduled(cron = "0 */30 * * * ?") // Run every 30 mins. Change to 0 */10 * * * ? for every 10 mins for testing
  public void runScheduledReportJob() {
    // In case jobs take longer time, the jobs will be queued and executed in turn
    try {
      scheduledReportService.generateAndSendScheduledReport();
      log.info("Scheduled reports generated and sent");
    } catch (Exception ex) {
      log.error("Exception while running runScheduledReportJob", ex);
    }
  }

  @Scheduled(cron = "0 0 */1 ? * *")
  public void updateCostMetadatRecord() {
    try {
      ceMetaDataRecordUpdateService.updateCloudProviderMetadata();
      log.info("updated cost data");
    } catch (Exception ex) {
      log.error("Exception while running updateCostMetadatRecord", ex);
    }
  }

  @Scheduled(cron = "0 0 */1 ? * *")
  public void processDataCleanupRequest() {
    boolean masterPod = accountShardService.isMasterPod();
    if (masterPod) {
      try {
        try (AutoLogContext ignore2 = new BatchJobTypeLogContext("DataCleanupRequest", OVERRIDE_ERROR)) {
          ceDataCleanupRequestService.processDataCleanUpRequest();
        }
      } catch (Exception ex) {
        log.error("Exception while running processDataCleanupRequest", ex);
      }
    }
  }

  @Scheduled(cron = "0 30 8 * * ?")
  public void runViewUpdateCostJob() {
    try {
      viewCostUpdateService.updateTotalCost();
      log.info("Updated view total cost");
    } catch (Exception ex) {
      log.error("Exception while running runViewUpdateCostJob", ex);
    }
  }

  @Scheduled(cron = "${scheduler-jobs-config.budgetAlertsJobCron}")
  public void runBudgetAlertsJob() {
    try {
      budgetAlertsService.sendBudgetAndBudgetGroupAlerts();
      log.info("Budget & Budget Groups alerts sent");
    } catch (Exception ex) {
      log.error("Exception while running budgetAlertsJob", ex);
    }
  }

  @Scheduled(cron = "${scheduler-jobs-config.budgetCostUpdateJobCron}")
  public void runBudgetCostUpdateJob() {
    try {
      budgetCostUpdateService.updateCosts();
      log.info("Costs updated for budgets & budget groups");
    } catch (Exception ex) {
      log.error("Exception while running runBudgetCostUpdateJob", ex);
    }
  }

  // Run once a day, midnight
  @Scheduled(cron = "${scheduler-jobs-config.governanceRecommendationJobCronAws}")
  public void runGovernanceRecommendationJob() {
    try {
      log.info("generateRecommendation Running for AWS");
      if (batchMainConfig.getRecommendationConfig().isGovernanceRecommendationEnabledAws()) {
        governanceRecommendationService.generateRecommendation(Collections.singletonList(ConnectorType.CE_AWS));
      }
    } catch (Exception e) {
      log.error("Exception while running runGovernanceRecommendationJob for AWS", e);
    }
  }

  @Scheduled(cron = "${scheduler-jobs-config.governanceRecommendationJobCronAzure}")
  public void runGovernanceRecommendationJobForAzure() {
    try {
      log.info("generateRecommendation Running for Azure");
      if (batchMainConfig.getRecommendationConfig().isGovernanceRecommendationEnabledAzure()) {
        governanceRecommendationService.generateRecommendation(Collections.singletonList(ConnectorType.CE_AZURE));
      }
    } catch (Exception e) {
      log.error("Exception while running runGovernanceRecommendationJob for Azure", e);
    }
  }

  @Scheduled(cron = "${scheduler-jobs-config.connectorHealthUpdateJobCron}")
  public void runNGConnectorsHealthUpdateJob() {
    try {
      if (!batchMainConfig.getConnectorHealthUpdateJobConfig().isEnabled()) {
        log.info("connectorHealthUpdateJob is disabled in config");
        return;
      }
      connectorsHealthUpdateService.update();
      log.info("Updated health of the connectors in NG");
    } catch (Exception ex) {
      log.error("Exception while running runNGConnectorsHealthUpdateJob", ex);
    }
  }

  @Scheduled(cron = "${scheduler-jobs-config.awsAccountTagsCollectionJobCron}") //  0 */10 * * * ? for testing
  public void runAwsAccountTagsCollectionJob() {
    try {
      if (!batchMainConfig.getAwsAccountTagsCollectionJobConfig().isEnabled()) {
        log.info("awsAccountTagsCollectionJobConfig is disabled in config");
        return;
      }
      log.info("running aws account tags collection job");
      awsAccountTagsCollectionService.update();
    } catch (Exception ex) {
      log.error("Exception while running runAwsAccountTagsCollectionJob", ex);
    }
  }

  // log hit/miss rate and size of the LoadingCache periodically for tuning
  @Scheduled(cron = "0 0 */7 ? * *")
  public void printCacheStats() throws IllegalAccessException {
    harnessServiceInfoFetcher.logCacheStats();
    instanceDataService.logCacheStats();
    k8sLabelServiceInfoFetcher.logCacheStats();
    k8SWorkloadService.logCacheStats();
  }

  @Scheduled(cron = "0 0 6 * * ?")
  public void runCfSampleJob() {
    if (cfClient == null) {
      return;
    }
    accountShardService.getCeEnabledAccountIds().forEach(account -> {
      Target target = Target.builder().name(account).identifier(account).build();
      boolean result = cfClient.boolVariation("cf_sample_flag", target, false);
      log.info(format(
          "The feature flag cf_sample_flag resolves to %s for account %s", Boolean.toString(result), target.getName()));
    });
  }

  @SuppressWarnings("squid:S1166") // not required to rethrow exceptions.
  private void runJob(String accountId, Job job, boolean runningMode) {
    if (BatchJobType.K8S_NODE_RECOMMENDATION == BatchJobType.fromJob(job)
        && !featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_AGGREGATE, accountId)) {
      return;
    }

    if (ImmutableSet.of(BatchJobType.K8S_WATCH_EVENT, BatchJobType.K8S_WORKLOAD_RECOMMENDATION)
            .contains(BatchJobType.fromJob(job))
        && accountId.equals("hW63Ny6rQaaGsKkVjE0pJA")) {
      return;
    }

    try {
      BatchJobType batchJobType = BatchJobType.fromJob(job);
      BatchJobBucket batchJobBucket = batchJobType.getBatchJobBucket();

      try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR);
           AutoLogContext ignore1 = new BatchJobBucketLogContext(batchJobBucket.name(), OVERRIDE_ERROR);
           AutoLogContext ignore2 = new BatchJobTypeLogContext(batchJobType.name(), OVERRIDE_ERROR);
           AutoLogContext ignore3 = new BatchJobRunningModeContext(runningMode, OVERRIDE_ERROR)) {
        batchJobRunner.runJob(accountId, job, runningMode);
      }
    } catch (Exception ex) {
      log.error("Exception while running job {}", job);
    }
  }
}
