/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.isFinalStatus;
import static io.harness.beans.ExecutionStatus.isNotFinalStatus;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ExecutionStatusTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testMappingFromStatusToExecutionStatus() {
    Set<ExecutionStatus> executionStatusesVisited = new HashSet<>();
    for (Status value : Status.values()) {
      if (Status.NO_OP == value || Status.UNRECOGNIZED == value) {
        continue;
      }
      ExecutionStatus executionStatus = ExecutionStatus.getExecutionStatus(value);
      assertThat(executionStatus).isNotNull();
      boolean contains = executionStatusesVisited.contains(executionStatus);
      assertThat(contains).isFalse();
      executionStatusesVisited.add(executionStatus);
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateFinalStatus() {
    assertThat(isFinalStatus(ABORTED)).isTrue();
    assertThat(isFinalStatus(ERROR)).isTrue();
    assertThat(isFinalStatus(FAILED)).isTrue();
    assertThat(isFinalStatus(SUCCESS)).isTrue();
    assertThat(isFinalStatus(REJECTED)).isTrue();
    assertThat(isFinalStatus(EXPIRED)).isTrue();
    assertThat(isFinalStatus(SKIPPED)).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateNotFinalStatus() {
    assertThat(isNotFinalStatus(ABORTED)).isFalse();
    assertThat(isNotFinalStatus(ERROR)).isFalse();
    assertThat(isNotFinalStatus(FAILED)).isFalse();
    assertThat(isNotFinalStatus(SUCCESS)).isFalse();
    assertThat(isNotFinalStatus(REJECTED)).isFalse();
    assertThat(isNotFinalStatus(EXPIRED)).isFalse();
    assertThat(isNotFinalStatus(SKIPPED)).isFalse();
  }
}
