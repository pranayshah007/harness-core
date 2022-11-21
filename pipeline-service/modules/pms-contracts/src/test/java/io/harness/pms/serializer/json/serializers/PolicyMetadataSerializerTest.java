package io.harness.pms.serializer.json.serializers;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import io.harness.CategoryTest;
import io.harness.rule.Owner;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class PolicyMetadataSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  public void testConstructor() {
    PolicyMetadataSerializerTest policyMetadataSerializerTest = new PolicyMetadataSerializerTest();
    AssertionsForClassTypes.assertThat(policyMetadataSerializerTest).isNotNull();
  }
}
