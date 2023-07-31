/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.pms.merger.helpers.RuntimeInputsValidator.areInputsValidAgainstSourceNode;
import static io.harness.pms.merger.helpers.RuntimeInputsValidator.validateInputsAgainstSourceNode;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class RuntimeInputsValidatorTest extends CategoryTest {
  private JsonNode convertYamlToJsonNode(String yaml) throws IOException {
    return YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testAreInputsValidAgainstSourceNode() throws IOException {
    // all true scenarios
    assertThat(areInputsValidAgainstSourceNode(null, null)).isTrue();
    assertThat(areInputsValidAgainstSourceNode(null, convertYamlToJsonNode("field: abc"))).isTrue();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: abc"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(abc,b,c)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(a.*)")))
        .isTrue();
    String yamlToValidate = "field:\n"
        + "  - a\n"
        + "  - b";
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isTrue();
    yamlToValidate = "field:\n"
        + "  - a\n"
        + "  - ab";
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>.regex(a.*)")))
        .isTrue();
    yamlToValidate = "field:\n"
        + "  identifier: id\n"
        + "  name: name";
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: a.allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: a.regex(a.*)"), convertYamlToJsonNode("field: <+input>.regex(a.*)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: \"yes\""),
                   convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: \"01\""),
                   convertYamlToJsonNode("field: <+input>.allowedValues(01, 2)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: yes.allowedValues(yes, no)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)"),
                   convertYamlToJsonNode("field: <+input>")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: <+input>.regex(a.*)"), convertYamlToJsonNode("field: <+input>")))
        .isTrue();

    // all false scenarios
    assertThat(areInputsValidAgainstSourceNode(null, convertYamlToJsonNode("field: <+input>"))).isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>"), null)).isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: abc")))
        .isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field2: abc")))
        .isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: def"), convertYamlToJsonNode("field: abc")))
        .isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field1: abc"), convertYamlToJsonNode("field: <+input>")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(a).allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.default(b).allowedValues(a,b,c)")))
        .isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(true).allowedValues(a,b,c)"),
            convertYamlToJsonNode("field: <+input>.default(true).allowedValues(a,b,c)")))
        .isTrue();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(123).allowedValues(a,b,c)"),
            convertYamlToJsonNode("field: <+input>.default(123).allowedValues(a,b,c)")))
        .isTrue();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(true).allowedValues(a,b,c)"),
            convertYamlToJsonNode("field: <+input>.default(false).allowedValues(a,b,c)")))
        .isFalse();
    assertThat(
        areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(123).allowedValues(a,b,c)"),
            convertYamlToJsonNode("field: <+input>.default(234).allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.default(a).allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.default(b).allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: <+input>"), convertYamlToJsonNode("field: <+input>.default(b)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: <+input>.default(b)"), convertYamlToJsonNode("field: <+input>")))
        .isTrue();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(b.*)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: <+input>"), convertYamlToJsonNode("field: <+input>.regex(b.*)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)")))
        .isFalse();
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)")))
        .isFalse();
    yamlToValidate = "field:\n"
        + "  - a\n"
        + "  - ab";
    assertThat(areInputsValidAgainstSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)")))
        .isFalse();
    yamlToValidate = "field:\n"
        + "  a: <+input>";
    assertThat(areInputsValidAgainstSourceNode(
                   convertYamlToJsonNode("field: <+input>"), convertYamlToJsonNode(yamlToValidate)))
        .isFalse();
    assertThat(validateInputsAgainstSourceNode(
                   "field: \"foo\" \nnewField: \"abc\"", "field: <+input>", Set.of(), Set.of("newField")))
        .isTrue();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidateInputsWithExtraKeys() throws IOException {
    String yamlToValidate = "field: abc";
    String sourceEntityYaml = "field: <+input>";
    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, new HashSet<>(), new HashSet<>()))
        .isTrue();

    // artifacts.primary.sources node has no runtime inputs hence not present in sourceNode
    yamlToValidate = "serviceInputs:\n"
        + "  serviceDefinition:\n"
        + "    type: \"Ssh\"\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: \"Test\"\n"
        + "          sources: \"<+input>\"";
    sourceEntityYaml = "serviceInputs:\n"
        + "  serviceDefinition:\n"
        + "    type: \"Ssh\"\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: \"<+input>\"";

    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, new HashSet<>(), new HashSet<>()))
        .isFalse();

    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, new HashSet<>(),
                   new HashSet<>(Collections.singletonList("artifacts.primary.sources"))))
        .isTrue();

    // when primary artifact ref is fixed in the pipeline yaml
    yamlToValidate = "serviceInputs:\n"
        + "  serviceDefinition:\n"
        + "    type: \"Ssh\"\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: \"Test\"";
    sourceEntityYaml = "serviceInputs:\n"
        + "  serviceDefinition:\n"
        + "    type: \"Ssh\"\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: \"<+input>\"\n";

    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, new HashSet<>(),
                   new HashSet<>(Collections.singletonList("artifacts.primary.sources"))))
        .isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateInputsWithUseFromStage() throws IOException {
    Set<String> KEYS_TO_IGNORE = Set.of("service.serviceInputs", "environment.environmentInputs",
        "environment.serviceOverrideInputs", "codebase.repoName");
    String yamlToValidate = "service:\n"
        + "  useFromStage:\n"
        + "    stage: s1";

    String sourceEntityYaml = "service:\n"
        + "  serviceRef: \"<+input>\"\n"
        + "  serviceInputs: \"<+input>\"";

    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, KEYS_TO_IGNORE, new HashSet<>()))
        .isTrue();

    yamlToValidate = "service:\n"
        + "  serviceRef: \"service-prod\"";
    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, KEYS_TO_IGNORE, new HashSet<>()))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateEnvironmentInputsWithUseFromStage() throws IOException {
    Set<String> KEYS_TO_IGNORE = Set.of("service.serviceInputs", "environment.environmentInputs",
        "environment.serviceOverrideInputs", "codebase.repoName");
    String yamlToValidate = "environment:\n"
        + "  useFromStage:\n"
        + "    stage: s1";

    String sourceEntityYaml = "environment:\n"
        + "  environmentRef: \"<+input>\"\n"
        + "  environmentInputs: \"<+input>\"";

    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, KEYS_TO_IGNORE, new HashSet<>()))
        .isTrue();

    yamlToValidate = "environment:\n"
        + "  environmentRef: \"env-prod\"";
    assertThat(validateInputsAgainstSourceNode(yamlToValidate, sourceEntityYaml, KEYS_TO_IGNORE, new HashSet<>()))
        .isTrue();
  }
}
