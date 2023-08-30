/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YamlNode.PATH_SEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.YamlException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class YamlNodeUtils {
  public static final String FQN_SEP = "\\.";

  public void addToPath(YamlNode yamlNode, String path, JsonNode newNode) {
    if (isEmpty(path)) {
      return;
    }

    List<String> pathList = Arrays.asList(path.split(PATH_SEP));
    if (isEmpty(pathList)) {
      return;
    }

    JsonNode curr = yamlNode.getCurrJsonNode();
    for (String currName : pathList) {
      if (curr == null) {
        return;
      }

      if (currName.charAt(0) == '[') {
        if (!curr.isArray()) {
          throw new YamlException(String.format("Trying to use index path (%s) on non-array node", currName));
        }
        try {
          int idx = Integer.parseInt(currName.substring(1, currName.length() - 1));
          curr = curr.get(idx);
        } catch (Exception ex) {
          throw new YamlException(String.format("Incorrect index path (%s) on array node", currName));
        }
      } else {
        curr = curr.get(currName);
      }
    }
    if (curr.isArray()) {
      ArrayNode arrayNode = (ArrayNode) curr;
      arrayNode.add(newNode);
    } else {
      ObjectNode objectNode = (ObjectNode) curr;
      objectNode.setAll((ObjectNode) newNode);
    }
  }

  /**
   * This method returns yamlNode at the fqn provided, starting from the given root yamlNode
   * @param yamlNode - Starting point of yamlNode traversal.
   * @param fqn - dot separated path, where arrays are uniquely identified using identifiers or name instead of indexes.
   * @return yamlNode at the fqn path.
   */
  public YamlNode goToPathUsingFqn(YamlNode yamlNode, String fqn) {
    if (isEmpty(fqn)) {
      return yamlNode;
    }

    List<String> pathList = Arrays.asList(fqn.split(FQN_SEP));
    if (isEmpty(pathList)) {
      return yamlNode;
    }

    YamlNode curr = yamlNode;
    for (String currName : pathList) {
      if (curr == null) {
        return null;
      }

      JsonNode next = null;
      if (curr.isObject()) {
        next = curr.getCurrJsonNode().get(currName);
        if (next != null && next.isNull()) {
          next = null;
        }
      } else if (curr.isArray()) {
        next = getNextNodeFromArray(curr, currName);
      }

      curr = next == null ? null : new YamlNode(currName, next, curr);
    }

    return curr;
  }

  public JsonNode getNextNodeFromArray(YamlNode yamlNode, String currName) {
    JsonNode next = null;
    for (YamlNode arrayElement : yamlNode.asArray()) {
      /* For nodes such as variables where only value field is associated with name, key.
      Example: fqn - variables.something
        variables:
          - name: something
            value: something
      Another Example: fqn - sources.something
        sources:
          - identifier: something
            etc...
      */
      if (isNotEmpty(arrayElement.getArrayUniqueIdentifier())
          && currName.equals(arrayElement.getArrayUniqueIdentifier())) {
        next = arrayElement.getCurrJsonNode();
        break;
      } else if (arrayElement.isObject()) {
        /*
          For nodes having root field and then identifier inside that or cases where there is array inside array.
          Example: fqn - stages.something
            stages:
              - stage:
                  identifier: something
                  ...
          Another Example: fqn = stages.something (notice parallel is not there in fqn)
            stages:
              - parallel:
                - stage:
                    identifier: something
                    ....
         */
        for (YamlField field : arrayElement.fields()) {
          // Nodes having identifier to refer uniquely from the array.
          if (isNotEmpty(field.getNode().getIdentifier()) && currName.equals(field.getNode().getIdentifier())) {
            next = field.getNode().getCurrJsonNode();
            break;
          }
          // If the node is like parallel, a dummy node having another list.
          else if (field.getNode().isArray()) {
            next = getNextNodeFromArray(field.getNode(), currName);
          }
        }
      }
      // if next is populated in this iteration, break the loop.
      if (next != null) {
        break;
      }
    }
    return next;
  }

  public YamlNode findFirstNodeMatchingFieldName(YamlNode yamlNode, String fieldName) {
    if (yamlNode == null) {
      return null;
    }

    if (yamlNode.isObject()) {
      return findFieldNameInObject(yamlNode, fieldName);
    } else if (yamlNode.isArray()) {
      return findFieldNameInArray(yamlNode, fieldName);
    } else if (fieldName.equals(yamlNode.getFieldName())) {
      return yamlNode;
    }

    return null;
  }

  /*
  This method is specifically used currently only for check that the stage idenfier provided in useFromStage
  field exists or not. To make it more extensible, we will need to modify it.
   */
  private YamlNode findFieldNameInObject(YamlNode yamlNode, String fieldName) {
    if (yamlNode == null || isEmpty(fieldName)) {
      return null;
    }
    for (YamlField childYamlField : yamlNode.fields()) {
      if (fieldName.equals(childYamlField.getName())) {
        return childYamlField.getNode();
      }

      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();
      YamlNode requiredNode = null;
      if (value.isArray() && !YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> Array
        requiredNode = findFieldNameInArray(childYamlField.getNode(), fieldName);
      } else if (value.isObject()) {
        // Value -> Object
        requiredNode = findFieldNameInObject(childYamlField.getNode(), fieldName);
      }
      if (requiredNode != null) {
        return requiredNode;
      }
    }
    return null;
  }

  /*TODO: This method currently works for the cases when we want to check for field name being a stage
  identifier. But we need to modify it to handle other cases also in future. For e.g. We may need to check
  the field name as a leaf node's value also.
   */
  private YamlNode findFieldNameInArray(YamlNode yamlNode, String fieldName) {
    if (yamlNode == null || isEmpty(fieldName)) {
      return null;
    }
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (isNotEmpty(fieldName) && fieldName.equals(arrayElement.getName())) {
        return arrayElement;
      }
      YamlNode requiredNode = null;
      if (arrayElement.isArray() || arrayElement.isObject()) {
        // Value -> Array
        requiredNode = findFieldNameInArray(arrayElement, fieldName);
      }
      if (requiredNode != null) {
        return requiredNode;
      }
    }
    return null;
  }
}
