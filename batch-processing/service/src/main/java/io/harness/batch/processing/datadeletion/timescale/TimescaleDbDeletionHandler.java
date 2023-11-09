/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.timescale;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.batch.processing.billing.timeseries.service.support.BillingDataTableNameProvider.getTableName;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_AGGREGATION;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_HOURLY;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.TIMESCALE_DB_DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.PodCountComputationServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;

import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class TimescaleDbDeletionHandler extends DataDeletionHandler {
  @Autowired private PodCountComputationServiceImpl podCountComputationService;
  @Autowired private AnomalyService anomalyService;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Autowired private K8sRecommendationDAO recommendationDAO;
  @Autowired private CostEventService costEventService;
  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationDataService;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;

  TimescaleDbDeletionHandler() {
    super(TIMESCALE_DB_DELETION);
  }

  @Override
  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    String accountId = dataDeletionRecord.getAccountId();
    boolean dryRun = dataDeletionRecord.getDryRun();
    log.info("Executing step: {} for accountId: {}", dataDeletionStep, accountId);
    try {
      boolean deleted;
      long recordsCount;
      String tableName;
      switch (dataDeletionStep) {
        case TIMESCALE_ACTIVE_POD_COUNT:
          recordsCount = podCountComputationService.getActivePodCountForAccount(accountId);
          deleted = dryRun || podCountComputationService.deleteActivePodCountForAccount(accountId);
          break;
        case TIMESCALE_ANOMALIES:
          recordsCount = anomalyService.count(accountId);
          deleted = dryRun || anomalyService.deleteAllForAccount(accountId);
          break;
        case TIMESCALE_BILLING_DATA:
          tableName = getTableName(INSTANCE_BILLING);
          recordsCount = billingDataService.getTimescaleBillingDataCount(accountId, tableName);
          deleted = dryRun || billingDataService.deleteTimescaleBillingData(accountId, tableName);
          break;
        case TIMESCALE_BILLING_DATA_AGGREGATED:
          tableName = getTableName(INSTANCE_BILLING_AGGREGATION);
          recordsCount = billingDataService.getTimescaleBillingDataCount(accountId, tableName);
          deleted = dryRun || billingDataService.deleteTimescaleBillingData(accountId, tableName);
          break;
        case TIMESCALE_BILLING_DATA_HOURLY:
          tableName = getTableName(INSTANCE_BILLING_HOURLY);
          recordsCount = billingDataService.getTimescaleBillingDataCount(accountId, tableName);
          deleted = dryRun || billingDataService.deleteTimescaleBillingData(accountId, tableName);
          break;
        case TIMESCALE_BILLING_DATA_HOURLY_AGGREGATED:
          tableName = getTableName(INSTANCE_BILLING_HOURLY_AGGREGATION);
          recordsCount = billingDataService.getTimescaleBillingDataCount(accountId, tableName);
          deleted = dryRun || billingDataService.deleteTimescaleBillingData(accountId, tableName);
          break;
        case TIMESCALE_BUDGET_ALERTS:
          recordsCount = budgetTimescaleQueryHelper.getCountForAccount(accountId);
          deleted = dryRun || budgetTimescaleQueryHelper.deleteAllForAccount(accountId);
          break;
        case TIMESCALE_CE_RECOMMENDATIONS:
          recordsCount = recommendationDAO.countRecommendationsForAccount(accountId);
          deleted = dryRun || recommendationDAO.deleteAllRecommendationsForAccount(accountId);
          break;
        case TIMESCALE_COST_EVENT_DATA:
          recordsCount = costEventService.count(accountId);
          deleted = dryRun || costEventService.deleteAllForAccount(accountId);
          break;
        case TIMESCALE_KUBERNETES_UTILIZATION_DATA:
          recordsCount = k8sUtilizationDataService.count(accountId);
          deleted = dryRun || k8sUtilizationDataService.deleteAllForAccount(accountId);
          break;
        case TIMESCALE_NODE_INFO:
          recordsCount = instanceInfoTimescaleDAO.countNodeInfoForAccount(accountId);
          deleted = dryRun || instanceInfoTimescaleDAO.deleteNodeInfoForAccount(accountId);
          break;
        case TIMESCALE_NODE_POOL_AGGREGATED:
          recordsCount = recommendationDAO.countNodePoolAggregatedForAccount(accountId);
          deleted = dryRun || recommendationDAO.deleteAllNodePoolAggregatedForAccount(accountId);
          break;
        case TIMESCALE_UTILIZATION_DATA:
          recordsCount = utilizationDataService.count(accountId);
          deleted = dryRun || utilizationDataService.deleteAllForAccount(accountId);
          break;
        case TIMESCALE_POD_INFO:
          recordsCount = instanceInfoTimescaleDAO.countPodInfoForAccount(accountId);
          deleted = dryRun || instanceInfoTimescaleDAO.deletePodInfoForAccount(accountId);
          break;
        case TIMESCALE_WORKLOAD_INFO:
          recordsCount = instanceInfoTimescaleDAO.countWorkloadInfoForAccount(accountId);
          deleted = dryRun || instanceInfoTimescaleDAO.deleteWorkloadInfoForAccount(accountId);
          break;
        default:
          log.warn("Unknown step: {} for accountId: {}", dataDeletionStep, accountId);
          return true;
      }
      dataDeletionRecord.getRecords().get(dataDeletionStep.name()).setRecordsCount(recordsCount);
      return deleted;
    } catch (Exception e) {
      log.error("Caught an exception while executing step: {}, accountId: {}", dataDeletionStep, accountId, e);
      return false;
    }
  }
}
