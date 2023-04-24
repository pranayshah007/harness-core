/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.manualIntervention;

import static io.harness.pms.contracts.commons.RepairActionCode.CUSTOM_FAILURE;
import static io.harness.pms.contracts.commons.RepairActionCode.STAGE_ROLLBACK;
import static io.harness.pms.contracts.commons.RepairActionCode.STEP_GROUP_ROLLBACK;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
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
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ManualInterventionAdviserWithRollbackTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks ManualInterventionAdviserWithRollback manualInterventionAdviserWithRollback;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    doReturn(ManualInterventionAdviserRollbackParameters.builder()
                 .timeout(60)
                 .timeoutAction(RepairActionCode.MANUAL_INTERVENTION)
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .adviserParameters("abcd".getBytes())
            .toStatus(FAILED)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
            .build();
    AdviserResponse adviserResponse = manualInterventionAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getFromStatus(), FAILED);
    assertEquals(
        adviserResponse.getInterventionWaitAdvise().getRepairActionCode(), RepairActionCode.MANUAL_INTERVENTION);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getMetadataCount(), 0);

    doReturn(
        ManualInterventionAdviserRollbackParameters.builder().timeout(60).timeoutAction(STEP_GROUP_ROLLBACK).build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = manualInterventionAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getRepairActionCode(), CUSTOM_FAILURE);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getMetadata().get("ROLLBACK"),
        NGFailureActionTypeConstants.STEP_GROUP_ROLLBACK);

    doReturn(ManualInterventionAdviserRollbackParameters.builder().timeout(60).timeoutAction(STAGE_ROLLBACK).build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    adviserResponse = manualInterventionAdviserWithRollback.onAdviseEvent(advisingEvent);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getRepairActionCode(), CUSTOM_FAILURE);
    assertEquals(adviserResponse.getInterventionWaitAdvise().getMetadata().get("ROLLBACK"),
        NGFailureActionTypeConstants.STAGE_ROLLBACK);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCanAdvice() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder().adviserParameters(null).fromStatus(FAILED).isPreviousAdviserExpired(true).build();
    assertFalse(manualInterventionAdviserWithRollback.canAdvise(advisingEvent));

    advisingEvent = AdvisingEvent.builder().adviserParameters(null).fromStatus(FAILED).toStatus(FAILED).build();
    assertTrue(manualInterventionAdviserWithRollback.canAdvise(advisingEvent));

    advisingEvent = AdvisingEvent.builder()
                        .adviserParameters(null)
                        .toStatus(FAILED)
                        .adviserParameters("abcd".getBytes())
                        .fromStatus(FAILED)
                        .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.APPLICATION_FAILURE).build())
                        .build();
    doReturn(ManualInterventionAdviserRollbackParameters.builder().build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    assertFalse(manualInterventionAdviserWithRollback.canAdvise(advisingEvent));
    doReturn(ManualInterventionAdviserRollbackParameters.builder()
                 .applicableFailureTypes(Collections.singleton(FailureType.APPLICATION_FAILURE))
                 .build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    assertTrue(manualInterventionAdviserWithRollback.canAdvise(advisingEvent));

    // with status as INTERVENTION_WAITING
    advisingEvent = AdvisingEvent.builder()
                        .adviserParameters(null)
                        .isPreviousAdviserExpired(true)
                        .fromStatus(INTERVENTION_WAITING)
                        .toStatus(SUCCEEDED)
                        .build();
    assertFalse(manualInterventionAdviserWithRollback.canAdvise(advisingEvent));
  }
}
