/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.RISHABH;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoOutcomeDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthSAOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthUAOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ClassUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ConnectorOutcomeTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFieldNamesForConnectorOutcome() {
    List<Class<?>> classesToTest = Arrays.asList(ConnectorInfoOutcomeDTO.class, AzureMSIAuthSAOutcomeDTO.class,
        AzureMSIAuthUAOutcomeDTO.class, GcpDelegateDetailsDTO.class);
    for (Class<?> classToTest : classesToTest) {
      getExpressionsInObject(classToTest, "");
    }
  }

  public boolean checkIfClassIsCollection(Field declaredField) {
    return Collection.class.isAssignableFrom(declaredField.getType());
  }

  public JsonSubTypes getJsonSubTypes(Field field) {
    JsonSubTypes annotation = field.getAnnotation(JsonSubTypes.class);
    if (annotation == null || isEmpty(annotation.value())) {
      annotation = field.getType().getAnnotation(JsonSubTypes.class);
    }
    if (checkIfClassIsCollection(field)) {
      ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
      Class<?> collectionTypeClass = (Class<?>) collectionType.getActualTypeArguments()[0];
      annotation = collectionTypeClass.getAnnotation(JsonSubTypes.class);
    }
    if (annotation == null || isEmpty(annotation.value())) {
      return null;
    }
    return annotation;
  }
  // Traverses the object checking values inside the object and return their expressions
  public List<String> getExpressionsInObject(Class<?> c, String prefix) {
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(c);
    List<String> resultantFieldExpressions = new LinkedList<>();
    for (Field field : fields) {
      field.setAccessible(true);
      String fieldName = getFieldName(field, c);
      JsonSubTypes annotations = getJsonSubTypes(field);
      String mergedFqn = getMergedFqn(prefix, fieldName);
      if (!isNull(annotations)) {
        for (JsonSubTypes.Type annotation : annotations.value()) {
          if (!isLeafVariable(null, annotation.value(), false)) {
            getExpressionsInObject(annotation.value(), mergedFqn);
          }
        }
      }
      try {
        if (!isLeafVariable(field, field.getType(), true)) {
          addExpressionInCustomObject(field, field.getType(), mergedFqn);
        }
      } catch (Exception e) {
        throw new InvalidRequestException("Unable to get field property in variables expression: " + e.getMessage());
      }
    }
    return resultantFieldExpressions;
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

  private void addExpressionInCustomObject(Field field, Class<?> fieldValue, String mergedFqn) {
    if (Map.class.isAssignableFrom(fieldValue)) {
      Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
      for (Type type : types) {
        if (!isLeafVariable(null, (Class<?>) type, false)) {
          getExpressionsInObject((Class<?>) type, mergedFqn);
        }
      }
    } else {
      getExpressionsInObject(fieldValue, mergedFqn);
    }
  }

  private String getFieldName(Field field, Class<?> c) {
    if (field.isAnnotationPresent(JsonProperty.class)) {
      JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
      if (!field.getName().equals(jsonPropertyAnnotation.value())) {
        throw new InvalidYamlException(
            "Field name and JsonProperty name doesn't match for field " + field.getType() + " in " + c.getName());
      }
    }
    return field.getName();
  }

  private boolean isPrimitiveDataType(Class<?> cls) {
    return String.class.isAssignableFrom(cls) || ClassUtils.isPrimitiveOrWrapper(cls) || cls.isEnum();
  }

  private boolean isLeafVariable(Field field, Class<?> fieldValue, Boolean checkField) {
    if (fieldValue == null || isPrimitiveDataType(fieldValue)) {
      return true;
    }
    return checkField && isCustomObjectAsLeafField(field, fieldValue);
  }

  private boolean isCustomObjectAsLeafField(Field field, Class<?> fieldValue) {
    if (field != null && field.isAnnotationPresent(VariableExpression.class)) {
      return field.getAnnotation(VariableExpression.class).skipInnerObjectTraversal();
    }
    // if value is null
    return fieldValue == null;
  }
}
