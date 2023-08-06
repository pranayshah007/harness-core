/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.SPG)
public class ExecutionStatusTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotChangeStatuses() {
    removeStatuses(ExecutionStatus.finalStatuses());
    removeStatuses(ExecutionStatus.brokeStatuses());
    removeStatuses(ExecutionStatus.negativeStatuses());
    removeStatuses(ExecutionStatus.runningStatuses());
    removeStatuses(ExecutionStatus.activeStatuses());
    removeStatuses(ExecutionStatus.flowingStatuses());
    removeStatuses(ExecutionStatus.persistedStatuses());
    removeStatuses(ExecutionStatus.persistedActiveStatuses());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotChangeResumableStatuses() {
    removeStatuses(ExecutionStatus.resumableStatuses);
  }

  private void removeStatuses(Set<ExecutionStatus> statuses) {
    assertThatThrownBy(() -> statuses.removeAll(new HashSet<>(statuses))).isInstanceOf(Exception.class);
  }
}
