package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ElastigroupFixedInstancesTest extends CategoryTest {
  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void getTypeTest() {
    ElastigroupFixedInstances instance = ElastigroupFixedInstances.builder()
                                             .max(ParameterField.createValueField(10))
                                             .min(ParameterField.createValueField(1))
                                             .desired(ParameterField.createValueField(2))
                                             .build();

    assertThat(instance.getType()).isEqualTo(ElastigroupInstancesType.FIXED);
  }
}
