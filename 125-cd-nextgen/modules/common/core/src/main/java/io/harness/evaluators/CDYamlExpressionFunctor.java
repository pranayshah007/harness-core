/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.evaluators;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ENVIRONMENT;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.OutputExpressionConstants;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class CDYamlExpressionFunctor {
  // Root yaml map
  YamlField rootYamlField;
  // Given element to start the expression search from.
  String fqnPathToElement;
  List<YamlField> aliasYamlFields;

  public Object get(String expression) {
    if (EmptyPredicate.isEmpty(fqnPathToElement)) {
      throw new InvalidRequestException("FQN Path cannot be empty for yaml expression functor.");
    }

    Map<String, Map<String, Object>> fqnToValueMap = new HashMap<>();

    // Traverse the yaml.
    getYamlMap(rootYamlField, fqnToValueMap, new LinkedList<>());
    if (EmptyPredicate.isNotEmpty(aliasYamlFields)) {
      aliasYamlFields.forEach(field -> getYamlMap(field, fqnToValueMap, new LinkedList<>()));
    }
    List<String> givenFqnList = new ArrayList(Arrays.asList(fqnPathToElement.split("\\.")));

    // Get the current element
    Map<String, Object> currentElementMap;
    if (fqnToValueMap.containsKey(fqnPathToElement)) {
      currentElementMap = fqnToValueMap.get(fqnPathToElement);
    } else {
      givenFqnList.remove(givenFqnList.size() - 1);
      String parentElementPath = String.join(".", givenFqnList);
      if (fqnToValueMap.containsKey(parentElementPath)) {
        currentElementMap = fqnToValueMap.get(parentElementPath);
      } else {
        throw new InvalidRequestException(
            "FQN path to the element doesnt exist in the yaml. FQN Path given - " + fqnPathToElement);
      }
    }

    // Check child first
    if (currentElementMap.containsKey(expression)) {
      return currentElementMap.get(expression);
    }
    // Check current and parent match
    else {
      if (expression.equals(PIPELINE)) {
        return fqnToValueMap.get(PIPELINE);
      }
      if (expression.equals(SERVICE)) {
        return fqnToValueMap.get(SERVICE);
      }
      if (expression.equals(OutputExpressionConstants.ENVIRONMENT)) {
        return fqnToValueMap.get(ENVIRONMENT);
      }
      givenFqnList.remove(givenFqnList.size() - 1);
      while (EmptyPredicate.isNotEmpty(givenFqnList)) {
        Map<String, Object> parentMap = fqnToValueMap.get(String.join(".", givenFqnList));
        if (parentMap.containsKey(expression)) {
          return parentMap.get(expression);
        }
        givenFqnList.remove(givenFqnList.size() - 1);
      }
    }

    return null;
  }

  @VisibleForTesting
  Map<String, Object> getYamlMap(
      YamlField yamlField, Map<String, Map<String, Object>> fqnToValueMap, List<String> fqnList) {
    Map<String, Object> contextMap = new HashMap<>();
    Map<String, Object> valueMap = new HashMap<>();

    /*
     * If Node is of array format
     * Example :
     * stages:
     *   - stage:
     *   - stage:
     * SO here stages node is an array
     */

    // Add node to fqn path.
    fqnList.add(yamlField.getName());

    if (yamlField.getNode().isArray()) {
      valueMap = getValueFromArray(yamlField.getNode(), fqnToValueMap, fqnList);
    } else if (yamlField.getNode().isObject()) {
      valueMap = getValueFromObject(yamlField.getNode(), fqnToValueMap, fqnList);
    }

    if (EmptyPredicate.isNotEmpty(valueMap)) {
      fqnToValueMap.put(String.join(".", fqnList), valueMap);
      contextMap.put(yamlField.getName(), valueMap);
    }

    // Remove the last added nodeName.
    fqnList.remove(fqnList.size() - 1);

    return contextMap;
  }

  private Map<String, Object> getValueFromArray(
      YamlNode yamlNode, Map<String, Map<String, Object>> fqnToValueMap, List<String> fqnList) {
    Map<String, Object> contextMap = new HashMap<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      /*
       * For nodes such as variables where only value field is associated with name, key.
       */
      if (EmptyPredicate.isEmpty(arrayElement.getIdentifier())
          && EmptyPredicate.isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
        contextMap.put(arrayElement.getArrayUniqueIdentifier(), arrayElement.getField("value").getNode().asText());
      } else if (EmptyPredicate.isNotEmpty(arrayElement.getIdentifier())) {
        // Nodes having identifier to refer uniquely from the array.
        processArrayElementWithKey(arrayElement.getIdentifier(), arrayElement, fqnList, contextMap, fqnToValueMap);
      } else if (EmptyPredicate.isNotEmpty(arrayElement.getStringValue(YAMLFieldNameConstants.SERVICE_REF))) {
        // Nodes having serviceRef to refer uniquely from the array.
        processArrayElementWithKey(arrayElement.getStringValue(YAMLFieldNameConstants.SERVICE_REF), arrayElement,
            fqnList, contextMap, fqnToValueMap);
      } else if (EmptyPredicate.isNotEmpty(arrayElement.getStringValue(YAMLFieldNameConstants.ENVIRONMENT_REF))) {
        // Nodes having environmentRef to refer uniquely from the array.
        processArrayElementWithKey(arrayElement.getStringValue(YAMLFieldNameConstants.ENVIRONMENT_REF), arrayElement,
            fqnList, contextMap, fqnToValueMap);
      } else if (arrayElement.isObject()) {
        for (YamlField field : arrayElement.fields()) {
          // Nodes having identifier to refer uniquely from the array.
          if (EmptyPredicate.isNotEmpty(field.getNode().getIdentifier())) {
            processArrayElementWithKey(
                field.getNode().getIdentifier(), field.getNode(), fqnList, contextMap, fqnToValueMap);
          }
          // If the node is like parallel, a dummy node having another list.
          else if (field.getNode().isArray()) {
            contextMap.putAll(getValueFromArray(field.getNode(), fqnToValueMap, fqnList));
          }
        }
      }
    }
    return contextMap;
  }

  private void processArrayElementWithKey(String key, YamlNode arrayElement, List<String> fqnList,
      Map<String, Object> contextMap, Map<String, Map<String, Object>> fqnToValueMap) {
    fqnList.add(key);
    Map<String, Object> valueFromObject = getValueFromObject(arrayElement, fqnToValueMap, fqnList);
    fqnToValueMap.put(String.join(".", fqnList), valueFromObject);
    contextMap.put(key, valueFromObject);
    fqnList.remove(fqnList.size() - 1);
  }

  private Map<String, Object> getValueFromObject(
      YamlNode yamlNode, Map<String, Map<String, Object>> fqnToValueMap, List<String> fqnList) {
    Map<String, Object> contextMap = new HashMap<>();
    for (YamlField field : yamlNode.fields()) {
      if (field.getNode().getCurrJsonNode().isValueNode()) {
        contextMap.put(field.getName(), field.getNode().asText());
      } else if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(field.getNode().getCurrJsonNode())) {
        contextMap.put(field.getName(), getListOfPrimitiveTypes(field.getNode()));
      } else {
        contextMap.putAll(getYamlMap(field, fqnToValueMap, fqnList));
      }
    }
    return contextMap;
  }

  private List<Object> getListOfPrimitiveTypes(YamlNode yamlNode) {
    List<Object> childNodes = new LinkedList<>();
    for (YamlNode childNode : yamlNode.asArray()) {
      childNodes.add(childNode.asText());
    }
    return childNodes;
  }
}
