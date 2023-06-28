/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.beans.ExternalApiMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ExternalApiMetricsServiceImpl {
  public static final String EXTERNAL_API_REQUEST_COUNT = "external_api_request_count";

  @Inject private MetricService metricService;

  public void recordApiRequestMetric(String accountId, String requestPath, String metricName) {
    if (isEmpty(accountId)) {
      return;
    }

    try (ExternalApiMetricContext ignore = new ExternalApiMetricContext(accountId, requestPath)) {
      metricService.incCounter(metricName);
    }
  }
}
