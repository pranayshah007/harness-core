/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.XIN;

import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.impl.DelegateTaskMetricContextBuilder;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.DEL)
public class DelegateMetricsServiceTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String PERPETUAL_TASK_TYPE = "perpetualTaskType";
  private static final String TEST_CUSTOM_METRIC_NAME = "test_custom_metric_name";

  @Mock private MetricService metricService;

  private DelegateMetricsService underTest;

  @Before
  public void setup() throws IllegalAccessException {
    underTest = new DelegateMetricsServiceImpl(metricService, new DelegateTaskMetricContextBuilder());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordDelegateTaskMetrics() {
    underTest.recordDelegateTaskMetrics(createDefaultDelegateTask(), TEST_CUSTOM_METRIC_NAME);

    verify(metricService).incCounter(TEST_CUSTOM_METRIC_NAME);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordDelegateTaskMetricsParams() {
    underTest.recordDelegateTaskMetrics("accountId", TEST_CUSTOM_METRIC_NAME);

    verify(metricService).incCounter(TEST_CUSTOM_METRIC_NAME);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordResponseMetrics() {
    underTest.recordDelegateTaskResponseMetrics(
        createDefaultDelegateTask(), createDefaultDelegateTaskResponse(), TEST_CUSTOM_METRIC_NAME);

    verify(metricService).incCounter(TEST_CUSTOM_METRIC_NAME);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testRecordPerpetualTaskMetrics() {
    underTest.recordPerpetualTaskMetrics(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TEST_CUSTOM_METRIC_NAME);
    verify(metricService).incCounter(TEST_CUSTOM_METRIC_NAME);
  }

  private static DelegateTask createDefaultDelegateTask() {
    return DelegateTask.builder()
        .accountId(ACCOUNT_ID)
        .status(DelegateTask.Status.QUEUED)
        .expiry(System.currentTimeMillis() - 10)
        .data(TaskData.builder().taskType(TaskType.COMMAND.name()).timeout(1).build())
        .build();
  }

  private static DelegateTaskResponse createDefaultDelegateTaskResponse() {
    return DelegateTaskResponse.builder()
        .accountId("ACCOUNT_ID")
        .responseCode(DelegateTaskResponse.ResponseCode.OK)
        .response(DelegateStringResponseData.builder().data("test data").build())
        .build();
  }
}
