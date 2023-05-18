/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.nextstep;

import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.rule.OwnerRule.BRIJESH;

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
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NextStepAdviserTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks NextStepAdviser nextStepAdviser;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    String nextNodeId = "nextNodeId";
    doReturn(NextStepAdviserParameters.builder().nextNodeId(nextNodeId).build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters(null)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    AdviserResponse adviserResponse = nextStepAdviser.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getType(), AdviseType.NEXT_STEP);
    assertEquals(adviserResponse.getNextStepAdvise().getNextNodeId(), nextNodeId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCanAdvice() {
    AdvisingEvent advisingEvent = AdvisingEvent.builder().adviserParameters(null).toStatus(ABORTED).build();
    assertFalse(nextStepAdviser.canAdvise(advisingEvent));
    doReturn(OptionalSweepingOutput.builder().build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    advisingEvent = AdvisingEvent.builder().adviserParameters(null).toStatus(FAILED).build();
    assertTrue(nextStepAdviser.canAdvise(advisingEvent));
  }
}
