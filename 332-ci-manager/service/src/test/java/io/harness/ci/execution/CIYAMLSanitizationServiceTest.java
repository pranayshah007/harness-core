package io.harness.ci.execution;

import static io.harness.rule.OwnerRule.HEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.integrationstage.K8InitializeTaskUtilsHelper;
import io.harness.ci.validation.CIYAMLSanitizationService;
import io.harness.ci.validation.CIYAMLSanitizationServiceImpl;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CIYAMLSanitizationServiceTest extends CIExecutionTestBase {
  private CIYAMLSanitizationService ciyamlSanitizationService;

  @Before
  public void setUp() {
    ciyamlSanitizationService = new CIYAMLSanitizationServiceImpl();
  }
  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testValidRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionWrapperConfigList();
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (Exception e) {
    }

    assertThat(validate).isEqualTo(true);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testMaliciousRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionMinerWrapperConfigList();
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (Exception e) {
    }

    assertThat(validate).isEqualTo(false);
  }
}
