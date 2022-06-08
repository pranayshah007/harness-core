package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;

import io.harness.category.element.UnitTests;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8InitializeTaskParamsBuilderTest extends CIExecutionTestBase {
  @Inject private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getK8InitializeTaskParams() {}
}