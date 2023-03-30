/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.pipelinerollback;

import static io.harness.pms.contracts.execution.failure.FailureType.CONNECTIVITY_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.UNKNOWN_FAILURE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class OnFailPipelineRollbackAdviserTest extends CategoryTest {
  @InjectMocks OnFailPipelineRollbackAdviser onFailPipelineRollbackAdviser;
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
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("something").build();
    AdviserResponse adviserResponse =
        onFailPipelineRollbackAdviser.onAdviseEvent(AdvisingEvent.builder().ambiance(ambiance).build());
    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.NEXT_STEP);
    assertThat(adviserResponse.getNextStepAdvise().getNextNodeId()).isEmpty();
    verify(executionSweepingOutputService, times(1))
        .consumeOptional(ambiance, "usePipelineRollbackStrategy",
            OnFailPipelineRollbackOutput.builder().shouldStartPipelineRollback(true).build(), "PIPELINE");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    boolean noFailure =
        onFailPipelineRollbackAdviser.canAdvise(AdvisingEvent.builder().toStatus(Status.SUCCEEDED).build());
    assertThat(noFailure).isFalse();

    Set<FailureType> failures = Collections.singleton(UNKNOWN_FAILURE);
    doReturn(OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failures).build())
        .when(kryoSerializer)
        .asObject((byte[]) any());

    boolean differentFailure = onFailPipelineRollbackAdviser.canAdvise(
        AdvisingEvent.builder()
            .toStatus(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                             .addFailureData(FailureData.newBuilder().addFailureTypes(CONNECTIVITY_FAILURE).build())
                             .build())
            .build());
    assertThat(differentFailure).isFalse();

    boolean coveredFailure = onFailPipelineRollbackAdviser.canAdvise(
        AdvisingEvent.builder()
            .toStatus(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                             .addFailureData(FailureData.newBuilder().addFailureTypes(CONNECTIVITY_FAILURE).build())
                             .addFailureData(FailureData.newBuilder().addFailureTypes(UNKNOWN_FAILURE).build())
                             .build())
            .build());
    assertThat(coveredFailure).isTrue();
  }
}