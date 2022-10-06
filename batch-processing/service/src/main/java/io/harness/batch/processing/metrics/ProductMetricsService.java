/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import java.time.Instant;

public interface ProductMetricsService {
  int countGcpBillingAccounts(String accountId);
  int countAwsBillingAccounts(String accountId);
  int countAwsCloudProviderInCd(String accountId);
  int countAwsCloudProviderInCe(String accountId);
  int countK8sClusterInCd(String accountId);
  int countK8sClusterInCe(String accountId);
  double getTotalClusterCost(String accountId, Instant start, Instant end);
  double getTotalUnallocatedCost(String accountId, Instant start, Instant end);
  double getOverallUnallocatedCostPercentage(String accountId, Instant start, Instant end);
  double getOverallIdleCostPercentage(String accountId, Instant start, Instant end);
  double getTotalIdleCost(String accountId, Instant start, Instant end);
  double getTotalK8sSpendInCe(String accountId, Instant start, Instant end);
  double getTotalEcsSpendInCe(String accountId, Instant start, Instant end);
  double countTotalK8sNamespaces(String accountId, Instant start, Instant end);
  double countTotalK8sWorkloads(String accountId, Instant start, Instant end);
  double countTotalK8sNodes(String accountId, Instant start, Instant end);
  double countTotalK8sPods(String accountId, Instant start, Instant end);
  double countTotalEcsClusters(String accountId, Instant start, Instant end);
  double countTotalEcsTasks(String accountId, Instant start, Instant end);
}
