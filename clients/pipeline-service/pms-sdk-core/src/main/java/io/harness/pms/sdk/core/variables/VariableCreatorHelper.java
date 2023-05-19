/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;

import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.reflection.ReflectionUtils;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ClassUtils;

@UtilityClass
public class VariableCreatorHelper {
  private String LOGGER_NAME = "org.slf4j.Logger";
  public void addVariablesForVariables(
      YamlField variablesField, Map<String, YamlProperties> yamlPropertiesMap, String topRootFieldName) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode());
        String localName = YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), topRootFieldName);
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlProperties.newBuilder()
                .setLocalName(localName)
                .setFqn(fqn)
                .setVariableName(variableName)
                .setVisible(true)
                .build());
      }
    });
  }

  public void addPropertiesForSchemaAutocomplete(
      YamlField propertiesField, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlNode yamlNode = propertiesField.getNode();
    addVariablesInComplexObject(yamlPropertiesMap, yamlNode);
  }

  public void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
    List<String> extraFields = new ArrayList<>();
    extraFields.add(YAMLFieldNameConstants.UUID);
    extraFields.add(YAMLFieldNameConstants.IDENTIFIER);
    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (field.getNode().isObject()) {
        addVariablesInComplexObject(yamlPropertiesMap, field.getNode());
      } else if (field.getNode().isArray()) {
        List<YamlNode> innerFields = field.getNode().asArray();
        innerFields.forEach(f -> addVariablesInComplexObject(yamlPropertiesMap, f));
      } else if (!extraFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });
  }

  public void addFieldToPropertiesMapUnderStep(YamlField fieldNode, Map<String, YamlProperties> yamlPropertiesMap) {
    String fqn = YamlUtils.getFullyQualifiedName(fieldNode.getNode());
    String localName;

    localName = YamlUtils.getQualifiedNameTillGivenField(fieldNode.getNode(), YAMLFieldNameConstants.PROPERTIES);

    yamlPropertiesMap.put(fieldNode.getNode().getCurrJsonNode().textValue(),
        YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).setVisible(true).build());
  }

  public VariableCreationResponse createVariableResponseForVariables(YamlField variablesField, String fieldName) {
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    addVariablesForVariables(variablesField, yamlPropertiesMap, fieldName);
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  public void addFieldToPropertiesMap(
      YamlField fieldNode, Map<String, YamlProperties> yamlPropertiesMap, String topRootFieldName) {
    String fqn = YamlUtils.getFullyQualifiedName(fieldNode.getNode());
    String localName = YamlUtils.getQualifiedNameTillGivenField(fieldNode.getNode(), topRootFieldName);
    yamlPropertiesMap.put(fieldNode.getNode().getCurrJsonNode().textValue(),
        YamlProperties.newBuilder()
            .setLocalName(localName)
            .setFqn(fqn)
            .setVariableName(fieldNode.getNode().getFieldName())
            .setVisible(true)
            .build());
  }

  public boolean isNotYamlFieldEmpty(YamlField yamlField) {
    if (yamlField == null) {
      return false;
    }
    return !(
        yamlField.getNode().fields().size() == 1 && yamlField.getNode().getField(YAMLFieldNameConstants.UUID) != null);
  }

  public List<YamlField> getStepYamlFields(List<YamlNode> yamlNodes) {
    List<YamlField> stepFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        stepFields.add(stepField);
      } else if (stepGroupField != null) {
        List<YamlField> childYamlFields = getStepYamlFields(stepGroupField);
        if (EmptyPredicate.isNotEmpty(childYamlFields)) {
          stepFields.addAll(childYamlFields);
        }
      } else if (parallelStepField != null) {
        List<YamlField> childYamlFields = Optional.of(parallelStepField.getNode().asArray())
                                              .orElse(Collections.emptyList())
                                              .stream()
                                              .map(el -> el.getField(YAMLFieldNameConstants.STEP))
                                              .filter(Objects::nonNull)
                                              .collect(Collectors.toList());
        childYamlFields.addAll(Optional.of(parallelStepField.getNode().asArray())
                                   .orElse(Collections.emptyList())
                                   .stream()
                                   .map(el -> el.getField(YAMLFieldNameConstants.STEP_GROUP))
                                   .filter(Objects::nonNull)
                                   .collect(Collectors.toList()));
        if (EmptyPredicate.isNotEmpty(childYamlFields)) {
          stepFields.addAll(childYamlFields);
        }
      }
    });
    return stepFields;
  }

  public List<YamlField> getStepYamlFields(YamlField config) {
    List<YamlNode> yamlNodes =
        Optional
            .of(Preconditions.checkNotNull(config.getNode().getField(YAMLFieldNameConstants.STEPS)).getNode().asArray())
            .orElse(Collections.emptyList());
    return getStepYamlFields(yamlNodes);
  }

  public void collectVariableExpressions(Object obj, Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlExtraProperties> yamlExtraPropertiesMap, String fqnPrefix, String localNamePrefix) {
    collectVariableExpressions(obj, yamlPropertiesMap, yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, "");
  }

  // This method collect variable expressions using @VariableExpression annotation
  // Need to give wrapper object and not direct ParameterField or Primitive field (will not work for these types as
  // given object)
  public void collectVariableExpressions(Object givenObject, Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlExtraProperties> yamlExtraPropertiesMap, String fqnPrefix, String localNamePrefix,
      String aliasNamePrefix) {
    Class<?> c = givenObject.getClass();
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(c);
    Field parentFieldUuid = ReflectionUtils.getFieldByName(c, "uuid");
    if (parentFieldUuid != null) {
      parentFieldUuid.setAccessible(true);
      try {
        if (parentFieldUuid.get(givenObject) == null) {
          parentFieldUuid.set(givenObject, UUIDGenerator.generateUuid());
        }

      } catch (IllegalAccessException e) {
        throw new InvalidRequestException(
            "Unable to set field property in variables expression for field - " + parentFieldUuid.toString(), e);
      }
    }
    for (Field field : fields) {
      if (skipVariableExpressionInclusion(field)) {
        continue;
      }
      field.setAccessible(true);

      try {
        Object fieldValue = field.get(givenObject);
        // Primitive objects
        if (isLeafVariable(field, fieldValue)) {
          addYamlPropertyForPrimitiveVariables(field, givenObject, parentFieldUuid, yamlPropertiesMap,
              yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, aliasNamePrefix);
        } else {
          addYamlPropertyToCustomObject(fieldValue, field, parentFieldUuid, givenObject, yamlPropertiesMap,
              yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, aliasNamePrefix);
        }

      } catch (IllegalAccessException e) {
        throw new InvalidRequestException(
            "Unable to set field property in variables expression for field - " + field.toString(), e);
      }
    }
  }

  private boolean isLeafVariable(Field field, Object fieldValue) {
    if (fieldValue == null || isPrimitiveDataType(fieldValue.getClass())) {
      return true;
    }
    return isCustomObjectAsLeafField(field, fieldValue);
  }

  private boolean isCustomObjectAsLeafField(Field field, Object fieldValue) {
    if (field != null && field.isAnnotationPresent(VariableExpression.class)) {
      return field.getAnnotation(VariableExpression.class).skipInnerObjectTraversal();
    }
    // if value is null
    if (fieldValue == null) {
      return true;
    }
    if (ParameterField.class.isAssignableFrom(fieldValue.getClass())) {
      ParameterField<?> parameterFieldValue = (ParameterField) fieldValue;
      // if ParameterField has value as null (if field is expression or isParameterFieldNull)
      if (parameterFieldValue.getValue() == null) {
        return true;
      }
      if (isPrimitiveDataType(parameterFieldValue.getValue().getClass())) {
        return true;
      }
      // If ParameterField has list of primitive types
      if (List.class.isAssignableFrom(parameterFieldValue.getValue().getClass())) {
        List valueList = (List) parameterFieldValue.getValue();
        if (!valueList.isEmpty() && isPrimitiveDataType(valueList.get(0).getClass())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isPrimitiveDataType(Class<?> cls) {
    return String.class.isAssignableFrom(cls) || ClassUtils.isPrimitiveOrWrapper(cls) || cls.isEnum();
  }

  private void addYamlPropertyForPrimitiveVariables(Field field, Object obj, Field fieldUUid,
      Map<String, YamlProperties> yamlPropertiesMap, Map<String, YamlExtraProperties> yamlExtraPropertiesMap,
      String fqnPrefix, String localNamePrefix, String aliasNamePrefix) throws IllegalAccessException {
    // Primitive objects
    YamlProperties yamlProperties = getYamlProperties(field, fqnPrefix, localNamePrefix, aliasNamePrefix);
    String fieldName = getFieldName(field);
    if (field.getType().isEnum()) {
      addYamlExtraPropertyToObject(fieldUUid, yamlExtraPropertiesMap, yamlProperties, fieldName, obj);
    } else if (field.getType().equals(String.class)) {
      if (replaceWithUuid(fieldName)) {
        String uuid = UUIDGenerator.generateUuid();
        field.set(obj, uuid);
        yamlPropertiesMap.put(uuid, yamlProperties);
      } else {
        addYamlExtraPropertyToObject(fieldUUid, yamlExtraPropertiesMap, yamlProperties, fieldName, obj);
      }
    } else if (field.getType().isPrimitive()) {
      addYamlExtraPropertyToObject(fieldUUid, yamlExtraPropertiesMap, yamlProperties, fieldName, obj);
    } else if (field.getType().equals(ParameterField.class)) {
      if (replaceWithUuid(fieldName)) {
        String uuid = UUIDGenerator.generateUuid();
        field.set(obj, ParameterField.createJsonResponseField(uuid));
        yamlPropertiesMap.put(uuid, yamlProperties);
      } else {
        addYamlExtraPropertyToObject(fieldUUid, yamlExtraPropertiesMap, yamlProperties, fieldName, obj);
      }
    }
    // Other Objects which are leaf fields due to skipInnerObject set as true
    else {
      addYamlExtraPropertyToObject(fieldUUid, yamlExtraPropertiesMap, yamlProperties, fieldName, obj);
    }
  }

  private void addYamlExtraPropertyToObject(Field fieldUUid, Map<String, YamlExtraProperties> yamlExtraPropertiesMap,
      YamlProperties yamlProperties, String fieldName, Object givenObj) throws IllegalAccessException {
    if (fieldUUid == null) {
      throw new InvalidRequestException(
          "Could not add variables for primitive types in object which doesn't have uuid field in parent object for fieldName - "
          + fieldName);
    }
    String uuid = (String) fieldUUid.get(givenObj);
    if (yamlExtraPropertiesMap.containsKey(uuid)) {
      YamlExtraProperties yamlExtraProperties = yamlExtraPropertiesMap.get(uuid);
      YamlExtraProperties properties = yamlExtraProperties.toBuilder().addProperties(yamlProperties).build();
      yamlExtraPropertiesMap.put(uuid, properties);
    } else {
      yamlExtraPropertiesMap.put(uuid, YamlExtraProperties.newBuilder().addProperties(yamlProperties).build());
    }
  }

  private void addYamlPropertyToCustomObject(Object fieldValue, Field field, Field parentFieldUuid, Object parentObject,
      Map<String, YamlProperties> yamlPropertiesMap, Map<String, YamlExtraProperties> yamlExtraPropertiesMap,
      String fqnPrefix, String localNamePrefix, String aliasNamePrefix) throws IllegalAccessException {
    VariableExpression annotation = field.getAnnotation(VariableExpression.class);
    String fieldAliasName = annotation == null ? "" : annotation.aliasName();

    // if null then add fieldName to extra property
    YamlProperties yamlProperties = getYamlProperties(field, fqnPrefix, localNamePrefix, aliasNamePrefix);
    String fieldName = getFieldName(field);
    if (fieldValue == null) {
      addYamlExtraPropertyToObject(
          parentFieldUuid, yamlExtraPropertiesMap, yamlProperties, field.getName(), parentObject);
    }
    // Check for List
    else if (List.class.isAssignableFrom(fieldValue.getClass())) {
      List valueList = (List) fieldValue;
      addYamlPropertyToListObject(valueList, field, parentFieldUuid, parentObject, yamlPropertiesMap,
          yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, aliasNamePrefix);
    }
    // check for map
    else if (Map.class.isAssignableFrom(fieldValue.getClass())) {
      Map objectMap = (Map) fieldValue;
      addYamlPropertyToMapObject(objectMap, field, parentFieldUuid, parentObject, yamlPropertiesMap,
          yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, aliasNamePrefix);
    }
    // ParameterField where we want to traverse for the value inside it.
    else if (ParameterField.class.isAssignableFrom(fieldValue.getClass())) {
      ParameterField<?> parameterFieldValue = (ParameterField) fieldValue;
      addYamlPropertyToCustomObject(parameterFieldValue.getValue(), field, parentFieldUuid, parentObject,
          yamlPropertiesMap, yamlExtraPropertiesMap, fqnPrefix, localNamePrefix, aliasNamePrefix);
    }
    // else its a custom complex object
    else {
      collectVariableExpressions(fieldValue, yamlPropertiesMap, yamlExtraPropertiesMap,
          getMergedFqn(fqnPrefix, fieldName), getMergedFqn(localNamePrefix, fieldName),
          getMergedFqn(aliasNamePrefix, fieldAliasName));
    }
  }

  private void addYamlPropertyToListObject(List valueList, Field field, Field parentFieldUuid, Object parentObject,
      Map<String, YamlProperties> yamlPropertiesMap, Map<String, YamlExtraProperties> yamlExtraPropertiesMap,
      String fqnPrefix, String localNamePrefix, String aliasNamePrefix) throws IllegalAccessException {
    String fieldName = getFieldName(field);
    VariableExpression annotation = field.getAnnotation(VariableExpression.class);
    String fieldAliasName = annotation == null ? "" : annotation.aliasName();
    for (Object item : valueList) {
      if (isLeafVariable(null, item)) {
        YamlProperties yamlProperties = getYamlProperties(field, fqnPrefix, localNamePrefix, aliasNamePrefix);
        addYamlExtraPropertyToObject(parentFieldUuid, yamlExtraPropertiesMap, yamlProperties, fieldName, parentObject);
        break;
      } else {
        // Handling list of primitive types
        String uniqueKeyInListField = getUniqueKeyInListField(item);
        // Check one more level down for unique key, valid for manifests etc.
        if (uniqueKeyInListField == null) {
          List<Field> internalObjectFields = ReflectionUtils.getAllDeclaredAndInheritedFields(item.getClass());
          Field internalField = internalObjectFields.get(0);
          internalField.setAccessible(true);
          Object internalFieldValue = internalField.get(item);
          uniqueKeyInListField = getUniqueKeyInListField(internalFieldValue);
          if (uniqueKeyInListField == null) {
            YamlProperties yamlProperties = getYamlProperties(field, fqnPrefix, localNamePrefix, aliasNamePrefix);
            addYamlExtraPropertyToObject(
                parentFieldUuid, yamlExtraPropertiesMap, yamlProperties, fieldName, parentObject);
            break;
          } else {
            String listKey = getMergedFqn(fieldName, uniqueKeyInListField);
            collectVariableExpressions(internalFieldValue, yamlPropertiesMap, yamlExtraPropertiesMap,
                getMergedFqn(fqnPrefix, listKey), getMergedFqn(localNamePrefix, listKey),
                getMergedFqn(aliasNamePrefix, fieldAliasName));
          }
        } else {
          String listKey = getMergedFqn(fieldName, uniqueKeyInListField);
          collectVariableExpressions(item, yamlPropertiesMap, yamlExtraPropertiesMap, getMergedFqn(fqnPrefix, listKey),
              getMergedFqn(localNamePrefix, listKey), getMergedFqn(aliasNamePrefix, fieldAliasName));
        }
      }
    }
  }

  private void addYamlPropertyToMapObject(Map objectMap, Field field, Field parentFieldUuid, Object parentObject,
      Map<String, YamlProperties> yamlPropertiesMap, Map<String, YamlExtraProperties> yamlExtraPropertiesMap,
      String fqnPrefix, String localNamePrefix, String aliasNamePrefix) throws IllegalAccessException {
    String fieldName = getFieldName(field);
    VariableExpression annotation = field.getAnnotation(VariableExpression.class);
    String fieldAliasName = annotation == null ? "" : annotation.aliasName();
    boolean fieldVisible = annotation == null || annotation.visible();
    // check for empty map
    if (objectMap.isEmpty() || (objectMap.size() == 1 && objectMap.containsKey(YAMLFieldNameConstants.UUID))) {
      YamlProperties yamlProperties = getYamlProperties(field, fqnPrefix, localNamePrefix, aliasNamePrefix);
      addYamlExtraPropertyToObject(parentFieldUuid, yamlExtraPropertiesMap, yamlProperties, fieldName, parentObject);
    } else {
      for (Object k1 : objectMap.keySet()) {
        // Ignore Uuid keys.
        if (k1.equals(YAMLFieldNameConstants.UUID)) {
          continue;
        }
        String key = getMergedFqn(fieldName, (String) k1);
        Object mapValue = objectMap.get(k1);
        if (isLeafVariable(null, mapValue)) {
          String valueUuid = UUIDGenerator.generateUuid();
          objectMap.put(k1, valueUuid);
          yamlPropertiesMap.put(valueUuid,
              getPrimitiveYamlProperty(getMergedFqn(fqnPrefix, key), getMergedFqn(localNamePrefix, key),
                  getMergedFqn(aliasNamePrefix, fieldAliasName), fieldVisible, (String) k1));
        } else {
          collectVariableExpressions(mapValue, yamlPropertiesMap, yamlExtraPropertiesMap, getMergedFqn(fqnPrefix, key),
              getMergedFqn(localNamePrefix, key), getMergedFqn(aliasNamePrefix, fieldAliasName));
        }
      }
    }
  }

  private String getMergedFqn(String prefix, String fieldPath) {
    if (EmptyPredicate.isEmpty(fieldPath)) {
      return prefix;
    }
    if (EmptyPredicate.isEmpty(prefix)) {
      return fieldPath;
    }
    return prefix + "." + fieldPath;
  }

  // returns false for identifier and type fields
  private boolean replaceWithUuid(String fieldName) {
    return !fieldName.equals(YAMLFieldNameConstants.IDENTIFIER) && !fieldName.equals(YAMLFieldNameConstants.TYPE);
  }

  private boolean skipVariableExpressionInclusion(Field field) {
    if (field.isAnnotationPresent(VariableExpression.class)) {
      return field.getAnnotation(VariableExpression.class).skipVariableExpression();
    } else {
      if (field.isAnnotationPresent(ApiModelProperty.class)) {
        ApiModelProperty annotation = field.getAnnotation(ApiModelProperty.class);
        return annotation.hidden();
      } else {
        return field.isAnnotationPresent(JsonIgnore.class);
      }
    }
  }

  private String getUniqueKeyInListField(Object fieldObject) {
    Map<String, Object> fieldValues = ReflectionUtils.getFieldValues(fieldObject,
        new HashSet<>(Arrays.asList(YamlNode.IDENTIFIER_FIELD_NAME, YamlNode.KEY_FIELD_NAME, YamlNode.NAME_FIELD_NAME)),
        false);
    if (fieldValues.get(YamlNode.IDENTIFIER_FIELD_NAME) != null) {
      return (String) fieldValues.get(YamlNode.IDENTIFIER_FIELD_NAME);
    } else if (fieldValues.get(YamlNode.KEY_FIELD_NAME) != null) {
      return (String) fieldValues.get(YamlNode.KEY_FIELD_NAME);
    } else if (fieldValues.get(YamlNode.NAME_FIELD_NAME) != null) {
      return (String) fieldValues.get(YamlNode.NAME_FIELD_NAME);
    } else {
      return null;
    }
  }

  private YamlProperties getYamlProperties(
      Field field, String fqnPrefix, String localNamePrefix, String aliasNamePrefix) {
    String aliasName = "";
    boolean visible = true;
    String fieldName = getFieldName(field);
    if (field.isAnnotationPresent(VariableExpression.class)) {
      VariableExpression annotation = field.getAnnotation(VariableExpression.class);
      aliasName = annotation.aliasName();
      visible = annotation.visible();
    }
    return YamlProperties.newBuilder()
        .setFqn(getMergedFqn(fqnPrefix, fieldName))
        .setLocalName(getMergedFqn(localNamePrefix, fieldName))
        .setAliasFQN(getMergedFqn(aliasNamePrefix, aliasName))
        .setVisible(visible)
        .setVariableName(fieldName)
        .build();
  }

  private String getFieldName(Field field) {
    if (field.isAnnotationPresent(VariableExpression.class)
        && field.getAnnotation(VariableExpression.class).policy().equals(REGULAR_WITH_CUSTOM_FIELD)) {
      VariableExpression annotation = field.getAnnotation(VariableExpression.class);
      return annotation.customFieldName();
    } else if (field.isAnnotationPresent(JsonProperty.class)) {
      JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
      return jsonPropertyAnnotation.value();
    }
    return field.getName();
  }

  private YamlProperties getPrimitiveYamlProperty(
      String fqn, String localName, String aliasName, boolean isVisible, String variableName) {
    return YamlProperties.newBuilder()
        .setFqn(fqn)
        .setLocalName(localName)
        .setAliasFQN(aliasName)
        .setVisible(isVisible)
        .setVariableName(variableName)
        .build();
  }

  public void addYamlExtraPropertyToMap(
      String uuid, Map<String, YamlExtraProperties> yamlExtraPropertiesMap, YamlExtraProperties otherExtraProperties) {
    if (yamlExtraPropertiesMap.containsKey(uuid)) {
      YamlExtraProperties yamlExtraProperties = yamlExtraPropertiesMap.get(uuid);
      YamlExtraProperties properties = yamlExtraProperties.toBuilder()
                                           .addAllProperties(otherExtraProperties.getPropertiesList())
                                           .addAllOutputProperties(otherExtraProperties.getOutputPropertiesList())
                                           .build();
      yamlExtraPropertiesMap.put(uuid, properties);
    } else {
      yamlExtraPropertiesMap.put(uuid, otherExtraProperties);
    }
  }

  // Traverses the object checking values inside the object and return their expressions
  public List<String> getExpressionsInObject(Object obj, String prefix) {
    Class<?> c = obj.getClass();
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(c);
    List<String> resultantFieldExpressions = new LinkedList<>();
    for (Field field : fields) {
      if (skipVariableExpressionInclusion(field)) {
        continue;
      }
      field.setAccessible(true);

      String fieldName = getFieldName(field);

      String mergedFqn = getMergedFqn(prefix, fieldName);
      try {
        Object fieldValue = field.get(obj);
        if (isLeafVariable(field, fieldValue)) {
          resultantFieldExpressions.add(mergedFqn);
        } else {
          addExpressionInCustomObject(field, fieldValue, resultantFieldExpressions, mergedFqn);
        }
      } catch (IllegalAccessException e) {
        throw new InvalidRequestException(
            "Unable to get field property in variables expression for field - " + field.toString(), e);
      }
    }
    return resultantFieldExpressions;
  }

  private void addExpressionInCustomObject(
      Field field, Object fieldValue, List<String> resultantFieldExpressions, String mergedFqn) {
    if (List.class.isAssignableFrom(fieldValue.getClass())) {
      List valueList = (List) fieldValue;
      if (EmptyPredicate.isEmpty(valueList)) {
        resultantFieldExpressions.add(mergedFqn);
      } else {
        for (Object item : valueList) {
          String uniqueKeyInListField = getUniqueKeyInListField(item);
          // Handling list of primitive types
          if (isLeafVariable(null, item) || uniqueKeyInListField == null) {
            resultantFieldExpressions.add(mergedFqn);
            break;
          }
          String listKey = getMergedFqn(mergedFqn, uniqueKeyInListField);
          resultantFieldExpressions.addAll(getExpressionsInObject(field, listKey));
        }
      }
    } else if (Map.class.isAssignableFrom(fieldValue.getClass())) {
      Map objectMap = (Map) fieldValue;
      // check for empty map
      if (EmptyPredicate.isEmpty(objectMap)
          || (objectMap.size() == 1 && objectMap.containsKey(YAMLFieldNameConstants.UUID))) {
        resultantFieldExpressions.add(mergedFqn);
      } else {
        for (Object k1 : objectMap.keySet()) {
          // Ignore Uuid keys.
          if (k1.equals(YAMLFieldNameConstants.UUID)) {
            continue;
          }
          String key = getMergedFqn(mergedFqn, (String) k1);
          Object mapValue = objectMap.get(k1);
          if (isLeafVariable(null, mapValue)) {
            resultantFieldExpressions.add(key);
          } else {
            resultantFieldExpressions.addAll(getExpressionsInObject(mapValue, key));
          }
        }
      }
    }
    // ParameterField
    else if (ParameterField.class.isAssignableFrom(fieldValue.getClass())) {
      ParameterField<?> parameterFieldValue = (ParameterField) fieldValue;
      addExpressionInCustomObject(field, parameterFieldValue.getValue(), resultantFieldExpressions, mergedFqn);
    } else {
      resultantFieldExpressions.addAll(getExpressionsInObject(fieldValue, mergedFqn));
    }
  }
}
