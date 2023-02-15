/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.onmarkfailure;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.MarkAsFailureAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviser;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviserParameters;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class OnMarkFailureAdviserTest extends CategoryTest {
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setStepCategory(StepCategory.STEP).setType(NODE_IDENTIFIER).build();

  @Mock KryoSerializer kryoSerializer;
  @InjectMocks OnMarkFailureAdviser onMarkFailureAdviser;
  private Ambiance ambiance;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setSetupId(NODE_SETUP_ID)
                                  .setRuntimeId(NODE_EXECUTION_ID)
                                  .setIdentifier(NODE_IDENTIFIER)
                                  .setStepType(DUMMY_STEP_TYPE)
                                  .build())
                   .build();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void shouldTestOnAdviseEvent() {
    String nextNodeId = generateUuid();
    OnMarkFailureAdviserParameters onMarkFailureAdviserParameters =
        OnMarkFailureAdviserParameters.builder().nextNodeId(nextNodeId).build();
    AdvisingEvent advisingEvent = AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.SUCCEEDED).build();

    doReturn(onMarkFailureAdviserParameters).when(kryoSerializer).asObject((byte[]) any());

    AdviserResponse adviserResponse = onMarkFailureAdviser.onAdviseEvent(advisingEvent);
    assertThat(adviserResponse).isInstanceOf(AdviserResponse.class);
    MarkAsFailureAdvise nextStepAdvise = adviserResponse.getMarkAsFailureAdvise();
    assertThat(nextStepAdvise.getNextNodeId()).isEqualTo(nextNodeId);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void shouldTestCanAdvise() {
    OnMarkFailureAdviserParameters onMarkFailureAdviserParameters = OnMarkFailureAdviserParameters.builder().build();
    AdvisingEvent advisingEvent = AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.FAILED).build();

    doReturn(onMarkFailureAdviserParameters).when(kryoSerializer).asObject((byte[]) any());

    boolean canAdvise = onMarkFailureAdviser.canAdvise(advisingEvent);
    assertThat(canAdvise).isTrue();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void shouldTestCanAdviseWithFailureTypes() {
    OnMarkFailureAdviserParameters onMarkFailureAdviserParameters =
        OnMarkFailureAdviserParameters.builder()
            .applicableFailureTypes(EnumSet.of(FailureType.CONNECTIVITY_FAILURE, FailureType.AUTHENTICATION_FAILURE))
            .build();
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.DELEGATE_PROVISIONING_FAILURE).build())
            .toStatus(Status.ABORTED)
            .build();

    doReturn(onMarkFailureAdviserParameters).when(kryoSerializer).asObject((byte[]) any());

    boolean canAdvise = onMarkFailureAdviser.canAdvise(advisingEvent);
    assertThat(canAdvise).isFalse();
  }
}
