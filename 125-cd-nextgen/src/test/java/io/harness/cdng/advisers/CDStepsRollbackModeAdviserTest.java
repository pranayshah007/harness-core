/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.advisers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.core.resolver.RefObjectUtils.getSweepingOutputRefObject;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE_ROLLBACK_FAILURE_INFO;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STOP_STEPS_SEQUENCE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackOutput;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class CDStepsRollbackModeAdviserTest extends CategoryTest {
  @InjectMocks CDStepsRollbackModeAdviser cdStepsRollbackModeAdviser;
  @Mock KryoSerializer kryoSerializer;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    doReturn(NextStepAdviserParameters.builder().nextNodeId("next").build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    AdviserResponse adviserResponse = cdStepsRollbackModeAdviser.onAdviseEvent(
        AdvisingEvent.builder().adviserParameters(ByteString.empty().toByteArray()).build());
    assertThat(adviserResponse.getNextStepAdvise().getNextNodeId()).isEqualTo("next");
    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.NEXT_STEP);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    AdvisingEvent aborted = AdvisingEvent.builder().toStatus(Status.ABORTED).build();
    assertThat(cdStepsRollbackModeAdviser.canAdvise(aborted)).isFalse();

    Level executionLevel = Level.newBuilder().setIdentifier("execution").build();
    Ambiance executionAmbiance = Ambiance.newBuilder().addLevels(executionLevel).build();
    AdvisingEvent executionAdvisingEvent = AdvisingEvent.builder().ambiance(executionAmbiance).build();

    Level provisionerLevel = Level.newBuilder().setIdentifier("provisioner").build();
    Ambiance provisionerAmbiance = Ambiance.newBuilder().addLevels(provisionerLevel).build();
    AdvisingEvent provisionerAdvisingEvent = AdvisingEvent.builder().ambiance(provisionerAmbiance).build();

    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(executionAmbiance, getSweepingOutputRefObject(STOP_STEPS_SEQUENCE));
    assertThat(cdStepsRollbackModeAdviser.canAdvise(executionAdvisingEvent)).isFalse();

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    assertThat(cdStepsRollbackModeAdviser.canAdvise(executionAdvisingEvent)).isTrue();
    assertThat(cdStepsRollbackModeAdviser.canAdvise(provisionerAdvisingEvent)).isFalse();

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(OnFailPipelineRollbackOutput.builder()
                             .levelsAtFailurePoint(Collections.singletonList(executionLevel))
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(executionAmbiance, getSweepingOutputRefObject(PIPELINE_ROLLBACK_FAILURE_INFO));
    assertThat(cdStepsRollbackModeAdviser.canAdvise(executionAdvisingEvent)).isTrue();
    assertThat(cdStepsRollbackModeAdviser.canAdvise(provisionerAdvisingEvent)).isFalse();

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(OnFailPipelineRollbackOutput.builder()
                             .levelsAtFailurePoint(Collections.singletonList(provisionerLevel))
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(executionAmbiance, getSweepingOutputRefObject(PIPELINE_ROLLBACK_FAILURE_INFO));
    assertThat(cdStepsRollbackModeAdviser.canAdvise(executionAdvisingEvent)).isFalse();

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(OnFailPipelineRollbackOutput.builder()
                             .levelsAtFailurePoint(Collections.singletonList(provisionerLevel))
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(provisionerAmbiance, getSweepingOutputRefObject(PIPELINE_ROLLBACK_FAILURE_INFO));
    assertThat(cdStepsRollbackModeAdviser.canAdvise(provisionerAdvisingEvent)).isTrue();
  }
}