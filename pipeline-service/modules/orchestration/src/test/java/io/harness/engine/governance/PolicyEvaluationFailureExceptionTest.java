package io.harness.engine.governance;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PolicyEvaluationFailureExceptionTest extends CategoryTest {
  GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().build();
  PolicyEvaluationFailureException policyEvaluationFailureException =
      new PolicyEvaluationFailureException("message", governanceMetadata, "yaml");

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetGovernanceMetadata() {
    assertThat(policyEvaluationFailureException.getGovernanceMetadata()).isInstanceOf(GovernanceMetadata.class);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetYaml() {
    assertThat(policyEvaluationFailureException.getYaml()).isInstanceOf(String.class);
  }
}