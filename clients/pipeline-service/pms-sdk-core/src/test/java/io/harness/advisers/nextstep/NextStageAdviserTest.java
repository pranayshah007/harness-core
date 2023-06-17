/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.nextstep;

import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;
import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NextStageAdviserTest extends CategoryTest {
  @InjectMocks NextStageAdviser nextStageAdviser;
  @Mock KryoSerializer kryoSerializer;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  Ambiance normalModeAmbiance;
  Ambiance rollbackModeAmbiance;

  String nextNodeId = "ab";
  String prbStageId = "cd";

  RefObject refObject;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    normalModeAmbiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build()).build();
    rollbackModeAmbiance = Ambiance.newBuilder()
                               .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(PIPELINE_ROLLBACK).build())
                               .build();
    refObject = RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    NextStepAdviserParameters nextStepAdviserParameters =
        NextStepAdviserParameters.builder().nextNodeId(nextNodeId).build();
    NextStageAdviserParameters nextStageAdviserParameters =
        NextStageAdviserParameters.builder().nextNodeId(nextNodeId).pipelineRollbackStageId(prbStageId).build();
    byte[] byteArray = ByteString.empty().toByteArray();

    doReturn(nextStepAdviserParameters).when(kryoSerializer).asObject(byteArray);
    AdviserResponse adviserResponse0 = nextStageAdviser.onAdviseEvent(
        AdvisingEvent.builder().ambiance(rollbackModeAmbiance).adviserParameters(byteArray).build());
    assertThat(adviserResponse0.getNextStepAdvise().getNextNodeId()).isEqualTo(nextNodeId);

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(normalModeAmbiance, refObject);
    AdviserResponse adviserResponse1 = nextStageAdviser.onAdviseEvent(
        AdvisingEvent.builder().ambiance(normalModeAmbiance).adviserParameters(byteArray).build());
    assertThat(adviserResponse1.getNextStepAdvise().getNextNodeId()).isEqualTo(nextNodeId);

    doReturn(nextStageAdviserParameters).when(kryoSerializer).asObject(byteArray);
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(normalModeAmbiance, refObject);
    AdviserResponse adviserResponse2 = nextStageAdviser.onAdviseEvent(
        AdvisingEvent.builder().ambiance(normalModeAmbiance).adviserParameters(byteArray).build());
    assertThat(adviserResponse2.getNextStepAdvise().getNextNodeId()).isEqualTo(prbStageId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsRollbackModeExecution() {
    assertThat(NextStageAdviser.isRollbackModeExecution(AdvisingEvent.builder().ambiance(normalModeAmbiance).build()))
        .isFalse();
    assertThat(NextStageAdviser.isRollbackModeExecution(AdvisingEvent.builder().ambiance(rollbackModeAmbiance).build()))
        .isTrue();
  }
}