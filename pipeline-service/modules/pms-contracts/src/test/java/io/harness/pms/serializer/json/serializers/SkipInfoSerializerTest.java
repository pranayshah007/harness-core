package io.harness.pms.serializer.json.serializers;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.harness.CategoryTest;
import io.harness.rule.Owner;

import org.junit.Test;

public class SkipInfoSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  public void testConstructor() {
    SkipInfoSerializer skipInfoSerializer = new SkipInfoSerializer();
    assertThat(skipInfoSerializer).isNotNull();
  }
}
