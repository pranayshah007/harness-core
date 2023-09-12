/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.rule.Owner;

import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomStageFilterCreatorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks CustomStageFilterCreator customStageFilterCreator;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageFilterCreator() {
    Set<String> stageTypes = customStageFilterCreator.getSupportedStageTypes();
    assertThat(stageTypes).isNotEmpty();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageGetFilter() {
    CustomStageNode customStageNode = new CustomStageNode();
    PipelineFilter filter =
        customStageFilterCreator.getFilter(FilterCreationContext.builder().build(), customStageNode);
    assertThat(filter).isNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageFieldClass() {
    assertThat(customStageFilterCreator.getFieldClass()).isInstanceOf(Class.class);
  }
}
