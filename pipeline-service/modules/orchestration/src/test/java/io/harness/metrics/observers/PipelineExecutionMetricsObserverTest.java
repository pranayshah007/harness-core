/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.observers;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionMetricsObserverTest extends CategoryTest {
  @Mock PipelineMetricUtils pipelineMetricUtils;
  @InjectMocks PipelineExecutionMetricsObserver pipelineExecutionMetricsObserver;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testOnEnd() {
    String accountId = UUIDGenerator.generateUuid();
    pipelineExecutionMetricsObserver.onEnd(
        Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, accountId).build(),
        Status.SUCCEEDED);
    verify(pipelineMetricUtils)
        .publishPipelineExecutionMetrics("pipeline_execution_end_count", Status.SUCCEEDED, accountId);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testOnStart() {
    String accountId = UUIDGenerator.generateUuid();
    pipelineExecutionMetricsObserver.onStart(
        OrchestrationStartInfo.builder()
            .ambiance(Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, accountId).build())
            .planExecutionMetadata(PlanExecutionMetadata.builder().build())
            .startStatus(Status.RUNNING)
            .build());
    verify(pipelineMetricUtils)
        .publishPipelineExecutionMetrics("pipeline_execution_start_count", Status.RUNNING, accountId);
  }
}