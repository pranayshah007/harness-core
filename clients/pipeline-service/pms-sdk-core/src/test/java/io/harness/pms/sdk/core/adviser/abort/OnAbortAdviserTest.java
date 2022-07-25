/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.abort;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class OnAbortAdviserTest extends PmsSdkCoreTestBase {
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setStepCategory(StepCategory.STEP).setType(NODE_IDENTIFIER).build();

  @Inject OnAbortAdviser onAbortAdviser;
  @Inject KryoSerializer kryoSerializer;

  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = AmbianceTestUtils.buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .addLevels(Level.newBuilder()
                                  .setSetupId(NODE_SETUP_ID)
                                  .setRuntimeId(NODE_EXECUTION_ID)
                                  .setIdentifier(NODE_IDENTIFIER)
                                  .setStepType(DUMMY_STEP_TYPE)
                                  .build())
                   .build();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnAdviseEvent() {
    AdvisingEvent advisingEvent = AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.FAILED).build();
    AdviserResponse adviserResponse = onAbortAdviser.onAdviseEvent(advisingEvent);
    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.END_PLAN);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestCanAdvise() {
    byte[] paramBytes = kryoSerializer.asBytes(OnAbortAdviserParameters.builder().build());
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.ABORTED).adviserParameters(paramBytes).build();
    boolean canAdvise = onAbortAdviser.canAdvise(advisingEvent);
    assertThat(canAdvise).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestCanAdviseWithFailureTypes() {
    byte[] paramBytes = kryoSerializer.asBytes(
        OnAbortAdviserParameters.builder()
            .applicableFailureTypes(EnumSet.of(FailureType.CONNECTIVITY_FAILURE, FailureType.AUTHENTICATION_FAILURE))
            .build());
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.TIMEOUT_FAILURE).build())
            .toStatus(Status.ABORTED)
            .adviserParameters(paramBytes)
            .build();
    boolean canAdvise = onAbortAdviser.canAdvise(advisingEvent);
    assertThat(canAdvise).isFalse();
  }
}
