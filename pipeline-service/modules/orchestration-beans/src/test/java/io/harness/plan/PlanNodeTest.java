/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanNodeTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestFromExpressionModeProto() {
    for (ExpressionMode expressionMode : ExpressionMode.values()) {
      if (expressionMode == ExpressionMode.UNRECOGNIZED || expressionMode == ExpressionMode.UNKNOWN_MODE) {
        assertThat(ExpressionModeMapper.fromExpressionModeProto(expressionMode))
            .isEqualTo(io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        continue;
      }
      io.harness.expression.common.ExpressionMode mappedExpressionMode =
          ExpressionModeMapper.fromExpressionModeProto(expressionMode);
      assertThat(mappedExpressionMode.name()).isEqualTo(expressionMode.name());
      assertThat(mappedExpressionMode.getIndex()).isEqualTo(expressionMode.getNumber());
    }
  }
}
