/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.ActiveSpendResultSetDTO;

import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

@OwnedBy(CE)
public class CCMLicenseUsageHelper {
  private static final String COST = "cost";
  private static final String MONTH = "month";
  private static final String CLUSTER = "CLUSTER";
  private static final String CLOUD_PROVIDER = "cloudProvider";
  private static final String CLUSTER_TYPE = "clustertype";
  private static final String CLUSTER_CLOUD_PROVIDER = "clustercloudprovider";
  private static final String MAX_START_TIME = "startTime_MAX";
  private static final String MIN_START_TIME = "startTime_MIN";
  private static final String MAX_DAY = "max_day";
  private static final String MIN_DAY = "min_day";
  private static final Set<String> DUPLICATE_K8S_CLOUD_PROVIDERS = ImmutableSet.of("K8S_AWS", "K8S_AZURE", "K8S_GCP");

  public static final String QUERY_TEMPLATE_BIGQUERY =
      "SELECT cloudProvider, clustertype, clustercloudprovider, SUM(cost) AS cost, "
      + "TIMESTAMP_TRUNC(TIMESTAMP_TRUNC(startTime, DAY), MONTH) AS month FROM %s "
      + "WHERE (((clustertype IS NULL) OR (clustertype = 'K8S')) AND ((instancetype IS NULL) OR "
      + "(instancetype IN ('K8S_NODE','K8S_PV','K8S_POD_FARGATE','ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE') )) AND "
      + "(TIMESTAMP_TRUNC(startTime, DAY) >= '%s') AND (TIMESTAMP_TRUNC(startTime, DAY) <= '%s')) "
      + "group by month, cloudProvider, clustertype, clustercloudprovider";
  public static final String QUERY_TEMPLATE_CLICKHOUSE =
      "SELECT SUM(cost) AS cost, date_trunc('month',day) AS month, cloudProvider FROM %s "
      + "WHERE day >= toDateTime(%s) AND day <= toDateTime(%s) GROUP BY month, cloudProvider";

  public static List<ActiveSpendResultSetDTO> getActiveSpendResultSetDTOsWithMinAndMaxDay(TableResult result) {
    List<ActiveSpendResultSetDTO> activeSpendResultSet = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      activeSpendResultSet.add(ActiveSpendResultSetDTO.builder()
                                   .cloudProvider(getCloudProvider(row))
                                   .month(row.get(MONTH).getStringValue())
                                   .cost(getNumericValue(row, COST))
                                   .maxDay(row.get(MAX_START_TIME).getTimestampValue())
                                   .minDay(row.get(MIN_START_TIME).getTimestampValue())
                                   .build());
    }
    return activeSpendResultSet;
  }

  public static List<ActiveSpendResultSetDTO> getActiveSpendResultSetDTOsWithMinAndMaxDay(ResultSet result)
      throws SQLException {
    List<ActiveSpendResultSetDTO> activeSpendResultSet = new ArrayList<>();
    while (result != null && result.next()) {
      activeSpendResultSet.add(ActiveSpendResultSetDTO.builder()
                                   .cloudProvider(fetchStringValue(result, CLOUD_PROVIDER))
                                   .month(fetchStringValue(result, MONTH))
                                   .cost(fetchNumericValue(result, COST))
                                   .maxDay(fetchTimestampValue(result, MAX_DAY))
                                   .minDay(fetchTimestampValue(result, MIN_DAY))
                                   .build());
    }
    return activeSpendResultSet;
  }

  public static List<ActiveSpendResultSetDTO> getActiveSpendResultSetDTOs(TableResult result) {
    List<ActiveSpendResultSetDTO> activeSpendResultSet = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      activeSpendResultSet.add(ActiveSpendResultSetDTO.builder()
                                   .cloudProvider(getCloudProvider(row))
                                   .month(row.get(MONTH).getStringValue())
                                   .cost(getNumericValue(row, COST))
                                   .build());
    }
    return activeSpendResultSet;
  }

  public static List<ActiveSpendResultSetDTO> getActiveSpendResultSetDTOs(ResultSet result) throws SQLException {
    List<ActiveSpendResultSetDTO> activeSpendResultSet = new ArrayList<>();
    while (result != null && result.next()) {
      activeSpendResultSet.add(ActiveSpendResultSetDTO.builder()
                                   .cloudProvider(fetchStringValue(result, CLOUD_PROVIDER))
                                   .month(fetchStringValue(result, MONTH))
                                   .cost(fetchNumericValue(result, COST))
                                   .build());
    }
    return activeSpendResultSet;
  }

  private static String getCloudProvider(FieldValueList row) {
    String cloudProvider = row.get(CLOUD_PROVIDER).getStringValue();
    if (CLUSTER.equals(cloudProvider)) {
      String clusterType = row.get(CLUSTER_TYPE).getStringValue();
      String clusterCloudProvider = row.get(CLUSTER_CLOUD_PROVIDER).getStringValue();
      cloudProvider = String.format("%s_%s", clusterType, clusterCloudProvider);
    }
    return cloudProvider;
  }

  public static Long computeDeduplicatedActiveSpend(List<ActiveSpendResultSetDTO> activeSpendResultSet) {
    long cost = 0L;
    Multimap<String, String> multiMap = ArrayListMultimap.create();

    // Will maintain a multi map with Month - [Cloud Provider] mapping
    for (ActiveSpendResultSetDTO activeSpendResultSetDTO : activeSpendResultSet) {
      multiMap.put(activeSpendResultSetDTO.getMonth(), activeSpendResultSetDTO.getCloudProvider());
    }

    for (ActiveSpendResultSetDTO activeSpendResultSetDTO : activeSpendResultSet) {
      String cloudProvider = activeSpendResultSetDTO.getCloudProvider();
      if (DUPLICATE_K8S_CLOUD_PROVIDERS.contains(cloudProvider)) {
        // K8S_AWS, K8S_GCP, K8S_AZURE  => cloudProviderSlices[0] -> K8S | cloudProviderSlices[1] -> AWS/GCP/AZURE
        String[] cloudProviderSlices = cloudProvider.split("_");
        String month = activeSpendResultSetDTO.getMonth();

        // If Corresponding Cloud Provider Data is present -> We should not add the K8s Cost
        if (!multiMap.containsEntry(month, cloudProviderSlices[1])) {
          cost += activeSpendResultSetDTO.getCost();
        }
      } else {
        cost += activeSpendResultSetDTO.getCost();
      }
    }
    return Math.round(cost * 100L) / 100L;
  }

  private static long getNumericValue(FieldValueList row, String fieldName) {
    FieldValue value = row.get(fieldName);
    return Math.round(value.getNumericValue().doubleValue() * 100L) / 100L;
  }

  private static long fetchNumericValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getLong(field);
  }

  private static String fetchStringValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getString(field);
  }

  private static long fetchTimestampValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime();
  }
}
