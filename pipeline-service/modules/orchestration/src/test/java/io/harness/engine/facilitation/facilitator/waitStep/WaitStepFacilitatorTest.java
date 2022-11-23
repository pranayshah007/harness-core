/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.waitStep;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepFacilitatorTest extends OrchestrationTestBase {
  @Inject WaitStepFacilitator waitStepFacilitator;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFacilitate() {
    FacilitatorResponseProto facilitatorResponseProto =
        waitStepFacilitator.facilitate(Ambiance.newBuilder().build(), null);
    assertEquals(facilitatorResponseProto.getExecutionMode(), ExecutionMode.WAIT_STEP);
  }
}
