/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expression;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.ShellScriptBaseDTO;
import io.harness.engine.expressions.ShellScriptYamlDTO;
import io.harness.engine.expressions.ShellScriptYamlExpressionEvaluator;
import io.harness.ng.core.template.TemplateEntityConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
@OwnedBy(PL)
public class ShellScriptYamlExpressionEvaluatorTest extends CategoryTest {
  private String yaml = "---\n"
      + "script:\n"
      + "  type: Script\n"
      + "  name: Script\n"
      + "  identifier: Script\n"
      + "  spec:\n"
      + "    timeout: 10\n"
      + "    shell: Bash\n"
      + "    onDelegate: 'true'\n"
      + "    source:\n"
      + "      __uuid: neREpx2mmQ14G7y3pKAQzW\n"
      + "      type: Inline\n"
      + "      spec:\n"
      + "        script: echo 1 echo <+script.spec.timeout>_<+script.spec.source.type> and <+script.spec.environmentVariables.e1>\n"
      + "    environmentVariables:\n"
      + "    - name: e1\n"
      + "      value: <+script.spec.environmentVariables.e2>\n"
      + "      type: String\n"
      + "      __uuid: 4G7y3pKAQzW-neREpx2mmQ1\n"
      + "    - name: e2\n"
      + "      value: dummyValue2\n"
      + "      type: String\n"
      + "      __uuid: 4G7y3pKAQzW-neREpx2mmQ2\n"
      + "    outputVariables:\n"
      + "    - name: o1\n"
      + "      value: v1\n"
      + "      type: String\n"
      + "      __uuid: 4G7y3pKAQzW-neREpx2mmQ3\n"
      + "    __uuid: M6HHtApvRa6cscRUnJ5NqA\n"
      + "  __uuid: xtkQAaoNRkCgtI5mU8KnEQ\n";

  @Test
  @Category(UnitTests.class)
  @Owner(developers = SHREYAS)
  public void testResolve() throws Exception {
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(yaml);
    ShellScriptBaseDTO shellScriptBaseDTO = YamlUtils.read(yaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    // Tests for single value resolution
    assertThat(shellScriptBaseDTO.getType()).isEqualTo(TemplateEntityConstants.SCRIPT);
    // Tests for resolution of Hierarchical resolution
    // ie resolve(expression 1) where expression 1 needs resolution of expression 2 or more levels
    assertThat(shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue())
        .isEqualTo("echo 1 echo 10_Inline and dummyValue2");
    // TODO: Tests for secret resolution
  }
}
