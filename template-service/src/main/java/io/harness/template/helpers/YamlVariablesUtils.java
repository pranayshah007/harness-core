/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.variables.VariableMergeServiceResponse.VariableResponseMapValue;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;
import lombok.SneakyThrows;

public class YamlVariablesUtils {
  @SneakyThrows
  public static VariableMergeServiceResponse getVariablesFromYaml(String yaml, TemplateEntityType templateEntityType) {
    YamlField uuidInjectedYaml = YamlUtils.injectUuidWithLeafUuid(yaml);
    Map<String, YamlProperties> uuIdToFQNMap =
        getUuidToFQNMapForLeafNodes(uuidInjectedYaml.getNode().getField(templateEntityType.getRootYamlName()));
    return VariableMergeServiceResponse.builder()
        .yaml(YamlUtils.writeYamlString(uuidInjectedYaml))
        .metadataMap(uuIdToFQNMap.entrySet().stream().collect(Collectors.toMap(entry
            -> entry.getKey(),
            entry -> VariableResponseMapValue.builder().yamlProperties(entry.getValue()).build())))
        .build();
  }

  private static Map<String, YamlProperties> getUuidToFQNMapForLeafNodes(YamlField uuidInjectedYaml) {
    Map<String, YamlProperties> uuidToFQNMap = new HashMap<>();
    Stack<String> path = new Stack<>();
    populateUuidToFQNMapForLeafNodes(uuidToFQNMap, uuidInjectedYaml, path);
    return uuidToFQNMap;
  }

  private static void populateUuidToFQNMapForLeafNodes(
      Map<String, YamlProperties> uuidToFQNMap, YamlField yamlField, Stack<String> path) {
    path.push(yamlField.getName());
    if (yamlField.getNode().isArray()) {
      populateUuidToFQNMapForLeafNodesInArray(uuidToFQNMap, yamlField.getNode(), path);
    } else if (yamlField.getNode().isObject()) {
      populateUuidToFQNMapForLeafNodesInObject(uuidToFQNMap, yamlField.getNode(), path);
    }
    path.pop();
  }

  private static void populateUuidToFQNMapForLeafNodesInObject(
      Map<String, YamlProperties> uuidToFQNMap, YamlNode yamlNode, Stack<String> path) {
    for (YamlField field : yamlNode.fields()) {
      if (field.getNode().getCurrJsonNode().isValueNode()) {
        if (Arrays.asList(YamlNode.IDENTIFIER_FIELD_NAME, YamlNode.UUID_FIELD_NAME, YamlNode.TYPE_FIELD_NAME)
                .contains(field.getName())) {
          continue;
        }
        uuidToFQNMap.put(field.getNode().asText(), getFQNFromPath(path, field.getName()));
      } else {
        populateUuidToFQNMapForLeafNodes(uuidToFQNMap, field, path);
      }
    }
  }

  private static void populateUuidToFQNMapForLeafNodesInArray(
      Map<String, YamlProperties> uuidToFQNMap, YamlNode yamlNode, Stack<String> path) {
    if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(yamlNode.getCurrJsonNode())) {
      return;
    }
    for (YamlNode arrayElement : yamlNode.asArray()) {
      /*
       * For nodes such as variables where only value field is associated with name, key.
       */
      if (EmptyPredicate.isEmpty(arrayElement.getIdentifier())
          && EmptyPredicate.isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
        uuidToFQNMap.put(arrayElement.getField("value").getNode().asText(),
            getFQNFromPath(path, arrayElement.getArrayUniqueIdentifier()));
      } else if (EmptyPredicate.isNotEmpty(arrayElement.getIdentifier())) {
        path.push(arrayElement.getIdentifier());
        populateUuidToFQNMapForLeafNodesInObject(uuidToFQNMap, arrayElement, path);
        path.pop();
      }
    }
  }

  private static YamlProperties getFQNFromPath(Stack<String> path, String fieldName) {
    StringBuilder fqnBuilder = new StringBuilder();
    path.stream().forEach(pathSring -> fqnBuilder.append(pathSring).append("."));
    fqnBuilder.append(fieldName);
    return YamlProperties.newBuilder()
        .setFqn(fqnBuilder.toString())
        .setVariableName(fieldName)
        .setVisible(true)
        .build();
  }
}
