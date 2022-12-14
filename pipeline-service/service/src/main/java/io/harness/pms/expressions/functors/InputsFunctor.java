/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingValue;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 This Functor is invoked when an expression starts with inputs.
 Should return a map of input values based on the type of input.
 *
 */

@OwnedBy(PIPELINE)
@Slf4j
public class InputsFunctor implements LateBindingValue {
  private final String inputs;
  private final String pipelineYamlV1;

  public InputsFunctor(String inputs, String pipelineYamlV1) {
    this.inputs = inputs;
    this.pipelineYamlV1 = pipelineYamlV1;
  }

  @Override
  public Object bind() {
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(pipelineYamlV1).getNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml");
    }
    JsonNode versionField = pipelineNode.getField("version").getNode().getCurrJsonNode();
    if (versionField == null || PipelineVersion.V0.equals(versionField.asText())) {
      log.warn("InputsFunctor is invoked for the PipelineYaml Version {}.", PipelineVersion.V0);
      return Collections.emptyMap();
    }

    YamlNode inputsYamlNode = pipelineNode.getField("inputs").getNode();

    Map<String, Object> inputsMap = JsonUtils.asMap(inputs);
    for (Map.Entry<String, Object> entry : inputsMap.entrySet()) {
      String key = entry.getKey();
      if (inputsYamlNode.getField(key) == null || inputsYamlNode.getField(key).getNode().getField("type") == null) {
        continue;
      }
      if (inputsYamlNode.getField(key).getNode().getField("type").getNode().getCurrJsonNode().asText().equals(
              "secret")) {
        entry.setValue(NGVariablesUtils.fetchSecretExpression((String) entry.getValue()));
      }
    }
    return inputsMap;
  }
}
