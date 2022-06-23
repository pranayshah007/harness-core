/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class QueueHoldingScopeTest {
  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetHoldingScopePipeline() {
    assertThat(QueueHoldingScope.PIPELINE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("Pipeline"));
    assertThat(QueueHoldingScope.PIPELINE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("PipeLine"));
    assertThat(QueueHoldingScope.PIPELINE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("PIPELINE"));
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetHoldingScopeStage() {
    assertThat(QueueHoldingScope.STAGE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("Stage"));
    assertThat(QueueHoldingScope.STAGE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("StaGE"));
    assertThat(QueueHoldingScope.STAGE).isEqualTo(QueueHoldingScope.getQueueHoldingScope("STAGE"));
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotGetHoldingScope() {
    assertThatCode(() -> QueueHoldingScope.getQueueHoldingScope("N/A"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value: N/A");
    assertThatCode(() -> QueueHoldingScope.getQueueHoldingScope("Pipeline "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value: Pipeline ");
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldEveryQueueScopeHasOurHoldingScope() {
    assertThat(QueueHoldingScope.PIPELINE.getHoldingScope()).isEqualTo(HoldingScope.PIPELINE);
    assertThat(QueueHoldingScope.STAGE.getHoldingScope()).isEqualTo(HoldingScope.STAGE);
  }
}
