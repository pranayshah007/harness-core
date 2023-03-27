/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class ContainerPlanCreaterUtils {
  public static List<YamlField> getStepYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }

  public static ExecutionWrapperConfig getExecutionConfig(YamlField step) {
    return getExecutionConfig(step, true);
  }

  public static ExecutionWrapperConfig getExecutionConfig(YamlField step, boolean isCI) {
    if (step.getType() == null) {
      throw new InvalidRequestException(String.format("Type cannot be null for %s Step", isCI ? "CI" : "Container"));
    }
    switch (step.getType()) {
      case YAMLFieldNameConstants.PARALLEL:
        List<YamlField> parallelNodes = getStepYamlFields(
            step.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STEPS));
        ParallelStepElementConfig parallelStepElementConfig =
            ParallelStepElementConfig.builder()
                .sections(parallelNodes.stream()
                              .map(ContainerPlanCreaterUtils::getExecutionConfig)
                              .collect(Collectors.toList()))
                .build();
        return ExecutionWrapperConfig.builder()
            .uuid(step.getUuid())
            .parallel(getJsonNode(parallelStepElementConfig, isCI))
            .build();
      case YAMLFieldNameConstants.GROUP:
        List<YamlField> groupNodes = getStepYamlFields(
            step.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STEPS));
        StepGroupElementConfig stepGroupElementConfig =
            StepGroupElementConfig.builder()
                .identifier(IdentifierGeneratorUtils.getId(step.getNodeName()))
                .name(step.getNodeName())
                .steps(
                    groupNodes.stream().map(ContainerPlanCreaterUtils::getExecutionConfig).collect(Collectors.toList()))
                .build();
        return ExecutionWrapperConfig.builder()
            .uuid(step.getUuid())
            .stepGroup(getJsonNode(stepGroupElementConfig, isCI))
            .build();
      default:
        JsonNode node = step.getNode().getCurrJsonNode();
        if (node != null && node.isObject() && node.get(YAMLFieldNameConstants.NAME) != null) {
          ObjectNode objectNode = (ObjectNode) node;
          objectNode.put(YAMLFieldNameConstants.IDENTIFIER,
              IdentifierGeneratorUtils.getId(objectNode.get(YAMLFieldNameConstants.NAME).asText()));
        }
        return ExecutionWrapperConfig.builder().uuid(step.getUuid()).step(step.getNode().getCurrJsonNode()).build();
    }
  }

  private static JsonNode getJsonNode(Object object, boolean isCI) {
    try {
      String json = JsonPipelineUtils.writeJsonString(object);
      return JsonPipelineUtils.getMapper().readTree(json);
    } catch (IOException e) {
      if (isCI) {
        throw new CIStageExecutionException("Failed to serialise node", e);
      }
      throw new ContainerStepExecutionException("Failed to serialise node", e);
    }
  }
}
