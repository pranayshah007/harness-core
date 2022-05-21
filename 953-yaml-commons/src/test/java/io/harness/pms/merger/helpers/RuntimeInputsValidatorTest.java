/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.pms.merger.helpers.RuntimeInputsValidator.areInputsValidAgainstSourceNode;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
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
  }
}
