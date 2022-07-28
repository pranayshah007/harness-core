package io.harness.steps.customstage;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomStageConfigTest {
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStageConfig() {
    String uuid = "temp";
    CustomStageConfig customStageConfig = CustomStageConfig.builder().build();
    customStageConfig.setUuid(uuid);
    assertThat(uuid).isEqualTo(customStageConfig.getUuid());
  }
}
