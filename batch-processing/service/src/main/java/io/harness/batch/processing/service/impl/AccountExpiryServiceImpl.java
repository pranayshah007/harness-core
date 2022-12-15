/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;

import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.intfc.AccountExpiryService;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;

import software.wings.beans.Account;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountExpiryServiceImpl implements AccountExpiryService {
  private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";

  @Autowired
  public AccountExpiryServiceImpl(BillingDataPipelineRecordDao billingDataPipelineRecordDao) {
    this.billingDataPipelineRecordDao = billingDataPipelineRecordDao;
  }

  @Override
  public boolean dataPipelineCleanup(Account account) {
    String accountId = account.getUuid();
    List<BillingDataPipelineRecord> billingDataPipelineRecordList =
        billingDataPipelineRecordDao.getAllRecordsByAccountId(accountId);

    billingDataPipelineRecordList.forEach(
        billingDataPipelineRecord -> deletePipelinePerRecord(accountId, billingDataPipelineRecord));
    return true;
  }

  public void deletePipelinePerRecord(String accountId, BillingDataPipelineRecord billingDataPipelineRecord) {
    String settingId = billingDataPipelineRecord.getSettingId();
    String cloudProvider = billingDataPipelineRecord.getCloudProvider();
    List<String> listOfPipelineJobs = new ArrayList<>();
    listOfPipelineJobs.add(billingDataPipelineRecord.getDataTransferJobName());
    listOfPipelineJobs.add(billingDataPipelineRecord.getPreAggregatedScheduledQueryName());

    if (cloudProvider.equals(CloudProvider.AWS.name())) {
      listOfPipelineJobs.add(billingDataPipelineRecord.getAwsFallbackTableScheduledQueryName());
    }

    // Delete associated Data Pipeline Jobs
    try {
      DataTransferServiceClient dataTransferClient = getDataTransferClient();
      for (String job : listOfPipelineJobs) {
        deleteDataTransfer(dataTransferClient, job);
      }
    } catch (IOException e) {
      log.error("Error Deleting Data Pipeline Jobs: {}", e);
    }

    // Delete the Data (DataSet, Tables)
    BigQuery bigquery = getBigQueryClient();
    bigquery.delete(billingDataPipelineRecord.getDataSetId(), BigQuery.DatasetDeleteOption.deleteContents());

    // Delete Data Pipeline Record
    billingDataPipelineRecordDao.removeBillingDataPipelineRecord(accountId, settingId);
  }

  protected void deleteDataTransfer(DataTransferServiceClient dataTransferClient, String job) {
    dataTransferClient.deleteTransferConfig(job);
  }

  protected BigQuery getBigQueryClient() {
    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    return BigQueryOptions.newBuilder().setCredentials(credentials).build().getService();
  }

  protected DataTransferServiceClient getDataTransferClient() throws IOException {
    Credentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    return getDataTransferClient(credentials);
  }

  protected DataTransferServiceClient getDataTransferClient(Credentials credentials) throws IOException {
    DataTransferServiceSettings dataTransferServiceSettings =
        DataTransferServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();
    return DataTransferServiceClient.create(dataTransferServiceSettings);
  }
}
