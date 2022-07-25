/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.plan.NodeType.PLAN;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionStrategyTest extends OrchestrationTestBase {
  @Inject NodeExecutionStrategyFactory nodeExecutionStrategyFactory;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testStartExecution() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).startExecution(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessFacilitationResponse() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).processFacilitationResponse(null, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleSdkResponseEvent() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).handleSdkResponseEvent(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponse() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).processAdviserResponse(null, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStepResponse() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).processStepResponse(null, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testResumeNodeExecution() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).resumeNodeExecution(null, null, true))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStartEventResponse() {
    assertThatThrownBy(() -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).processStartEventResponse(null, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testConcludeExecution() {
    assertThatThrownBy(
        () -> nodeExecutionStrategyFactory.obtainStrategy(PLAN).concludeExecution(null, null, null, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
