package io.harness.pms.serializer.json.serializers;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SkipInfoSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testConstructor() {
    SkipInfoSerializer skipInfoSerializer = new SkipInfoSerializer();
    AssertionsForClassTypes.assertThat(skipInfoSerializer).isNotNull();
  }
}
