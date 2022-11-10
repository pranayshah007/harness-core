/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.wait;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.wait.WaitStepAction;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepActionMapperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testMapWaitStepAction() {
    WaitStepActionDto waitStepActionDto = WaitStepActionDto.MARK_AS_FAIL;
    WaitStepAction waitStepAction = WaitStepActionMapper.mapWaitStepAction(waitStepActionDto);
    assertEquals(waitStepAction, WaitStepAction.MARK_AS_FAIL);
  }
}
