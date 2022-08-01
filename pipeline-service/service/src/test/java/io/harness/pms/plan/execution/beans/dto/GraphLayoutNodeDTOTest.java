/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GraphLayoutNodeDTOTest extends CategoryTest {
  /**
   * If this fails, either update the counter or add an update.set in the linked function
   * { @link PmsExecutionSummaryServiceImpl#cloneGraphLayoutNodeDtoWithModifications graphLayoutNode }
   */
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIfNewFieldsAreAdded() {
    assertThat(ReflectionUtils.getAllDeclaredAndInheritedFields(GraphLayoutNodeDTO.class).size())
        .isEqualTo(20)
        .withFailMessage("You have added a new field in GraphLayoutNodeDTO. If you are not "
            + "updating it anywhere after creating then please update PmsExecutionSummaryService#cloneGraphLayoutNodeDtoWithModifications");
  }
}
