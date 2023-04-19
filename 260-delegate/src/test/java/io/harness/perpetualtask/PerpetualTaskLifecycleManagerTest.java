/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskParams;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.protobuf.Any;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PerpetualTaskLifecycleManagerTest extends CategoryTest {
  private PerpetualTaskLifecycleManager perpetualTaskLifecycleManager;
  private final Map<String, PerpetualTaskExecutor> factoryMap = new HashMap<>();
  @Mock private TimeLimiter timeLimiter;
  @Mock private PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient;

  @Mock private PerpetualTaskExecutor perpetualTaskExecutor;

  @Captor private ArgumentCaptor<PerpetualTaskId> perpetualTaskIdArgumentCaptor;
  @Captor private ArgumentCaptor<Instant> instantArgumentCaptor;
  @Captor private ArgumentCaptor<PerpetualTaskResponse> perpetualTaskResponseArgumentCaptor;
  @Captor private ArgumentCaptor<String> accountIdArgumentCaptor;

  private final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private final String ACCOUNT_ID = "test-account-id";

  private final String EXCEPTION_MSG = "Some exception message";

  private final String EXCEPTION_MSG_RESPONSE = "Exception: Some exception message";

  private final String TIMEDOUT_EXCEPTION = "Timedout exception message";

  private final String TIMEDOUT_EXCEPTION_RESPONSE = "UncheckedTimeoutException: Timedout exception message";

  @Mock private AtomicInteger currentlyExecutingPerpetualTasksCount;

  @Before
  public void setUp() throws Exception {
    factoryMap.put("EcsPerpetualTaskParams", perpetualTaskExecutor);

    String REGION = "us-east-1";
    String CLUSTER_ID = "clusterId";
    String SETTING_ID = "settingId";
    String CLUSTER_NAME = "ecs-ccm-cluster";
    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    PerpetualTaskExecutionContext taskContext =
        PerpetualTaskExecutionContext.newBuilder().setTaskParams(params).build();
    perpetualTaskLifecycleManager = new PerpetualTaskLifecycleManager(perpetualTaskId, taskContext, factoryMap,
        perpetualTaskServiceAgentClient, timeLimiter, currentlyExecutingPerpetualTasksCount, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testRunOnceWhenTimeout() {
    PerpetualTaskResponse perpetualTaskResponse =
        PerpetualTaskResponse.builder().responseCode(408).responseMessage(TIMEDOUT_EXCEPTION_RESPONSE).build();
    when(perpetualTaskExecutor.runOnce(any(), any(), any())).thenAnswer(invocation -> {
      throw new UncheckedTimeoutException(TIMEDOUT_EXCEPTION);
    });
    when(currentlyExecutingPerpetualTasksCount.get()).thenReturn(1);
    perpetualTaskLifecycleManager.call();
    verify(perpetualTaskServiceAgentClient)
        .recordPerpetualTaskFailure(perpetualTaskIdArgumentCaptor.capture(), accountIdArgumentCaptor.capture(),
            perpetualTaskResponseArgumentCaptor.capture());
    assertThat(perpetualTaskIdArgumentCaptor.getValue().getId()).isEqualTo(PERPETUAL_TASK_ID);
    assertThat(perpetualTaskResponseArgumentCaptor.getValue()).isEqualTo(perpetualTaskResponse);

    verify(currentlyExecutingPerpetualTasksCount, times(1)).getAndIncrement();
    verify(currentlyExecutingPerpetualTasksCount, times(1)).getAndDecrement();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testRunOnceWhenExceptionWhileExecuting() {
    PerpetualTaskResponse perpetualTaskResponse =
        PerpetualTaskResponse.builder().responseCode(500).responseMessage(EXCEPTION_MSG_RESPONSE).build();
    when(perpetualTaskExecutor.runOnce(any(), any(), any())).thenAnswer(invocation -> {
      throw new Exception(EXCEPTION_MSG);
    });
    when(currentlyExecutingPerpetualTasksCount.get()).thenReturn(1);
    perpetualTaskLifecycleManager.call();
    verify(perpetualTaskServiceAgentClient)
        .recordPerpetualTaskFailure(perpetualTaskIdArgumentCaptor.capture(), accountIdArgumentCaptor.capture(),
            perpetualTaskResponseArgumentCaptor.capture());
    assertThat(perpetualTaskIdArgumentCaptor.getValue().getId()).isEqualTo(PERPETUAL_TASK_ID);
    assertThat(perpetualTaskResponseArgumentCaptor.getValue()).isEqualTo(perpetualTaskResponse);

    verify(currentlyExecutingPerpetualTasksCount, times(1)).getAndIncrement();
    verify(currentlyExecutingPerpetualTasksCount, times(1)).getAndDecrement();
  }
}
