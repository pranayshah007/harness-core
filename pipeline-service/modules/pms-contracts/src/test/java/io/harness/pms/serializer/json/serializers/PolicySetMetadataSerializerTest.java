package io.harness.pms.serializer.json.serializers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.rule.Owner;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

@OwnedBy(PIPELINE)
public class PolicySetMetadataSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  public void testConstructor() {
    PolicySetMetadataSerializer policySetMetadataSerializer = new PolicySetMetadataSerializer();
    AssertionsForClassTypes.assertThat(policySetMetadataSerializer).isNotNull();
  }
}
