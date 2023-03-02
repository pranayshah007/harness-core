/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.retry;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise;
import io.harness.pms.contracts.advisers.RetryAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.AdvisingEvent.AdvisingEventBuilder;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryAdviserTest extends PmsSdkCoreTestBase {
  public static final String DUMMY_NODE_ID = generateUuid();
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_NAME = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();

  @InjectMocks @Inject RetryAdviser retryAdviser;

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
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestValidStatusWithIgnore() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.RETRY);
    assertThat(adviserResponse.getRetryAdvise()).isNotNull();
    RetryAdvise retryAdvise = adviserResponse.getRetryAdvise();
    assertThat(retryAdvise.getRetryNodeExecutionId()).isEqualTo(NODE_EXECUTION_ID);
    assertThat(retryAdvise.getWaitInterval()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePostRetryWithManualIntervention() {
    List<String> retryIds = Lists.newArrayList(
        generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid());

    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .retryIds(retryIds)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.MANUAL_INTERVENTION)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.INTERVENTION_WAIT);
    assertThat(adviserResponse.getInterventionWaitAdvise()).isNotNull();
    InterventionWaitAdvise interventionWaitAdvise = adviserResponse.getInterventionWaitAdvise();
    assertThat(interventionWaitAdvise.getTimeout())
        .isEqualTo(Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePostRetryWithEndExecution() {
    List<String> retryIds = Lists.newArrayList(
        generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid());

    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .retryIds(retryIds)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.END_EXECUTION)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.END_PLAN);
    assertThat(adviserResponse.getEndPlanAdvise()).isNotNull();
    EndPlanAdvise endPlanAdvise = adviserResponse.getEndPlanAdvise();
    assertThat(endPlanAdvise.getIsAbort()).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePostRetryWithOnFail() {
    List<String> retryIds = Lists.newArrayList(
        generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid());

    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .retryIds(retryIds)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.ON_FAIL)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.NEXT_STEP);
    assertThat(adviserResponse.getNextStepAdvise()).isNotNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePostRetryWithOnMarkAsSucess() {
    List<String> retryIds = Lists.newArrayList(
        generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid());

    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .retryIds(retryIds)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.MARK_AS_SUCCESS)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.MARK_SUCCESS);
    assertThat(adviserResponse.getMarkSuccessAdvise()).isNotNull();
    MarkSuccessAdvise markSuccessAdvise = adviserResponse.getMarkSuccessAdvise();
    assertThat(markSuccessAdvise.getNextNodeId()).isEqualTo(DUMMY_NODE_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestLastWaitInterval() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.<io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters>builder()
            .ambiance(ambiance)
            .retryIds(Arrays.asList(generateUuid(), generateUuid(), generateUuid(), generateUuid()))
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.RETRY);
    assertThat(adviserResponse.getRetryAdvise()).isNotNull();
    RetryAdvise retryAdvise = adviserResponse.getRetryAdvise();
    assertThat(retryAdvise.getRetryNodeExecutionId()).isEqualTo(NODE_EXECUTION_ID);
    assertThat(retryAdvise.getWaitInterval()).isEqualTo(5);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestAfterRetryStatus() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .retryIds(Arrays.asList(generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid()))
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)))
            .build();
    AdviserResponse adviserResponse = retryAdviser.onAdviseEvent(advisingEvent);

    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.IGNORE_FAILURE);
    assertThat(adviserResponse.getIgnoreFailureAdvise()).isNotNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .retryIds(Arrays.asList(generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid()))
            .toStatus(Status.INTERVENTION_WAITING)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)))
            .build();
    assertThat(retryAdviser.canAdvise(advisingEvent)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCanAdvise() {
    AdvisingEventBuilder advisingEventBuilder =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("Auth Error")
                             .addAllFailureTypes(EnumSet.of(FailureType.AUTHENTICATION_FAILURE))
                             .build())
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)));

    AdvisingEvent authFailEvent = advisingEventBuilder.build();
    boolean canAdvise = retryAdviser.canAdvise(authFailEvent);
    assertThat(canAdvise).isTrue();

    AdvisingEvent appFailEvent = advisingEventBuilder
                                     .failureInfo(FailureInfo.newBuilder()
                                                      .setErrorMessage("Application Error")
                                                      .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                                                      .build())
                                     .build();
    canAdvise = retryAdviser.canAdvise(appFailEvent);
    assertThat(canAdvise).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetAllFailureTypes() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .ambiance(ambiance)
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(getRetryParams(RepairActionCode.IGNORE)))
            .failureInfo(FailureInfo.newBuilder()
                             .addAllFailureTypes(Collections.singletonList(FailureType.APPLICATION_FAILURE))
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                                                 .setLevel(io.harness.eraro.Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage("Some Exception Message")
                                                 .build())
                             .build())
            .build();
    List<FailureType> allFailureTypes = retryAdviser.getAllFailureTypes(advisingEvent);
    assertThat(allFailureTypes).hasSize(2);
    assertThat(allFailureTypes).containsExactlyInAnyOrder(FailureType.APPLICATION_FAILURE, FailureType.TIMEOUT_FAILURE);
  }

  private static io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters getRetryParams(
      RepairActionCode repairActionCode) {
    return RetryAdviserParameters.builder()
        .retryCount(5)
        .waitIntervalList(ImmutableList.of(2, 5))
        .repairActionCodeAfterRetry(repairActionCode)
        .nextNodeId(DUMMY_NODE_ID)
        .applicableFailureTypes(EnumSet.of(FailureType.AUTHENTICATION_FAILURE))
        .build();
  }
}
