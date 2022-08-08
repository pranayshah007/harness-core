/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.util.Set;

public class CCMLicenseUsageHelper {
  public static final String ACCOUNT_ID = "accountId";
  public static final String COST = "cost";
  public static final String MONTH = "month";
  public static final String CLOUD_PROVIDER = "cloudProvider";
  private static final Set<String> duplicateK8sCloudProviders = ImmutableSet.of("K8S_AWS", "K8S_AZURE", "K8S_GCP");

  public static Long computeDeduplicatedActiveSpend(TableResult result) {
    Long cost = 0L;
    Multimap<String, String> multiMap = ArrayListMultimap.create();

    // Will maintain a multi map with Month - [Cloud Provider] mapping
    for (FieldValueList row : result.iterateAll()) {
      multiMap.put(row.get(MONTH).getStringValue(), row.get(CLOUD_PROVIDER).getStringValue());
    }

    for (FieldValueList row : result.iterateAll()) {
      String cloudProvider = row.get(CLOUD_PROVIDER).getStringValue();
      if (duplicateK8sCloudProviders.contains(cloudProvider)) {
        // K8S_AWS, K8S_AWS, K8S_AWS  => cloudProviderSlices[0] -> K8S | cloudProviderSlices[1] -> AWS/GCP/AZURE
        String cloudProviderSlices[] = cloudProvider.split("_");
        String month = row.get(MONTH).getStringValue();

        // If Corresponding Cloud Provider Data is present -> We should not add the K8s Cost
        if (!multiMap.containsEntry(month, cloudProviderSlices[1])) {
          cost += getNumericValue(row, COST);
        }
      } else {
        cost += getNumericValue(row, COST);
      }
    }
    return Math.round(cost * 100L) / 100L;
  }

  private static long getNumericValue(FieldValueList row, String fieldName) {
    FieldValue value = row.get(fieldName);
    return Math.round(value.getNumericValue().longValue() * 100L) / 100L;
  }
}
