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
import io.harness.ccm.commons.utils.BigQueryHelper;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class AzureCpuUtilisationService {
  private static final String AVERAGE_QUERY_TEMPLATE_BIGQUERY = "SELECT avg(average) as average"
      + " FROM %s WHERE (TIMESTAMP_TRUNC(metricStartTime, DAY) >= '%s') AND"
      + " vmId = '%s' AND metricName = 'Percentage CPU'";

  private static final String AVERAGE = "average";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final String AZURE_VM_INVENTORY_METRIC = "azureVMInventoryMetric";

  @Inject private BigQueryService bigQueryService;
  @Inject private BigQueryHelper bigQueryHelper;

  public Double getAverageAzureVmCpuUtilisationData(String vmId, String accountIdentifier) {
    long startTime = getStartOfLastMonth();
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
      log.error("Failed to get Azure VM Cpu Utilisation for Account:{}, {}", accountIdentifier, e);
    }
    return null;
  }

  private long getStartOfLastMonth() {
    ZoneId zoneId = ZoneId.of("GMT");
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return (zdtStart.toEpochSecond() * 1000) - 30 * ONE_DAY_MILLIS;
  }
}
