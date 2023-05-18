/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.expressions.NGTriggerExpressionEvaluatorProvider;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
// TODO: Merge with having same expression support instead of null return for unresolved expressions in
// engineExpressionEvaluator
public class YamlExpressionResolveHelper {
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private NodeExecutionService nodeExecutionService;

  @Inject private NGTriggerExpressionEvaluatorProvider ngTriggerExpressionEvaluatorProvider;

  public String resolveExpressionsInYaml(
      String yamlString, String planExecutionId, ResolveInputYamlType resolveInputYamlType) {
    Optional<NodeExecution> nodeExecution = nodeExecutionService.getPipelineNodeExecutionWithProjections(
        planExecutionId, NodeProjectionUtils.withAmbianceAndStatus);

    if (nodeExecution.isPresent()) {
      EngineExpressionEvaluator engineExpressionEvaluator;
      if (resolveInputYamlType.equals(ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS)) {
        engineExpressionEvaluator = ngTriggerExpressionEvaluatorProvider.get(nodeExecution.get().getAmbiance());
      } else {
        engineExpressionEvaluator =
            pmsEngineExpressionService.prepareExpressionEvaluator(nodeExecution.get().getAmbiance());
      }
      try {
        YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlString));
        YamlField pipelineYamlField = yamlField.getNode().getField("pipeline");

        resolveExpressions(Preconditions.checkNotNull(pipelineYamlField, "YAML does not have pipeline object"),
            engineExpressionEvaluator);

        JsonNode resolvedYamlNode = yamlField.getNode().getCurrJsonNode();
        YamlUtils.removeUuid(resolvedYamlNode);
        return YamlPipelineUtils.writeString(resolvedYamlNode).replace("---\n", "");

      } catch (IOException ex) {
        log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
        throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      }
    }

    throw new InvalidRequestException("Invalid request : No execution details found");
  }

  private void resolveExpressions(YamlField field, EngineExpressionEvaluator engineExpressionEvaluator) {
    if (field.getNode().isObject()) {
      resolveExpressionsInObject(field.getNode(), engineExpressionEvaluator);
    } else if (field.getNode().isArray()) {
      resolveExpressionsInArray(field.getNode(), engineExpressionEvaluator);
    }
  }

  private void resolveExpressionsInObject(YamlNode parentNode, EngineExpressionEvaluator engineExpressionEvaluator) {
    for (YamlField childYamlField : parentNode.fields()) {
      if (childYamlField.getNode().getCurrJsonNode().isValueNode()) {
        resolveExpressionInValueNode(parentNode, childYamlField.getName(),
            childYamlField.getNode().getCurrJsonNode().asText(), engineExpressionEvaluator);
      } else if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(parentNode.getCurrJsonNode())) {
        continue;
      } else {
        resolveExpressions(childYamlField, engineExpressionEvaluator);
      }
    }
  }

  private void resolveExpressionsInArray(YamlNode arrayNode, EngineExpressionEvaluator engineExpressionEvaluator) {
    int childIndex = 0;
    for (YamlNode arrayElement : arrayNode.asArray()) {
      if (arrayElement.isObject()) {
        resolveExpressionsInObject(arrayElement, engineExpressionEvaluator);
      } else if (arrayElement.isArray()) {
        resolveExpressionsInArray(arrayElement, engineExpressionEvaluator);
      } else if (arrayElement.getCurrJsonNode().isValueNode()) {
        resolveExpressionForArrayElement(
            arrayNode, childIndex, arrayElement.getCurrJsonNode().asText(), engineExpressionEvaluator);
      }
      childIndex = childIndex + 1;
    }
  }

  public void resolveExpressionForArrayElement(
      YamlNode parentNode, int childIndex, String childValue, EngineExpressionEvaluator engineExpressionEvaluator) {
    ArrayNode object = (ArrayNode) parentNode.getCurrJsonNode();
    if (EngineExpressionEvaluator.hasExpressions(childValue)) {
      String resolvedExpression = engineExpressionEvaluator.renderExpression(
          childValue, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      // Update node value only if expression was successfully resolved
      if (isExpressionResolved(resolvedExpression) && !resolvedExpression.equals(childValue)) {
        object.set(childIndex, resolvedExpression);
      }
    }
  }

  private void resolveExpressionInValueNode(
      YamlNode parentNode, String childName, String childValue, EngineExpressionEvaluator engineExpressionEvaluator) {
    ObjectNode objectNode = (ObjectNode) parentNode.getCurrJsonNode();
    if (NGExpressionUtils.matchesExecutionInputPattern(childValue)) {
      return;
    }
    if (EngineExpressionEvaluator.hasExpressions(childValue)) {
      String resolvedExpression = engineExpressionEvaluator.renderExpression(
          childValue, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      // Update node value only if expression was successfully resolved
      if (isExpressionResolved(resolvedExpression) && !resolvedExpression.equals(childValue)) {
        objectNode.put(childName, resolvedExpression);
      }
    }
  }

  private boolean isExpressionResolved(String resolvedValue) {
    return resolvedValue != null && !resolvedValue.equals("null");
  }
}
