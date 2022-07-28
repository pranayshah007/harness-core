package io.harness.steps.customstage;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
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
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageFilterCreator() {
    //        CustomStageFilterCreator customStageFilterCreator = new CustomStageFilterCreator();
    Set<String> stageTypes = customStageFilterCreator.getSupportedStageTypes();
    assertThat(stageTypes).isNotEmpty();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageGetFilter() {
    CustomStageNode customStageNode = new CustomStageNode();
    PipelineFilter filter =
        customStageFilterCreator.getFilter(FilterCreationContext.builder().build(), customStageNode);
    assertThat(filter).isNull();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageFieldClass() {
    assertThat(customStageFilterCreator.getFieldClass()).isInstanceOf(java.lang.Class.class);
  }
}
