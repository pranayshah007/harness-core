/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.intfc;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateTaskResponse;

public interface DelegateMetricsService {
  void recordDelegateTaskMetrics(DelegateTask task, String metricName);

  void recordDelegateTaskMetrics(String accountId, String metricName);

  void recordDelegateTaskResponseMetrics(DelegateTask delegateTask, DelegateTaskResponse response, String metricName);

  void recordDelegateMetrics(Delegate delegate, String metricName);

  void recordPerpetualTaskMetrics(String accountId, String perpetualTaskType, String metricName);

  void recordDelegateMetricsPerAccount(String accountId, String metricName);

  void recordDelegateHeartBeatMetricsPerAccount(String accountId, String accountName, String companyName,
      DelegateRing delegateRing, String orgId, String projectId, String delegateName, String delegateId,
      String delegateVersion, String delegateConnectionStatus, String delegateEventType, boolean isNg,
      boolean isImmutable, long lastHB, String metricName);
}
