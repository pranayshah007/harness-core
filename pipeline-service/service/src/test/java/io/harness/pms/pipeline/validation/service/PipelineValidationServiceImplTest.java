/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.service;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.validator.InvalidYamlException;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class PipelineValidationServiceImplTest extends PipelineServiceTestBase {
  @InjectMocks PipelineValidationServiceImpl pipelineValidationServiceImpl;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testValidateExistenceOfPipelineWithEmptyYaml() {
    String pipelineYaml = "";
    assertThatThrownBy(() -> pipelineValidationServiceImpl.checkIfRootNodeIsPipeline(pipelineYaml))
        .isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCheckIfRootNodeIsPipeline() {
    String pipelineYaml = "template:\n"
        + "  name: remote_p1\n"
        + "  identifier: remote_p1\n"
        + "  versionLabel: v1\n"
        + "  type: Step\n"
        + "  projectIdentifier: new_git_exp_proj\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  spec:\n"
        + "    timeout: 10m\n"
        + "    type: ShellScript\n"
        + "    spec:\n"
        + "      shell: Bash\n"
        + "      onDelegate: true\n"
        + "      source:\n"
        + "        type: Inline\n"
        + "        spec:\n"
        + "          script: echo \"Hello\"\n"
        + "      environmentVariables: []\n"
        + "      outputVariables: []\n";
    assertThatThrownBy(() -> pipelineValidationServiceImpl.checkIfRootNodeIsPipeline(pipelineYaml))
        .isInstanceOf(InvalidYamlException.class);
  }
}
