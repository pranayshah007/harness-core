package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStep;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TasSwapRoutesStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy @InjectMocks private io.harness.cdng.tas.TasSwapRoutesStep tasSwapRoutesStep;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    Class<StepElementParameters> stepElementParametersClass = tasSwapRoutesStep.getStepParametersClass();
    assertThat(stepElementParametersClass).isEqualTo(StepElementParameters.class);
  }
}
