/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;

import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPBigQueryInternalTablesCleanup {
  private static final String DATASET_TABLE_FORMAT = "%s.%s.%s";
  private static final String CE_INTERNAL_DATASET = "CE_INTERNAL";
  private static final String HARNESS_DATASET = "harness";
  private static final String USER_AGENT_HEADER = "user-agent";
  private static final String USER_AGENT_HEADER_ENVIRONMENT_VARIABLE = "USER_AGENT_HEADER";
  private static final String DEFAULT_USER_AGENT = "default-user-agent";
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";
  private static final String MSP_MARKUP = "mspMarkup";
  private static final String AWS_TRUTH_TABLE = "awstruthtable";

  private static final String COUNT_QUERY = "SELECT count(*) as recordscount FROM %s WHERE accountId = \"%s\"";
  private static final String DELETE_QUERY = "DELETE FROM %s WHERE accountId = \"%s\"";
  private static final String COUNT_QUERY_MSP =
      "SELECT count(*) as recordscount FROM %s WHERE accountId = \"%s\" OR mspAccountId = \"%s\"";
  private static final String DELETE_QUERY_MSP = "DELETE FROM %s WHERE accountId = \"%s\" OR mspAccountId = \"%s\"";

  @Autowired BatchMainConfig configuration;

  public boolean delete(String accountId, String tableName, DataDeletionStep step,
      DataDeletionRecord dataDeletionRecord, boolean dryRun) throws InterruptedException {
    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    BigQuery bigQuery = BigQueryOptions.newBuilder()
                            .setCredentials(credentials)
                            .setHeaderProvider(getHeaderProvider())
                            .build()
                            .getService();
    String countQuery = getCountQuery(accountId, tableName);
    String deleteQuery = getDeleteQuery(accountId, tableName);
    QueryJobConfiguration countQueryConfig = QueryJobConfiguration.newBuilder(countQuery).build();
    QueryJobConfiguration deleteQueryConfig = QueryJobConfiguration.newBuilder(deleteQuery).build();
    try {
      Job job = bigQuery.create(JobInfo.newBuilder(countQueryConfig).build());
      log.info("Table name: {}, Job id: {} deletion", tableName, job.getJobId());
      TableResult result = job.getQueryResults();
      dataDeletionRecord.getRecords()
          .get(step.name())
          .setRecordsCount(result.iterateAll().iterator().next().get(0).getLongValue());
      if (!dryRun) {
        job = bigQuery.create(JobInfo.newBuilder(deleteQueryConfig).build());
        log.info("Table name: {}, Job id: {} deletion", tableName, job.getJobId());
        job.getQueryResults();
      }
    } catch (BigQueryException | InterruptedException e) {
      log.error("Error in processing query in table: {}", tableName, e);
      throw e;
    }
    return true;
  }

  private String getCountQuery(String accountId, String tableName) {
    String projectId = configuration.getGcpConfig().getGcpProjectId();
    String datasetTableName;
    if (tableName.equals(AWS_TRUTH_TABLE)) {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, HARNESS_DATASET, tableName);
      return String.format(COUNT_QUERY, datasetTableName, accountId);
    } else if (tableName.equals(MSP_MARKUP)) {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, CE_INTERNAL_DATASET, tableName);
      return String.format(COUNT_QUERY_MSP, datasetTableName, accountId, accountId);
    } else {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, CE_INTERNAL_DATASET, tableName);
      return String.format(COUNT_QUERY, datasetTableName, accountId);
    }
  }

  private String getDeleteQuery(String accountId, String tableName) {
    String projectId = configuration.getGcpConfig().getGcpProjectId();
    String datasetTableName;
    if (tableName.equals(AWS_TRUTH_TABLE)) {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, HARNESS_DATASET, tableName);
      return String.format(DELETE_QUERY, datasetTableName, accountId);
    } else if (tableName.equals(MSP_MARKUP)) {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, CE_INTERNAL_DATASET, tableName);
      return String.format(DELETE_QUERY_MSP, datasetTableName, accountId, accountId);
    } else {
      datasetTableName = format(DATASET_TABLE_FORMAT, projectId, CE_INTERNAL_DATASET, tableName);
      return String.format(DELETE_QUERY, datasetTableName, accountId);
    }
  }

  private HeaderProvider getHeaderProvider() {
    String userAgent = System.getenv(USER_AGENT_HEADER_ENVIRONMENT_VARIABLE);
    return FixedHeaderProvider.create(
        Map.of(USER_AGENT_HEADER, Objects.nonNull(userAgent) ? userAgent : DEFAULT_USER_AGENT));
  }
}
