/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.XIN;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.metrics.impl.DelegateTaskMetricContextBuilder;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;
import software.wings.service.intfc.AssignDelegateService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateMetricsServiceTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String PERPETUAL_TASK_TYPE = "perpetualTaskType";
  private static final String TEST_CUSTOM_METRIC_NAME = "test_custom_metric_name";

  @Inject private DelegateTaskMetricContextBuilder metricContextBuilder;
  @Inject private HPersistence persistence;

  @InjectMocks @Inject private DelegateMetricsService delegateMetricsService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;

  @Mock private MetricService metricService;
  @Mock private AssignDelegateService assignDelegateService;
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordDelegateTaskMetrics() {
    delegateMetricsService.recordDelegateTaskMetrics(createDefaultDelegateTask(), TEST_CUSTOM_METRIC_NAME);

    Mockito.verify(metricService).incCounter(eq(TEST_CUSTOM_METRIC_NAME));
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordDelegateTaskMetricsParams() {
    delegateMetricsService.recordDelegateTaskMetrics("accountId", TEST_CUSTOM_METRIC_NAME);

    Mockito.verify(metricService).incCounter(eq(TEST_CUSTOM_METRIC_NAME));
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordResponseMetrics() {
    delegateMetricsService.recordDelegateTaskResponseMetrics(
        createDefaultDelegateTask(), createDefaultDelegateTaskResponse(), TEST_CUSTOM_METRIC_NAME);

    Mockito.verify(metricService).incCounter(eq(TEST_CUSTOM_METRIC_NAME));
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordMetrics_saveDelegateTask() {
    Mockito.when(assignDelegateService.getEligibleDelegatesToExecuteTask(anyObject()))
        .thenReturn(Lists.newArrayList("delegateId1"));
    Mockito.when(assignDelegateService.getConnectedDelegateList(anyObject(), anyObject()))
        .thenReturn(Lists.newArrayList("delegateId1"));
    delegateTaskServiceClassic.processDelegateTask(createDefaultDelegateTask(), DelegateTask.Status.QUEUED);
    Mockito.verify(metricService).incCounter(eq("delegate_task_creation"));
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testRecordMetrics_noEligibleDelegates() {
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.processDelegateTask(createDefaultDelegateTask(), DelegateTask.Status.QUEUED);
    Mockito.verify(metricService).incCounter(eq("delegate_task_no_eligible_delegates"));
    Mockito.verify(metricService).incCounter(eq("delegate_response"));
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testRecordPerpetualTaskMetrics() {
    delegateMetricsService.recordPerpetualTaskMetrics(ACCOUNT_ID, PERPETUAL_TASK_TYPE, TEST_CUSTOM_METRIC_NAME);
    Mockito.verify(metricService).incCounter(eq(TEST_CUSTOM_METRIC_NAME));
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
