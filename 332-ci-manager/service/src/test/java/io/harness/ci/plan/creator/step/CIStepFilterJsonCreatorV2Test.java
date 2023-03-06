/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.step;

import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIStepFilterJsonCreatorV2Test {
  CIStepFilterJsonCreatorV2 ciStepFilterJsonCreatorV2 = new CIStepFilterJsonCreatorV2();

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepK8() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().value("image").build())
                             .build())
            .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode);

    RunStepNode runStepNode1 =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().expressionValue("<+matrix.image>").build())
                             .build())
            .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode1);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepK8Exception() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("KubernetesDirect"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode =
        RunStepNode.builder()
            .runStepInfo(RunStepInfo.builder()
                             .connectorRef(ParameterField.<String>builder().value("connector").build())
                             .image(ParameterField.<String>builder().build())
                             .build())
            .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2.validateStep(context, runStepNode))
        .isInstanceOf(InvalidYamlException.class);

    RunStepNode runStepNode1 = RunStepNode.builder()
                                   .runStepInfo(RunStepInfo.builder()
                                                    .connectorRef(ParameterField.<String>builder().build())
                                                    .image(ParameterField.<String>builder().value("image").build())
                                                    .build())
                                   .build();

    assertThatThrownBy(() -> ciStepFilterJsonCreatorV2.validateStep(context, runStepNode1))
        .isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateRunStepVMImageNull() {
    YamlNode yamlNode = new YamlNode(getRunStepElementConfigAsJsonNode("VM"));
    YamlField yamlField = new YamlField("Command", yamlNode);
    FilterCreationContext context = FilterCreationContext.builder().currentField(yamlField).build();
    RunStepNode runStepNode = RunStepNode.builder()
                                  .runStepInfo(RunStepInfo.builder()
                                                   .connectorRef(ParameterField.<String>builder().build())
                                                   .image(ParameterField.<String>builder().build())
                                                   .build())
                                  .build();

    ciStepFilterJsonCreatorV2.validateStep(context, runStepNode);
  }

  private static JsonNode getRunStepElementConfigAsJsonNode(String infra) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "identifier");
    stepElementConfig.put("name", "name");

    ObjectNode infraNode = mapper.createObjectNode();
    infraNode.put("type", infra);
    stepElementConfig.set("infrastructure", infraNode);

    return stepElementConfig;
  }
}
