/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.steps.ShellScriptProvisionStepPlanCreator;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ShellScriptProvisionStepPlanCreatorTest extends CategoryTest {
  private final ShellScriptProvisionStepPlanCreator planCreator = new ShellScriptProvisionStepPlanCreator();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(planCreator.getFieldClass()).isEqualTo(ShellScriptProvisionStepNode.class);
  }
}
