/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.wait;

import static junit.framework.TestCase.assertEquals;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.timeout.Timeout;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepInfoTest extends OrchestrationStepsTestBase {
  WaitStepInfo waitStepInfo =
      new WaitStepInfo(ParameterField.createValueField(Timeout.builder().timeoutString("20s").build()), "uuod");
  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertEquals(waitStepInfo.getStepType(), StepSpecTypeConstants.WAIT_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    assertEquals(waitStepInfo.getFacilitatorType(), OrchestrationFacilitatorType.WAIT_STEP);
  }
}
