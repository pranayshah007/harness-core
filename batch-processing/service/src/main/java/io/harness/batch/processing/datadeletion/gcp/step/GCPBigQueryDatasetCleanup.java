/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.DATA_SET_NAME_TEMPLATE;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.GCP_BQ_DATASET;

import static com.google.cloud.bigquery.BigQuery.DatasetDeleteOption.deleteContents;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.BillingDataPipelineUtils;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;

import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPBigQueryDatasetCleanup {
  private static final String USER_AGENT_HEADER = "user-agent";
  private static final String USER_AGENT_HEADER_ENVIRONMENT_VARIABLE = "USER_AGENT_HEADER";
  private static final String DEFAULT_USER_AGENT = "default-user-agent";
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";

  @Autowired BatchMainConfig configuration;
  @Autowired BillingDataPipelineRecordDao billingDataPipelineRecordDao;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun) {
    BillingDataPipelineRecord billingDataPipelineRecord = billingDataPipelineRecordDao.getByAccountId(accountId);
    String dataSetName;
    if (billingDataPipelineRecord == null) {
      dataSetName =
          String.format(DATA_SET_NAME_TEMPLATE, BillingDataPipelineUtils.modifyStringToComplyRegex(accountId));
    } else {
      dataSetName = billingDataPipelineRecord.getDataSetId();
    }
    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    BigQuery bigquery = BigQueryOptions.newBuilder()
                            .setCredentials(credentials)
                            .setHeaderProvider(getHeaderProvider())
                            .build()
                            .getService();
    DatasetId datasetId = DatasetId.of(configuration.getBillingDataPipelineConfig().getGcpProjectId(), dataSetName);
    dataDeletionRecord.getRecords().get(GCP_BQ_DATASET.name()).setRecordsCount(1L);
    return dryRun || bigquery.delete(datasetId, deleteContents());
  }

  private HeaderProvider getHeaderProvider() {
    String userAgent = System.getenv(USER_AGENT_HEADER_ENVIRONMENT_VARIABLE);
    return FixedHeaderProvider.create(
        Map.of(USER_AGENT_HEADER, Objects.nonNull(userAgent) ? userAgent : DEFAULT_USER_AGENT));
  }
}
