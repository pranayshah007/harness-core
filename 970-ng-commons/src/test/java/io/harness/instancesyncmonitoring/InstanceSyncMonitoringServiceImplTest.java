/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instancesyncmonitoring;

import static io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl.DURATION_METRIC_PATTERN;
import static io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl.NEW_DEPLOYMENT_METRIC_NAME;
import static io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl.NG_SUFFIX_PATTERN;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncMonitoringServiceImplTest {
  AutoCloseable openMocks;
  private static final String ACCOUNT_ID = "TEST_ACCOUNT_ID";
  @Mock private MetricService metricService;
  private io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl instanceSyncMonitoringServiceImpl;

  @Before
  public void setup() {
    openMocks = MockitoAnnotations.openMocks(this);
    instanceSyncMonitoringServiceImpl = new InstanceSyncMonitoringServiceImpl(metricService);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testRecordMetrics() {
    long duration = 1000;

    // NG
    instanceSyncMonitoringServiceImpl.recordMetrics(ACCOUNT_ID, true, true, duration);
    verify(metricService).incCounter(String.format(NG_SUFFIX_PATTERN, NEW_DEPLOYMENT_METRIC_NAME));
    verify(metricService)
        .recordDuration(
            String.format(NG_SUFFIX_PATTERN, String.format(DURATION_METRIC_PATTERN, NEW_DEPLOYMENT_METRIC_NAME)),
            Duration.ofMillis(duration));

    // CG
    instanceSyncMonitoringServiceImpl.recordMetrics(ACCOUNT_ID, false, true, duration);
    verify(metricService).incCounter(String.format(NG_SUFFIX_PATTERN, NEW_DEPLOYMENT_METRIC_NAME));
    verify(metricService)
        .recordDuration(
            String.format(DURATION_METRIC_PATTERN, NEW_DEPLOYMENT_METRIC_NAME), Duration.ofMillis(duration));
  }
}
