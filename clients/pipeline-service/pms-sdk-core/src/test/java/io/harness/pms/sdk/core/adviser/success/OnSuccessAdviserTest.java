/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.success;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.IGNORE_FAILED;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.execution.Status.SUSPENDED;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
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
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PIPELINE)
public class OnSuccessAdviserTest extends PmsSdkCoreTestBase {
  public static final String DUMMY_NODE_ID = generateUuid();
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_NAME = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();

  private final EnumSet<Status> POSITIVE_STATUSES = EnumSet.of(SUCCEEDED, SKIPPED, SUSPENDED, IGNORE_FAILED);

  @InjectMocks @Inject OnSuccessAdviser successAdviser;

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
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    AdvisingEvent advisingEvent = AdvisingEvent.builder()
                                      .ambiance(ambiance)
                                      .toStatus(Status.FAILED)
                                      .adviserParameters(kryoSerializer.asBytes(getOnSuccessParams()))
                                      .build();
    AdviserResponse adviserResponse = successAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.NEXT_STEP);
    assertThat(adviserResponse.getNextStepAdvise()).isNotNull();
    NextStepAdvise nextStepAdvise = adviserResponse.getNextStepAdvise();
    assertThat(nextStepAdvise.getNextNodeId()).isEqualTo(DUMMY_NODE_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    for (Status status : POSITIVE_STATUSES) {
      AdvisingEvent advisingEvent = AdvisingEvent.builder()
                                        .ambiance(ambiance)
                                        .toStatus(status)
                                        .adviserParameters(kryoSerializer.asBytes(getOnSuccessParams()))
                                        .build();
      assertThat(successAdviser.canAdvise(advisingEvent)).isTrue();
    }
  }

  private static OnSuccessAdviserParameters getOnSuccessParams() {
    return OnSuccessAdviserParameters.builder().nextNodeId(DUMMY_NODE_ID).build();
  }
}
