/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.metrics.beans.AccountActivityMetricsContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AccountActivityMetricsServiceImpl {
  public static final String ACCOUNT_ACTIVITY_METRICS = "account_activity_metrics";

  @Inject private MetricService metricService;

  private void recordAccountActivityMetric(
      String accountIdentifier, int activeProjects, int uniqueLogins, String metricName) {
    try (AccountActivityMetricsContext ignore =
             new AccountActivityMetricsContext(accountIdentifier, activeProjects, uniqueLogins)) {
      metricService.incCounter(metricName);
    }
  }
  public void recordAccountActivityMetric(String accountIdentifier, int activeProjects, int uniqueLogins) {
    recordAccountActivityMetric(accountIdentifier, activeProjects, uniqueLogins, ACCOUNT_ACTIVITY_METRICS);
  }
}