/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.billing.dao.CloudBillingTransferRunDao;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.billing.CloudBillingTransferRun;
import io.harness.ccm.commons.entities.billing.TransferJobRunState;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpOrganization;
import io.harness.ccm.config.GcpOrganizationDao;
import io.harness.ccm.config.GcpOrganizationService;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.cloud.bigquery.datatransfer.v1.TransferRun;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class GcpBillingDataPipelineTasklet implements Tasklet {
  @Autowired private BatchMainConfig mainConfig;
  @Autowired protected CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private GcpOrganizationDao gcpOrganizationDao;
  @Autowired private GcpOrganizationService gcpOrganizationService;
  @Autowired private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired private CloudBillingTransferRunDao cloudBillingTransferRunDao;
  @Autowired private BillingDataPipelineService billingDataPipelineService;

  private static final String COPY_TRANSFER_JOB_NAME_TEMPLATE = "BigQueryCopyTransferJob_%s_%s";
  private static final String GCP_PRE_AGG_QUERY_TEMPLATE = "gcpPreAggQuery_%s_%s";
  private static final String GCP_COPY_SCHEDULED_QUERY_TEMPLATE = "gcpCopyScheduledQuery_%s_%s";
  private static final String RUN_ONCE_GCP_COPY_SCHEDULED_QUERY_TEMPLATE = "runOnceGcpCopyScheduledQuery_%s_%s";
  private static final String US = "us";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();

    if (!mainConfig.getBillingDataPipelineConfig().isGcpSyncEnabled()) {
      Account account = cloudToHarnessMappingService.getAccountInfoFromId(accountId);
      String accountName = account.getAccountName();
      String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
      log.info("Executing GcpBillingDataPipelineTasklet");
      List<GcpBillingAccount> gcpBillingAccounts =
          cloudToHarnessMappingService.listGcpBillingAccountUpdatedInDuration(accountId);
      log.info("Found gcpBillingAccounts {}", gcpBillingAccounts);
      gcpBillingAccounts.stream()
          .filter(gcpBillingAccount -> {
            List<BillingDataPipelineRecord> billingDataPipelineRecords =
                billingDataPipelineRecordDao.listByGcpBillingAccountDataset(gcpBillingAccount.getAccountId(),
                    gcpBillingAccount.getBqProjectId(), gcpBillingAccount.getBqDatasetId());
            return isEmpty(billingDataPipelineRecords);
          })
          .forEach(gcpBillingAccount -> {
            String dstDataSetId = billingDataPipelineService.createDataSet(account);
            String gcpBqProjectId = gcpBillingAccount.getBqProjectId();
            String gcpBqDatasetId = gcpBillingAccount.getBqDatasetId();
            String gcpBqDataSetRegion = gcpBillingAccount.getBqDataSetRegion();

            GcpOrganization gcpOrganization = gcpOrganizationDao.get(gcpBillingAccount.getOrganizationSettingId());
            String dstProjectId = gcpProjectId;

            String transferJobResourceName = null;
            String transferJobDisplayName = null;
            String runOnceScheduledQueryName;
            log.info("Found region {}", Strings.toLowerCase(gcpBqDataSetRegion));
            switch (Strings.toLowerCase(gcpBqDataSetRegion)) {
              case US:
                transferJobDisplayName =
                    String.format(GCP_COPY_SCHEDULED_QUERY_TEMPLATE, gcpBqProjectId, gcpBqDatasetId);
                log.info("Creating scheduled query with name {}", transferJobDisplayName);
                try {
                  billingDataPipelineService.createTransferScheduledQueriesForGCP(transferJobDisplayName, dstDataSetId,
                      gcpOrganization.getServiceAccountEmail(), gcpBqProjectId + "." + gcpBqDatasetId);
                  log.info("Created scheduled query with name {}", transferJobDisplayName);
                } catch (IOException e) {
                  log.error("Error while creating BQ -> BQ Transfer Job {}", transferJobDisplayName, e);
                }
                try {
                  runOnceScheduledQueryName =
                      String.format(RUN_ONCE_GCP_COPY_SCHEDULED_QUERY_TEMPLATE, gcpBqProjectId, gcpBqDatasetId);
                  log.info("Creating scheduled query with name {}", runOnceScheduledQueryName);
                  transferJobResourceName =
                      billingDataPipelineService.createRunOnceScheduledQueryGCP(runOnceScheduledQueryName,
                          gcpBqProjectId, gcpBqDatasetId, dstDataSetId, gcpOrganization.getServiceAccountEmail());
                  log.info("Created scheduled query with name {}", runOnceScheduledQueryName);
                } catch (IOException e) {
                  log.error("Error while creating BQ -> BQ Run Once Scheduled Query Job {}", transferJobDisplayName, e);
                }
                break;
              default:
                try {
                  transferJobDisplayName =
                      String.format(COPY_TRANSFER_JOB_NAME_TEMPLATE, gcpBqProjectId, gcpBqDatasetId);
                  log.info("Creating BQ Transfer job with name {}", transferJobDisplayName);
                  transferJobResourceName =
                      billingDataPipelineService.createDataTransferJobFromBQ(transferJobDisplayName, gcpBqProjectId,
                          gcpBqDatasetId, dstProjectId, dstDataSetId, gcpOrganization.getServiceAccountEmail());
                  log.info("Created BQ Transfer job with name {}", transferJobDisplayName);
                } catch (IOException e) {
                  log.error("Error while creating BQ -> BQ Transfer Job {}", transferJobDisplayName, e);
                }
            }

            try {
              billingDataPipelineService.triggerTransferJobRun(
                  transferJobResourceName, gcpOrganization.getServiceAccountEmail());
              log.info("Triggered BQ Transfer job with name {}", transferJobResourceName);
            } catch (IOException e) {
              log.error("Error while starting manual run for BQ -> BQ Transfer Job {}", transferJobDisplayName, e);
            }

            String scheduledQueryResourceName = null;
            String scheduledQueryDisplayName =
                String.format(GCP_PRE_AGG_QUERY_TEMPLATE, gcpBqProjectId, gcpBqDatasetId);
            try {
              scheduledQueryResourceName =
                  billingDataPipelineService.createScheduledQueriesForGCP(scheduledQueryDisplayName, dstDataSetId);
              log.info("Created preaggregated scheduled query for GCP");
            } catch (IOException e) {
              log.error("Error while creating Scheduled Queries {}", scheduledQueryDisplayName, e);
            }

            String billingDataPipelineRecordId = billingDataPipelineRecordDao.create(
                BillingDataPipelineRecord.builder()
                    .accountId(accountId)
                    .accountName(accountName)
                    .settingId(gcpBillingAccount.getOrganizationSettingId())
                    .cloudProvider(CloudProvider.GCP.name())
                    .dataSetId(dstDataSetId)
                    .dataTransferJobName(transferJobDisplayName)
                    .transferJobResourceName(transferJobResourceName)
                    .gcpBqProjectId(gcpBqProjectId)
                    .gcpBqDatasetId(gcpBqDatasetId)
                    .preAggregatedScheduledQueryName(scheduledQueryDisplayName)
                    .preAggregatedScheduleQueryResourceName(scheduledQueryResourceName)
                    .build());
            try {
              List<TransferRun> transferRunList = billingDataPipelineService.listTransferRuns(
                  transferJobResourceName, gcpOrganization.getServiceAccountEmail());
              transferRunList.forEach(transferRun -> {
                cloudBillingTransferRunDao.upsert(CloudBillingTransferRun.builder()
                                                      .accountId(accountId)
                                                      .organizationUuid(gcpBillingAccount.getOrganizationSettingId())
                                                      .billingDataPipelineRecordId(billingDataPipelineRecordId)
                                                      .transferRunResourceName(transferRun.getName())
                                                      .state(TransferJobRunState.PENDING)
                                                      .build());
              });
            } catch (IOException e) {
              log.error("Error while getting manual runs for BQ -> BQ Transfer Job {}", transferJobDisplayName, e);
            }
          });
    }
    return null;
  }
}
