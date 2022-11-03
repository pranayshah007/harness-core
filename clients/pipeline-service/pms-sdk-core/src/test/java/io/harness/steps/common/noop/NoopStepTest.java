package io.harness.steps.common.noop;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NoopStepTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSync() {
    NoopStep noopStep = new NoopStep();
    StepResponse stepResponse = noopStep.executeSync(null, null, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}