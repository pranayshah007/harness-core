/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.rollback;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.steps.common.NGSectionStepParameters;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class RollbackStageStepTest extends CategoryTest {
  RollbackStageStep rollbackStageStep;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    rollbackStageStep = new RollbackStageStep(executionSweepingOutputService);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testObtainChild() {
    NGSectionStepParameters stepParameters =
        NGSectionStepParameters.builder().logMessage("msg").childNodeId("child").build();
    ChildExecutableResponse response = rollbackStageStep.obtainChild(null, stepParameters, null);
    Mockito.verify(executionSweepingOutputService, Mockito.times(1))
        .consumeOptional(null, "rollback", RollbackStageSweepingOutput.builder().isPipelineRollback(true).build(),
            StepOutcomeGroup.PIPELINE.name());
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo("child");
  }
}