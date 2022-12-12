/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorBudgetRiskTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetFromPercentage() {
    assertThat(ErrorBudgetRisk.getFromPercentage(-0.001)).isEqualTo(ErrorBudgetRisk.EXHAUSTED);
    assertThat(ErrorBudgetRisk.getFromPercentage(0)).isEqualTo(ErrorBudgetRisk.UNHEALTHY);
    assertThat(ErrorBudgetRisk.getFromPercentage(1)).isEqualTo(ErrorBudgetRisk.UNHEALTHY);
    assertThat(ErrorBudgetRisk.getFromPercentage(25)).isEqualTo(ErrorBudgetRisk.NEED_ATTENTION);
    assertThat(ErrorBudgetRisk.getFromPercentage(30)).isEqualTo(ErrorBudgetRisk.NEED_ATTENTION);
    assertThat(ErrorBudgetRisk.getFromPercentage(50)).isEqualTo(ErrorBudgetRisk.OBSERVE);
    assertThat(ErrorBudgetRisk.getFromPercentage(74)).isEqualTo(ErrorBudgetRisk.OBSERVE);
    assertThat(ErrorBudgetRisk.getFromPercentage(75)).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(ErrorBudgetRisk.getFromPercentage(99)).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(ErrorBudgetRisk.getFromPercentage(100)).isEqualTo(ErrorBudgetRisk.HEALTHY);
  }
}
