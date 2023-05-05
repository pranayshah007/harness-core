/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AllowedValuesHelperTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSplit() {
    assertEquals(
        AllowedValuesHelper.split("abc, \\'def, ghi\\', \\\"jkl, mn\\\""), List.of("abc", "def, ghi", "jkl, mn"));
    assertEquals(AllowedValuesHelper.split("abc's\\xyz, \\'def'm, gh\\i\\', \\\"jkl, mn\\\", \\'mnk"),
        List.of("abc's\\xyz", "def'm, gh\\i", "jkl, mn", "\\'mnk"));
  }
}
