/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomStageNodeTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks CustomStageNode customStageNode;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageType() {
    assertThat(customStageNode.getType()).isEqualTo(StepSpecTypeConstants.CUSTOM_STAGE);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageInfo() {
    CustomStageConfig customStageConfig = CustomStageConfig.builder().build();
    customStageNode.setCustomStageConfig(customStageConfig);
    assertThat(customStageNode.getStageInfoConfig()).isEqualTo(customStageConfig);
  }
}
