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
  private static final String CLOUD_PROVIDER = "cloudProvider";
  private static final String MAX_DAY = "max_day";
  private static final String MIN_DAY = "min_day";
  private static final Set<String> DUPLICATE_K8S_CLOUD_PROVIDERS = ImmutableSet.of("K8S_AWS", "K8S_AZURE", "K8S_GCP");

  public static List<ActiveSpendResultSetDTO> getActiveSpendResultSetDTOsWithMinAndMaxDay(TableResult result) {
    List<ActiveSpendResultSetDTO> activeSpendResultSet = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      activeSpendResultSet.add(ActiveSpendResultSetDTO.builder()
                                   .cloudProvider(row.get(CLOUD_PROVIDER).getStringValue())
                                   .month(row.get(MONTH).getStringValue())
                                   .cost(getNumericValue(row, COST))
                                   .maxDay(row.get(MAX_DAY).getTimestampValue())
                                   .minDay(row.get(MIN_DAY).getTimestampValue())
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
                                   .cloudProvider(row.get(CLOUD_PROVIDER).getStringValue())
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
        // K8S_AWS, K8S_AWS, K8S_AWS  => cloudProviderSlices[0] -> K8S | cloudProviderSlices[1] -> AWS/GCP/AZURE
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
