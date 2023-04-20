/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.retry;

import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryAdviserWithRollbackTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks RetryAdviserWithRollback retryAdviserWithRollback;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.MANUAL_INTERVENTION)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    List<String> retryIds = new ArrayList<>();
    retryIds.add("id1");
    retryIds.add("id2");
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    AdviserResponse adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.RETRY);

    advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .retryIds(retryIds)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.INTERVENTION_WAIT);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.END_EXECUTION)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.END_PLAN);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.IGNORE)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.IGNORE_FAILURE);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.STAGE_ROLLBACK)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.NEXT_STEP);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.STEP_GROUP_ROLLBACK)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.NEXT_STEP);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.ON_FAIL)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.NEXT_STEP);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.MARK_AS_SUCCESS)
                 .nextNodeId("nextNodeId")
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = retryAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.MARK_SUCCESS);

    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.UNRECOGNIZED)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    AdvisingEvent finalAdvisingEvent = advisingEvent;
    assertThatThrownBy(() -> retryAdviserWithRollback.onAdviseEvent(finalAdvisingEvent));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCanAdvice() {
    doReturn(RetryAdviserRollbackParameters.builder()
                 .applicableFailureTypes(Collections.singleton(FailureType.APPLICATION_FAILURE))
                 .repairActionCodeAfterRetry(RepairActionCode.IGNORE)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());

    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();

    assertFalse(retryAdviserWithRollback.canAdvise(advisingEvent));

    advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .toStatus(FAILED)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    assertFalse(retryAdviserWithRollback.canAdvise(advisingEvent));

    advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .toStatus(FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.APPLICATION_FAILURE).build())
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    assertTrue(retryAdviserWithRollback.canAdvise(advisingEvent));

    doReturn(RetryAdviserRollbackParameters.builder()
                 .applicableFailureTypes(Collections.emptySet())
                 .repairActionCodeAfterRetry(RepairActionCode.IGNORE)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    assertFalse(retryAdviserWithRollback.canAdvise(advisingEvent));
  }
}
