/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.ExecutionCheck;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbstractPreFacilitationCheckerTest extends OrchestrationTestBase {
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCheck() {
    RunPreFacilitationChecker rChecker = spy(RunPreFacilitationChecker.class);
    SkipPreFacilitationChecker sChecker = spy(SkipPreFacilitationChecker.class);
    rChecker.setNextChecker(sChecker);

    doReturn(ExecutionCheck.builder().proceed(true).build())
        .when(rChecker)
        .performCheck(any(Ambiance.class), any(Node.class));
    doReturn(ExecutionCheck.builder().proceed(true).build())
        .when(sChecker)
        .performCheck(any(Ambiance.class), any(Node.class));

    rChecker.check(Ambiance.newBuilder().getDefaultInstanceForType(), PlanNode.builder().build());

    verify(rChecker).performCheck(any(Ambiance.class), any(Node.class));
    verify(sChecker).performCheck(any(Ambiance.class), any(Node.class));
  }
}
