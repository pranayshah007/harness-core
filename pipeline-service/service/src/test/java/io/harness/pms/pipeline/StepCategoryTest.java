/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StepCategoryTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddStepData() {
    StepCategory stepCategory = StepCategory.builder().build();
    assertThat(stepCategory.getStepsData()).hasSize(0);
    StepData stepData = StepData.builder().name("search").type("Http").build();
    stepCategory.addStepData(stepData);
    assertThat(stepCategory.getStepsData()).hasSize(1);
    assertThat(stepCategory.getStepsData().get(0).getName()).isEqualTo("search");
    assertThat(stepCategory.getStepsData().get(0).getType()).isEqualTo("Http");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddStepCategory() {
    StepCategory stepCategory = StepCategory.builder().build();
    assertThat(stepCategory.getStepCategories()).hasSize(0);

    StepCategory innerStepCategory = StepCategory.builder().build();
    StepData stepData = StepData.builder().name("search").type("Http").build();
    innerStepCategory.addStepData(stepData);

    stepCategory.addStepCategory(innerStepCategory);
    assertThat(stepCategory.getStepCategories()).hasSize(1);
    assertThat(stepCategory.getStepCategories().get(0)).isEqualTo(innerStepCategory);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetChildStepCategory() {
    StepCategory innerStepCategory = StepCategory.builder().name("search").build();

    StepCategory stepCategory = StepCategory.builder().build();
    assertThat(stepCategory.getStepCategories()).hasSize(0);
    stepCategory.addStepCategory(innerStepCategory);

    assertThat(stepCategory.getOrCreateChildStepCategory("search")).isEqualTo(innerStepCategory);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateChildStepCategory() {
    StepCategory innerStepCategory = StepCategory.builder().name("search").build();

    StepCategory stepCategory = StepCategory.builder().build();
    assertThat(stepCategory.getStepCategories()).hasSize(0);

    assertThat(stepCategory.getOrCreateChildStepCategory("search")).isEqualTo(innerStepCategory);
  }
}
