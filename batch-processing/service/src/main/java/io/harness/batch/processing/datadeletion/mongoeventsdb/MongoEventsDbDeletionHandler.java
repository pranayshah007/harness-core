/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.mongoeventsdb;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.MONGO_EVENTS_DB_DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.tasklet.dao.intfc.DataGeneratedNotificationDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.dao.intfc.AccountShardMappingDao;
import io.harness.batch.processing.dao.intfc.BatchJobScheduledDataDao;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.batch.processing.pricing.pricingprofile.PricingProfileDao;
import io.harness.ccm.billing.dao.CloudBillingTransferRunDao;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.cluster.dao.K8sYamlDao;
import io.harness.ccm.commons.dao.AWSConnectorToBucketMappingDao;
import io.harness.ccm.commons.dao.CECloudAccountDao;
import io.harness.ccm.commons.dao.CEDataCleanupRequestDao;
import io.harness.ccm.commons.dao.CEGcpServiceAccountDao;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.dao.ClusterRecordDao;
import io.harness.ccm.commons.dao.notifications.CCMNotificationsDao;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.RecommendationsIgnoreListDAO;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;
import io.harness.ccm.health.CeExceptionRecordDao;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingHistoryDao;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dao.RuleEnforcementDAO;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.ccm.views.dao.RuleSetDAO;
import io.harness.ccm.views.dao.ViewCustomFieldDao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class MongoEventsDbDeletionHandler extends DataDeletionHandler {
  @Autowired AccountShardMappingDao accountShardMappingDao;
  @Autowired AWSConnectorToBucketMappingDao awsConnectorToBucketMappingDao;
  @Autowired AzureRecommendationDAO azureRecommendationDAO;
  @Autowired BatchJobScheduledDataDao batchJobScheduledDataDao;
  @Autowired BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired BudgetGroupDao budgetGroupDao;
  @Autowired BudgetDao budgetDao;
  @Autowired BusinessMappingDao businessMappingDao;
  @Autowired BusinessMappingHistoryDao businessMappingHistoryDao;
  @Autowired CECloudAccountDao ceCloudAccountDao;
  @Autowired CEClusterDao ceClusterDao;
  @Autowired CEDataCleanupRequestDao ceDataCleanupRequestDao;
  @Autowired CeExceptionRecordDao ceExceptionRecordDao;
  @Autowired CEMetadataRecordDao ceMetadataRecordDao;
  @Autowired CEReportScheduleDao ceReportScheduleDao;
  @Autowired CEViewDao ceViewDao;
  @Autowired CEViewFolderDao ceViewFolderDao;
  @Autowired CloudBillingTransferRunDao cloudBillingTransferRunDao;
  @Autowired ClusterRecordDao clusterRecordDao;
  @Autowired DataGeneratedNotificationDao dataGeneratedNotificationDao;
  @Autowired EC2RecommendationDAO ec2RecommendationDAO;
  @Autowired ECSRecommendationDAO ecsRecommendationDAO;
  @Autowired ECSServiceDao ecsServiceDao;
  @Autowired CEGcpServiceAccountDao gcpServiceAccountDao;
  @Autowired RuleExecutionDAO ruleExecutionDAO;
  @Autowired RuleDAO ruleDAO;
  @Autowired RuleEnforcementDAO ruleEnforcementDAO;
  @Autowired RuleSetDAO ruleSetDAO;
  @Autowired InstanceDataDao instanceDataDao;
  @Autowired K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired K8sWorkloadDao k8sWorkloadDao;
  @Autowired K8sYamlDao k8sYamlDao;
  @Autowired LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  @Autowired CCMNotificationsDao ccmNotificationsDao;
  @Autowired PricingProfileDao pricingProfileDao;
  @Autowired PublishedMessageDao publishedMessageDao;
  @Autowired RecommendationsIgnoreListDAO recommendationsIgnoreListDAO;
  @Autowired ViewCustomFieldDao viewCustomFieldDao;

  MongoEventsDbDeletionHandler() {
    super(MONGO_EVENTS_DB_DELETION);
  }

  @Override
  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    String accountId = dataDeletionRecord.getAccountId();
    boolean dryRun = dataDeletionRecord.getDryRun();
    log.info("Executing step: {} for accountId: {}", dataDeletionStep, accountId);
    try {
      boolean deleted;
      long recordsCount;
      switch (dataDeletionStep) {
        case MONGO_EVENTS_DB_ACCOUNT_SHARD_MAPPING:
          recordsCount = accountShardMappingDao.count(accountId);
          deleted = dryRun || accountShardMappingDao.delete(accountId);
          break;
        case MONGO_EVENTS_DB_AWS_ENTITY_TO_BUCKET_MAPPING:
          recordsCount = awsConnectorToBucketMappingDao.count(accountId);
          deleted = dryRun || awsConnectorToBucketMappingDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_AZURE_RECOMMENDATION:
          recordsCount = azureRecommendationDAO.count(accountId);
          deleted = dryRun || azureRecommendationDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BATCH_JOB_SCHEDULED_DATA:
          recordsCount = azureRecommendationDAO.count(accountId);
          deleted = dryRun || batchJobScheduledDataDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BILLING_DATA_PIPELINE_RECORD:
          recordsCount = billingDataPipelineRecordDao.count(accountId);
          deleted = dryRun || billingDataPipelineRecordDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BUDGET_GROUPS:
          recordsCount = budgetGroupDao.count(accountId);
          deleted = dryRun || budgetGroupDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BUDGETS:
          recordsCount = budgetDao.count(accountId);
          deleted = dryRun || budgetDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BUSINESS_MAPPING:
          recordsCount = businessMappingDao.count(accountId);
          deleted = dryRun || businessMappingDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_BUSINESS_MAPPING_HISTORY:
          recordsCount = businessMappingHistoryDao.count(accountId);
          deleted = dryRun || businessMappingHistoryDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_CLOUD_ACCOUNT:
          recordsCount = ceCloudAccountDao.count(accountId);
          deleted = dryRun || ceCloudAccountDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_CLUSTER:
          recordsCount = ceClusterDao.count(accountId);
          deleted = dryRun || ceClusterDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_DATA_CLEANUP_REQUEST:
          recordsCount = ceDataCleanupRequestDao.count(accountId);
          deleted = dryRun || ceDataCleanupRequestDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_EXCEPTION_RECORD:
          recordsCount = ceExceptionRecordDao.count(accountId);
          deleted = dryRun || ceExceptionRecordDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_METADATA_RECORD:
          recordsCount = ceMetadataRecordDao.count(accountId);
          deleted = dryRun || ceMetadataRecordDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_REPORT_SCHEDULE:
          recordsCount = ceReportScheduleDao.count(accountId);
          deleted = dryRun || ceReportScheduleDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_VIEW:
          recordsCount = ceViewDao.count(accountId);
          deleted = dryRun || ceViewDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CE_VIEW_FOLDER:
          recordsCount = ceViewFolderDao.count(accountId);
          deleted = dryRun || ceViewFolderDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CLOUD_BILLING_TRANSFER_RUNS:
          recordsCount = cloudBillingTransferRunDao.count(accountId);
          deleted = dryRun || cloudBillingTransferRunDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_CLUSTER_RECORDS:
          recordsCount = clusterRecordDao.count(accountId);
          deleted = dryRun || clusterRecordDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_DATA_GENERATED_NOTIFICATION:
          recordsCount = dataGeneratedNotificationDao.count(accountId);
          deleted = dryRun || dataGeneratedNotificationDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_EC2_RECOMMENDATION:
          recordsCount = ec2RecommendationDAO.count(accountId);
          deleted = dryRun || ec2RecommendationDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_ECS_PARTIAL_RECOMMENDATION_HISTOGRAM:
          recordsCount = ecsRecommendationDAO.countPartialHistograms(accountId);
          deleted = dryRun || ecsRecommendationDAO.deleteAllPartialHistogramsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_ECS_SERVICE:
          recordsCount = ecsServiceDao.count(accountId);
          deleted = dryRun || ecsServiceDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_ECS_SERVICE_RECOMMENDATION:
          recordsCount = ecsRecommendationDAO.countRecommendations(accountId);
          deleted = dryRun || ecsRecommendationDAO.deleteAllRecommendationsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GCP_SERVICE_ACCOUNT:
          recordsCount = gcpServiceAccountDao.count(accountId);
          deleted = dryRun || gcpServiceAccountDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GOVERNANCE_RECOMMENDATION:
          recordsCount = ruleExecutionDAO.countRuleRecommendations(accountId);
          deleted = dryRun || ruleExecutionDAO.deleteAllRuleRecommendationsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GOVERNANCE_RULE:
          recordsCount = ruleDAO.count(accountId);
          deleted = dryRun || ruleDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GOVERNANCE_RULE_ENFORCEMENT:
          recordsCount = ruleEnforcementDAO.count(accountId);
          deleted = dryRun || ruleEnforcementDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GOVERNANCE_RULE_EXECUTION:
          recordsCount = ruleExecutionDAO.countRuleExecutions(accountId);
          deleted = dryRun || ruleExecutionDAO.deleteAllRuleExecutionsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_GOVERNANCE_RULE_SET:
          recordsCount = ruleSetDAO.count(accountId);
          deleted = dryRun || ruleSetDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_INSTANCE_DATA:
          recordsCount = instanceDataDao.count(accountId);
          deleted = dryRun || instanceDataDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_K8S_NODE_RECOMMENDATION:
          recordsCount = k8sRecommendationDAO.countNodeRecommendations(accountId);
          deleted = dryRun || k8sRecommendationDAO.deleteAllNodeRecommendationsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_K8S_WORKLOAD:
          recordsCount = k8sWorkloadDao.count(accountId);
          deleted = dryRun || k8sWorkloadDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_K8S_WORKLOAD_RECOMMENDATION:
          recordsCount = k8sRecommendationDAO.countWorkloadRecommendations(accountId);
          deleted = dryRun || k8sRecommendationDAO.deleteAllWorkloadRecommendationsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_K8S_YAML:
          recordsCount = k8sWorkloadDao.count(accountId);
          deleted = dryRun || k8sYamlDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_LAST_RECEIVED_PUBLISHED_MESSAGE:
          recordsCount = lastReceivedPublishedMessageDao.count(accountId);
          deleted = dryRun || lastReceivedPublishedMessageDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_LATEST_CLUSTER_INFO:
          recordsCount = lastReceivedPublishedMessageDao.countLatestClusterInfo(accountId);
          deleted = dryRun || lastReceivedPublishedMessageDao.deleteAllLatestClusterInfoForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_NOTIFICATION_SETTING:
          recordsCount = ccmNotificationsDao.count(accountId);
          deleted = dryRun || ccmNotificationsDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_PARTIAL_RECOMMENDATION_HISTOGRAM:
          recordsCount = k8sRecommendationDAO.countPartialHistograms(accountId);
          deleted = dryRun || k8sRecommendationDAO.deleteAllPartialHistogramsForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_PRICING_PROFILE:
          recordsCount = pricingProfileDao.count(accountId);
          deleted = dryRun || pricingProfileDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_PUBLISHED_MESSAGES:
          recordsCount = publishedMessageDao.count(accountId);
          deleted = dryRun || publishedMessageDao.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_RECOMMENDATIONS_IGNORE_LIST:
          recordsCount = recommendationsIgnoreListDAO.count(accountId);
          deleted = dryRun || recommendationsIgnoreListDAO.deleteAllForAccount(accountId);
          break;
        case MONGO_EVENTS_DB_VIEW_CUSTOM_FIELD:
          recordsCount = viewCustomFieldDao.count(accountId);
          deleted = dryRun || viewCustomFieldDao.deleteAllForAccount(accountId);
          break;
        default:
          log.warn("Unknown step: {} for accountId: {}", dataDeletionStep, accountId);
          return true;
      }
      dataDeletionRecord.getRecords().get(dataDeletionStep.name()).setRecordsCount(recordsCount);
      if (!deleted) {
        log.info("Entities have already been deleted for step: {}, accountId: {}", dataDeletionStep, accountId);
      } else {
        log.info("Entities deletion successful for step: {}, accountId: {}", dataDeletionStep, accountId);
      }
      return true;
    } catch (Exception e) {
      log.error("Caught an exception while executing step: {}, accountId: {}", dataDeletionStep, accountId, e);
      return false;
    }
  }
}
