/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.retry;

import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
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
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryStepGroupAdvisorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks RetryStepGroupAdvisor retryStepGroupAdvisor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    doReturn(RetryAdviserRollbackParameters.builder()
                 .repairActionCodeAfterRetry(RepairActionCode.MANUAL_INTERVENTION)
                 .waitIntervalList(Collections.singletonList(10))
                 .retryCount(1)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .toStatus(Status.FAILED)
            .build();
    AdviserResponse adviserResponse = retryStepGroupAdvisor.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.RETRY);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
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

    assertFalse(retryStepGroupAdvisor.canAdvise(advisingEvent));

    advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .toStatus(FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.APPLICATION_FAILURE).build())
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    assertTrue(retryStepGroupAdvisor.canAdvise(advisingEvent));
  }
}
