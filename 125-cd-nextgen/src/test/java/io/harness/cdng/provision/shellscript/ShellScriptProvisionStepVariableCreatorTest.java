/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.variables.StepVariableCreatorTestUtils;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ShellScriptProvisionStepVariableCreatorTest extends CategoryTest {
  private final ShellScriptProvisionStepVariableCreator variableCreator = new ShellScriptProvisionStepVariableCreator();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(variableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.SHELL_SCRIPT_PROVISION));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(variableCreator.getFieldClass()).isEqualTo(ShellScriptProvisionStepNode.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2ShellScripProvisionInlineScript() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithShellScriptProvisionStepInlineScript.json", variableCreator,
        ShellScriptProvisionStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.test.spec.execution.steps.shell1.name",
            "pipeline.stages.test.spec.execution.steps.shell1.description",
            "pipeline.stages.test.spec.execution.steps.shell1.timeout",
            "pipeline.stages.test.spec.execution.steps.shell1.spec.delegateSelectors",
            "pipeline.stages.test.spec.execution.steps.shell1.spec.source.spec.script");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2ShellScripProvisionFileStoreScriptAndEnvVariables() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithShellScriptProvisionStepFileStoreScript.json", variableCreator,
        ShellScriptProvisionStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.test.spec.execution.steps.shell2.name",
            "pipeline.stages.test.spec.execution.steps.shell2.description",
            "pipeline.stages.test.spec.execution.steps.shell2.timeout",
            "pipeline.stages.test.spec.execution.steps.shell2.spec.delegateSelectors",
            "pipeline.stages.test.spec.execution.steps.shell2.spec.source.spec.file",
            "pipeline.stages.test.spec.execution.steps.shell2.spec.environmentVariables.testvariable");
  }
}
