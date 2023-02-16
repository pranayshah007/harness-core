/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.managerclient.VerificationServiceClient;
import io.harness.rest.RestResponse;

import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.delegatetasks.MetricDataStoreService;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class MetricDataStoreServiceImpl implements MetricDataStoreService {
  @Inject private VerificationServiceClient verificationClient;
  @Inject private TimeLimiter timeLimiter;

  @Override
  public boolean saveNewRelicMetrics(String accountId, String applicationId, String stateExecutionId,
      String delegateTaskId, List<NewRelicMetricDataRecord> metricData) throws Exception {
    if (metricData.isEmpty()) {
      return true;
    }

    RestResponse<Boolean> restResponse = HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
        ()
            -> execute(verificationClient.saveTimeSeriesMetrics(
                accountId, applicationId, stateExecutionId, delegateTaskId, metricData)));
    return restResponse.getResource();
  }
}
