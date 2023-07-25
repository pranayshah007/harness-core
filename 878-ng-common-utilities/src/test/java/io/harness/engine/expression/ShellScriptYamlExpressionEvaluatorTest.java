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
  /*
   Yaml string is broken in 3 parts for easy testing purpose.
   1- String before script.
   2- Script
   3- String after script.
   Any new test case can use the getYaml function passing the script as a string to create a simple yaml string.
   Other use cases might need new yaml string creation as well but the above acts as a simple example for most cases.
   */
  private String yamlBeforeScript = "---\n"
      + "connector:\n"
      + "  type: SecretManager\n"
      + "  name: Script\n"
      + "  identifier: CustomSecretManagerIdentifier\n"
      + "  spec:\n"
      + "    shell: Bash\n"
      + "    source:\n"
      + "      spec:\n";

  private String yamlAfterScript = "\n"
      + "        type: Inline\n"
      + "    environmentVariables:\n"
      + "    - name: e1\n"
      + "      value: <+connector.spec.environmentVariables.e2>\n"
      + "      type: String\n"
      + "      __uuid: 4G7y3pKAQzW-neREpx2mmQ1\n"
      + "    - name: e2\n"
      + "      value: dummyValue2\n"
      + "      type: String\n"
      + "      __uuid: 4G7y3pKAQzW-neREpx2mmQ2\n"
      + "    - name: e3\n"
      + "      value: SecretManager\n"
      + "      type: String\n"
      + "    outputVariables:\n"
      + "    - name: o1\n"
      + "      value: v1\n"
      + "      type: String\n";
  StringBuilder scriptStringBuilder = new StringBuilder("        script: ");

  public String getYaml(String script) {
    return yamlBeforeScript + scriptStringBuilder.append(script).toString() + yamlAfterScript;
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testResolveSingleValue() throws Exception {
    String yaml = getYaml("AnyScript");
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(yaml, 7, null);
    ShellScriptBaseDTO shellScriptBaseDTO = YamlUtils.read(yaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    // Tests for single value resolution
    assertThat(shellScriptBaseDTO.getType()).isEqualTo(TemplateEntityConstants.SECRET_MANAGER);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  // Tests for resolution of Hierarchical resolution
  // ie resolve(expression 1) where expression 1 needs resolution of expression 2 or more levels
  public void testResolveHierarichalExpression() throws Exception {
    String script =
        "echo 1 echo <+spec.source.spec.type> and <+spec.environmentVariables.e1> and <+secretManager.source.spec.type>";
    String yaml = getYaml(script);
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(yaml, 7, null);
    ShellScriptBaseDTO shellScriptBaseDTO = YamlUtils.read(yaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    assertThat(shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue())
        .isEqualTo("echo 1 echo Inline and dummyValue2 and Inline");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  // Test resolving secret evaluation
  public void testResolveSecretExpression() throws Exception {
    String script = "echo <+secrets.getValue(\"Token\")>";
    String yaml = getYaml(script);
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(yaml, 7, null);
    ShellScriptBaseDTO shellScriptBaseDTO = YamlUtils.read(yaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);

    assertThat(shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue())
        .isEqualTo("echo ${ngSecretManager.obtain(\"Token\", 7)}");
  }
}
