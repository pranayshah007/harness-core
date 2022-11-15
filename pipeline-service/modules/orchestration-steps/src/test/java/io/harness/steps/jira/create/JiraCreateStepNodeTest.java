/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira.create;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class JiraCreateStepNodeTest extends OrchestrationStepsTestBase {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetType() {
    assertEquals(new JiraCreateStepNode().getType(), StepSpecTypeConstants.JIRA_CREATE);
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    assertNull(new JiraCreateStepNode().getStepSpecType());
  }
}
