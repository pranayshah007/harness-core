/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import io.harness.exception.ngexception.beans.yamlschema.NodeErrorInfo;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidatorTypeCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaValidationUtils {
  public static final String ENUM_SCHEMA_ERROR_CODE = ValidatorTypeCode.ENUM.getErrorCode();
  public String[] removeParenthesisFromArguments(String[] arguments) {
    List<String> cleanArguments = new ArrayList<>();
    int length = arguments.length;
    for (int index = 0; index < length; index++) {
      if (!arguments[index].equals("[]")) {
        cleanArguments.add(arguments[index].substring(1, arguments[index].length() - 1));
      }
    }
    return cleanArguments.toArray(new String[0]);
  }

  public Map<String, List<ValidationMessage>> getValidationMessageCodeMap(
      Collection<ValidationMessage> validationMessages) {
    Map<String, List<ValidationMessage>> codes = new HashMap<>();
    for (ValidationMessage validationMessage : validationMessages) {
      if (codes.containsKey(validationMessage.getCode())) {
        codes.get(validationMessage.getCode()).add(validationMessage);
      } else {
        List<ValidationMessage> validationMessageList = new ArrayList<>();
        validationMessageList.add(validationMessage);
        codes.put(validationMessage.getCode(), validationMessageList);
      }
    }
    return codes;
  }

  public Map<String, List<ValidationMessage>> getValidationPathMap(Collection<ValidationMessage> validationMessages) {
    Map<String, List<ValidationMessage>> pathMap = new HashMap<>();
    for (ValidationMessage validationMessage : validationMessages) {
      if (pathMap.containsKey(validationMessage.getPath())) {
        pathMap.get(validationMessage.getPath()).add(validationMessage);
      } else {
        List<ValidationMessage> validationMessageList = new ArrayList<>();
        validationMessageList.add(validationMessage);
        pathMap.put(validationMessage.getPath(), validationMessageList);
      }
    }
    return pathMap;
  }

  public JsonNode parseJsonNodeByPath(ValidationMessage validationMessage, JsonNode jsonNode) {
    return parseJsonNodeByPath(validationMessage.getPath(), jsonNode);
  }

  public JsonNode parseJsonNodeByPath(String errorPath, JsonNode jsonNode) {
    JsonNode currentNode = jsonNode.deepCopy();
    String[] pathList = errorPath.split("\\.");
    for (String path : pathList) {
      if (path.equals("$")) {
        continue;
      }
      char[] charSet = path.toCharArray();
      int index = 0;
      while (index < charSet.length) {
        if (charSet[index] == ']') {
          index++;
        } else if (charSet[index] == '[') {
          int endIndex = path.indexOf(']', index);
          // Selecting element in ArrayNode.
          currentNode = currentNode.get(Integer.parseInt(path.substring(index + 1, endIndex)));
          index = endIndex + 1;
        } else {
          int nextIndex = path.indexOf('[', index);
          if (nextIndex == -1) {
            nextIndex = path.length();
          }
          currentNode = currentNode.get(path.substring(index, nextIndex));
          index = nextIndex;
        }
      }
    }
    return currentNode;
  }

  public NodeErrorInfo getStageErrorInfo(String path, JsonNode jsonNode) {
    try {
      char[] stringBuffer = path.toCharArray();
      int index = path.indexOf("stages[");
      while (index < path.length() && stringBuffer[index] != ']') {
        index++;
      }
      // Adding stage in path after stages[index].
      String pathToStage = path.substring(0, index + 7);
      JsonNode stageNode = parseJsonNodeByPath(pathToStage, jsonNode);
      if (stageNode == null) {
        return null;
      }
      return getNodeErrorInfoFromJsonNode(stageNode, pathToStage);
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  public NodeErrorInfo getStepErrorInfo(String path, JsonNode jsonNode) {
    try {
      char[] stringBuffer = path.toCharArray();
      int index = path.indexOf("steps[");
      while (index < path.length() && stringBuffer[index] != ']') {
        index++;
      }
      // Adding step in path after steps[index].
      String pathToStep = path.substring(0, index + 6);
      JsonNode stepNode = parseJsonNodeByPath(pathToStep, jsonNode);
      if (stepNode == null) {
        return null;
      }
      return getNodeErrorInfoFromJsonNode(stepNode, pathToStep);

    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  private NodeErrorInfo getNodeErrorInfoFromJsonNode(JsonNode jsonNode, String fqn) {
    return NodeErrorInfo.builder()
        .name(JsonNodeUtils.getString(jsonNode, "name"))
        .identifier(JsonNodeUtils.getString(jsonNode, "identifier"))
        .type(JsonNodeUtils.getString(jsonNode, "type"))
        .fqn(fqn)
        .build();
  }

  // If more specific error is present then we can ignore an error message.
  public Set<ValidationMessage> filterErrorsIfMoreSpecificErrorIsPresent(
      Collection<ValidationMessage> validationMessages) {
    Set<String> errorLocations =
        validationMessages.stream().map(ValidationMessage::getPath).collect(Collectors.toSet());
    return validationMessages.stream()
        .filter(message -> !checkIfMoreSpecificErrorIsPresent(errorLocations, message))
        .collect(Collectors.toSet());
  }

  private boolean checkIfMoreSpecificErrorIsPresent(Collection<String> errorLocations, ValidationMessage message) {
    if (message.getCode().equals(ENUM_SCHEMA_ERROR_CODE)) {
      String path = message.getPath();
      // Currently, filtering the ".type" errors only. Will keep adding more cases here.
      if (path.endsWith(".type")) {
        String pathTillNode = path.substring(0, path.length() - 5);
        if (checkIfNodePathIsPresentInAllPaths(errorLocations, pathTillNode, path)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean checkIfNodePathIsPresentInAllPaths(
      Collection<String> allPaths, String pathTillNode, String completePath) {
    for (String path : allPaths) {
      // Checking if pathTillNode's children is present in errors.
      if (!path.equals(completePath) && path.startsWith(pathTillNode)) {
        return true;
      }
    }
    return false;
  }

  private String getPathTillPreviousNode(String path) {
    String[] pathComponents = path.split("\\.");
    if (pathComponents.length <= 1) {
      return "";
    }
    int lastElementLength = pathComponents[pathComponents.length - 1].length();
    return path.substring(0, path.length() - lastElementLength - 1);
  }
}
