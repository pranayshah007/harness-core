/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.pms.merger.helpers.YamlRefreshHelper.refreshNodeFromSourceNode;
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
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class YamlRefreshHelperTest extends CategoryTest {
  private JsonNode convertYamlToJsonNode(String yaml) throws IOException {
    return YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
  }

  private String convertToYaml(JsonNode jsonNode) {
    if (jsonNode == null) {
      return "";
    }
    String yaml = YamlUtils.write(jsonNode).replaceFirst("---\n", "");
    // removing last \n from string to simplify test
    return yaml.substring(0, yaml.length() - 1);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshNodeFromSourceNode() throws IOException {
    // all true scenarios
    assertThat(refreshNodeFromSourceNode(null, null)).isNull();
    assertThat(refreshNodeFromSourceNode(null, convertYamlToJsonNode("field: abc"))).isNull();
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"abc\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: abc"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(abc,b,c)"))))
        .isEqualTo("field: \"abc\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo("field: \"abc\"");
    String yamlToValidate = "field:\n"
        + "- a\n"
        + "- b";
    String expectedYaml = "field:\n"
        + "- \"a\"\n"
        + "- \"b\"";
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo(expectedYaml);
    yamlToValidate = "field:\n"
        + "- a\n"
        + "- ab";
    expectedYaml = "field:\n"
        + "- \"a\"\n"
        + "- \"ab\"";
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo(expectedYaml);
    yamlToValidate = "field:\n"
        + "  identifier: id\n"
        + "  name: name";
    expectedYaml = "field:\n"
        + "  identifier: \"id\"\n"
        + "  name: \"name\"";
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo(expectedYaml);
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: a.allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"a.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: a.regex(a.*)"), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo("field: \"a.regex(a.*)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: \"yes\""),
                   convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)"))))
        .isEqualTo("field: \"yes\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: \"01\""),
                   convertYamlToJsonNode("field: <+input>.allowedValues(01, 2)"))))
        .isEqualTo("field: \"01\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)"),
                   convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"<+input>.allowedValues(yes, no)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: <+input>.regex(a.*)"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"<+input>.regex(a.*)\"");

    // all false scenarios
    assertThat(convertToYaml(refreshNodeFromSourceNode(null, convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"<+input>\"");
    assertThat(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>"), null)).isNull();
    assertThat(convertToYaml(
                   refreshNodeFromSourceNode(convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field2: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(
                   refreshNodeFromSourceNode(convertYamlToJsonNode("field: def"), convertYamlToJsonNode("field: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field1: abc"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"<+input>\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: \"<+input>.regex(b.*)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: <+input>"), convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: \"<+input>.regex(b.*)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: \"<+input>.regex(b.*)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: \"<+input>.regex(b.*)\"");
    yamlToValidate = "field:\n"
        + "  - a\n"
        + "  - ab";
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRefreshNodeFromSourceNodeWithUseFromStage() throws IOException {
    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"<+input>\"\n    serviceInputs: \"<+input>\"\n"),
            convertYamlToJsonNode(
                "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"<+input>\"\n    serviceInputs: \"<+input>\"\n"))))
        .isEqualTo(
            "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"<+input>\"\n    serviceInputs: \"<+input>\"");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode("type: Deployment\nspec:\n  service:\n    serviceRef: prod_service\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo(
            "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"prod_service\"\n    serviceInputs: \"<+input>\"");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr\n    serviceRef: two\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo(
            "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"two\"\n    serviceInputs:\n      serviceDefinition:\n        type: \"Kubernetes\"\n        spec:\n          variables:\n          - name: \"ghcgh\"\n            type: \"String\"\n            value: \"ewfrvgdbgr\"");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode("type: Deployment\nspec:\n  service:\n    useFromStage:\n      stage: s1\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo("type: \"Deployment\"\nspec:\n  service:\n    useFromStage:\n      stage: \"s1\"");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: fixedService\n    serviceInputs: <+input>\n"))))
        .isEqualTo(
            "type: \"Deployment\"\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: \"Kubernetes\"\n        spec:\n          variables:\n          - name: \"ghcgh\"\n            type: \"String\"\n            value: \"ewfrvgdbgr\"");
  }
}
