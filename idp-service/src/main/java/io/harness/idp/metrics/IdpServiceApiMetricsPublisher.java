/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceApiMetricsPublisher {
  public static final String IDP_SERVICE_API_4XX = "idp_service_api_4xx";
  public static final String IDP_SERVICE_API_5XX = "idp_service_api_5xx";
  public static final String IDP_SERVICE_API_ALL = "idp_service_api_all";
  public static final String IDP_SERVICE_API_DURATION = "idp_service_api_duration";
  @Inject MetricService metricService;
  public void recordMetric(String accountIdentifier, String path, int status, long duration) {
    try (IDPMetricContext ignore = new IDPMetricContext(accountIdentifier, path)) {
      metricService.incCounter(IDP_SERVICE_API_ALL);
      metricService.recordDuration(IDP_SERVICE_API_DURATION, Duration.ofMillis(duration));
      if (status >= 400 && status < 500) {
        metricService.incCounter(IDP_SERVICE_API_4XX);
      } else if (status >= 500) {
        metricService.incCounter(IDP_SERVICE_API_5XX);
      }
    }
  }
}
