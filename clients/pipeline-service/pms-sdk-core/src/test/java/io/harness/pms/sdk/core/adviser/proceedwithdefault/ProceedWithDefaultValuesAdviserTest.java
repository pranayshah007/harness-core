/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.proceedwithdefault;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.ProceedWithDefaultAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ProceedWithDefaultValuesAdviserTest extends CategoryTest {
  ProceedWithDefaultValueAdviser adviser = new ProceedWithDefaultValueAdviser();
  private Ambiance ambiance;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    AdvisingEvent advisingEvent = AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.EXPIRED).build();

    AdviserResponse adviserResponse = adviser.onAdviseEvent(advisingEvent);
    assertThat(adviserResponse.getProceedWithDefaultAdvise()).isEqualTo(ProceedWithDefaultAdvise.newBuilder().build());
    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.PROCEED_WITH_DEFAULT);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder().ambiance(ambiance).toStatus(Status.INPUT_WAITING).fromStatus(Status.EXPIRED).build();
    assertThat(adviser.canAdvise(advisingEvent)).isTrue();
  }
}
