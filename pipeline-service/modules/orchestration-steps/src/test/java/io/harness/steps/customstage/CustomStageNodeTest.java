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
