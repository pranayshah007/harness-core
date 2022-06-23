/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.evaluators.YamlExpressionEvaluator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlExpressionResolveHelperTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void resolveExpressionForArrayElement() throws IOException {
    YamlExpressionResolveHelper yamlExpressionResolveHelper = new YamlExpressionResolveHelper();
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";

    YamlExpressionEvaluator yamlExpressionEvaluator = new YamlExpressionEvaluator(arrayTypeString, "pipeline.name");
    YamlField yamlField = YamlUtils.readTree(arrayTypeString);
    YamlNode parentNode = yamlField.getNode().getField("pipeline").getNode().getField("delegateSelector").getNode();
    ArrayNode parentArrayNode = (ArrayNode) parentNode.getCurrJsonNode();

    // case1: value passed is not expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 0, parentArrayNode.get(0).textValue(), yamlExpressionEvaluator);
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");

    // case2: value passed is expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 1, parentArrayNode.get(1).textValue(), yamlExpressionEvaluator);
    assertThat(parentArrayNode.get(1).textValue()).isEqualTo("pipelineName");
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");
  }
}
