/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.CE_GCP_CREDENTIALS_PATH;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.GCP_BQ_TRANSFER_JOBS;
import static io.harness.ccm.commons.utils.BigQueryHelper.modifyStringToComplyRegex;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceSettings;
import com.google.cloud.bigquery.datatransfer.v1.DeleteTransferConfigRequest;
import com.google.cloud.bigquery.datatransfer.v1.ListTransferConfigsRequest;
import com.google.cloud.bigquery.datatransfer.v1.ProjectName;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPBigQueryTransferJobsCleanup {
  private static final String TRANSFER_JOB_PREFIX = "ccm_gcp_dataset";

  @Autowired BatchMainConfig configuration;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun) throws Exception {
    ServiceAccountCredentials serviceAccountCredentials = getCredentials(CE_GCP_CREDENTIALS_PATH);
    if (serviceAccountCredentials == null) {
      throw new Exception("Couldn't get credentials from CE_GCP_CREDENTIALS_PATH");
    }
    FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(serviceAccountCredentials);
    DataTransferServiceSettings settings =
        DataTransferServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
    try (DataTransferServiceClient dataTransferServiceClient = DataTransferServiceClient.create(settings)) {
      List<TransferConfig> transferConfigs = new ArrayList<>();
      ProjectName parent = ProjectName.of(configuration.getBillingDataPipelineConfig().getGcpProjectId());
      ListTransferConfigsRequest request = ListTransferConfigsRequest.newBuilder().setParent(parent.toString()).build();
      dataTransferServiceClient.listTransferConfigs(request).iterateAll().forEach(transferConfig -> {
        if (transferConfig.getDisplayName().startsWith(TRANSFER_JOB_PREFIX)
            && transferConfig.getDisplayName().contains(modifyStringToComplyRegex(accountId))) {
          transferConfigs.add(transferConfig);
        }
      });
      dataDeletionRecord.getRecords().get(GCP_BQ_TRANSFER_JOBS.name()).setRecordsCount((long) transferConfigs.size());
      if (!dryRun) {
        for (TransferConfig transferConfig : transferConfigs) {
          DeleteTransferConfigRequest deleteRequest =
              DeleteTransferConfigRequest.newBuilder().setName(transferConfig.getName()).build();
          dataTransferServiceClient.deleteTransferConfig(deleteRequest);
          log.info("Transfer config deleted successfully: {}", transferConfig.getDisplayName());
        }
      }
    } catch (ApiException ex) {
      log.error("Error in GCP Transfer Config listing/deletion", ex);
      throw ex;
    }
    return true;
  }
}
