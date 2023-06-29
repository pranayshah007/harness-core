/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.beans.recommendation.AzureVmUtilisationDTO;
import io.harness.ccm.commons.utils.BigQueryHelper;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class AzureCpuUtilisationService {
  private static final String QUERY_TEMPLATE_BIGQUERY = "SELECT AVG(average) AS average, MAX(maximum) AS maximum,"
      + " TIMESTAMP_TRUNC(metricStartTime, DAY) AS startTime,"
      + " TIMESTAMP_TRUNC(metricEndTime, DAY) AS endTime"
      + " FROM %s WHERE (TIMESTAMP_TRUNC(metricStartTime, DAY) >= '%s')"
      + " AND LOWER(vmId) = LOWER('%s') AND metricName = 'Percentage CPU'"
      + " GROUP BY startTime, endTime ORDER BY startTime";
  private static final String AVERAGE_QUERY_TEMPLATE_BIGQUERY = "SELECT avg(average) as average"
      + " FROM %s WHERE (TIMESTAMP_TRUNC(metricStartTime, DAY) >= '%s')"
      + " AND LOWER(vmId) = LOWER('%s') AND metricName = 'Percentage CPU'";
  private static final String MAXIMUM_QUERY_TEMPLATE_BIGQUERY = "SELECT max(maximum) as maximum"
      + " FROM %s WHERE (TIMESTAMP_TRUNC(metricStartTime, DAY) >= '%s')"
      + " AND LOWER(vmId) = LOWER('%s') AND metricName = 'Percentage CPU'";

  private static final String START_TIME = "startTime";
  private static final String END_TIME = "endTime";
  private static final String AVERAGE = "average";
  private static final String MAXIMUM = "maximum";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final String AZURE_VM_INVENTORY_METRIC = "azureVMInventoryMetric";

  @Inject private BigQueryService bigQueryService;
  @Inject private BigQueryHelper bigQueryHelper;

  public List<AzureVmUtilisationDTO> getAzureVmCpuUtilisationData(String vmId, String accountIdentifier, int duration) {
    long startTime = getStartOfLastDuration(duration);
    String cloudProviderTableName =
        bigQueryHelper.getCloudProviderTableName(accountIdentifier, AZURE_VM_INVENTORY_METRIC);
    String query = format(QUERY_TEMPLATE_BIGQUERY, cloudProviderTableName, Instant.ofEpochMilli(startTime), vmId);
    log.info("Query for Azure VM Cpu Utilisation: {}", query);
    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
      if (result.getTotalRows() == 0) {
        return null;
      }
      return extractAzureVmCpuUtilisationFromTable(vmId, result);
    } catch (InterruptedException e) {
      log.error("Failed to get Azure VM Cpu Utilisation data for Account:{}, {}", accountIdentifier, e);
      Thread.currentThread().interrupt();
    } catch (BigQueryException e) {
      log.error("Failed to get Azure VM Cpu Utilisation data for Account:{}, {}", accountIdentifier, e.getMessage());
    }
    return null;
  }

  public Double getAverageAzureVmCpuUtilisationData(String vmId, String accountIdentifier, int duration) {
    long startTime = getStartOfLastDuration(duration);
    String cloudProviderTableName =
        bigQueryHelper.getCloudProviderTableName(accountIdentifier, AZURE_VM_INVENTORY_METRIC);
    String query =
        format(AVERAGE_QUERY_TEMPLATE_BIGQUERY, cloudProviderTableName, Instant.ofEpochMilli(startTime), vmId);
    log.info("Query for Average Azure VM Cpu Utilisation: {}", query);
    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
      for (FieldValueList row : result.iterateAll()) {
        return row.get(AVERAGE).getValue() == null ? null : row.get(AVERAGE).getDoubleValue();
      }
    } catch (InterruptedException e) {
      log.error("Failed to get Azure VM Average Cpu Utilisation for Account:{}, {}", accountIdentifier, e);
    } catch (BigQueryException e) {
      log.error("Failed to get Azure VM Average Cpu Utilisation for Account:{}, {}", accountIdentifier, e.getMessage());
    }
    return null;
  }

  public Double getMaximumAzureVmCpuUtilisationData(String vmId, String accountIdentifier, int duration) {
    long startTime = getStartOfLastDuration(duration);
    String cloudProviderTableName =
        bigQueryHelper.getCloudProviderTableName(accountIdentifier, AZURE_VM_INVENTORY_METRIC);
    String query =
        format(MAXIMUM_QUERY_TEMPLATE_BIGQUERY, cloudProviderTableName, Instant.ofEpochMilli(startTime), vmId);
    log.info("Query for Maximum Azure VM Cpu Utilisation: {}", query);
    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
      for (FieldValueList row : result.iterateAll()) {
        return row.get(MAXIMUM).getValue() == null ? null : row.get(MAXIMUM).getDoubleValue();
      }
    } catch (InterruptedException e) {
      log.error("Failed to get Azure VM Maximum Cpu Utilisation for Account:{}, {}", accountIdentifier, e);
    } catch (BigQueryException e) {
      log.error("Failed to get Azure VM Maximum Cpu Utilisation for Account:{}, {}", accountIdentifier, e.getMessage());
    }
    return null;
  }

  private List<AzureVmUtilisationDTO> extractAzureVmCpuUtilisationFromTable(String vmId, TableResult result) {
    List<AzureVmUtilisationDTO> azureVmUtilisationResultSet = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      azureVmUtilisationResultSet.add(AzureVmUtilisationDTO.builder()
                                          .vmId(vmId)
                                          .averageCpu(row.get(AVERAGE).getDoubleValue())
                                          .startTime(row.get(START_TIME).getTimestampValue() / 1000l)
                                          .endTime(row.get(END_TIME).getTimestampValue() / 1000l)
                                          .maxCpu(row.get(MAXIMUM).getDoubleValue())
                                          .build());
    }
    return azureVmUtilisationResultSet;
  }

  private long getStartOfLastDuration(int duration) {
    ZoneId zoneId = ZoneId.of("GMT");
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return (zdtStart.toEpochSecond() * 1000) - duration * ONE_DAY_MILLIS;
  }
}