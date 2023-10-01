/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.NotExpression;
import io.harness.pms.yaml.validation.InputSetValidator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
@OwnedBy(PIPELINE)
@RecasterAlias("parameterField")
@Slf4j
public class ParameterField<T> {
  @NotExpression private String expressionValue;
  private boolean expression;
  private T value;
  private T defaultValue;
  private boolean typeString;
  private boolean isExecutionInput;
  // This field is set when runtime input with validation is given.
  private InputSetValidator inputSetValidator;

  // Below 2 fields are when caller wants to set String field instead of T like for some errors, input set merge, etc.
  private boolean jsonResponseField;
  private String responseField;

  public T getValue() {
    return value != null ? value : defaultValue;
  }

  public T obtainValue() {
    return value;
  }

  public static <T> ParameterField<T> createExpressionField(
      boolean isExpression, String expressionValue, InputSetValidator inputSetValidator, boolean isTypeString) {
    return new ParameterField<>(null, null, isExpression, expressionValue, inputSetValidator, isTypeString);
  }

  public static <T> ParameterField<T> createFieldWithDefaultValue(boolean isExpression, boolean isExecutionInput,
      String expressionValue, T defaultValue, InputSetValidator inputSetValidator, boolean isTypeString) {
    return new ParameterField<>(
        null, defaultValue, isExpression, isExecutionInput, expressionValue, inputSetValidator, isTypeString);
  }

  public static <T> ParameterField<T> createValueField(T value) {
    return new ParameterField<>(value, null, false, null, null, value != null && value.getClass().equals(String.class));
  }

  public static <T> ParameterField<T> createValueFieldWithInputSetValidator(
      T value, InputSetValidator inputSetValidator, boolean isTypeString) {
    return new ParameterField<>(value, null, false, null, inputSetValidator, isTypeString);
  }

  public static <T> ParameterField<T> createJsonResponseField(String responseField) {
    return new ParameterField<>(true, responseField);
  }

  public static <T> ParameterField<T> ofNull() {
    return new ParameterField<T>(null, false, false, null, null, false, null, false, null);
  }

  public ParameterField(String expressionValue, boolean expression, boolean isExecutionInput, T value, T defaultValue,
      boolean typeString, InputSetValidator inputSetValidator, boolean jsonResponseField, String responseField) {
    this.expressionValue = expressionValue;
    this.expression = expression;
    this.value = value;
    this.defaultValue = defaultValue;
    this.typeString = typeString;
    this.inputSetValidator = inputSetValidator;
    this.jsonResponseField = jsonResponseField;
    this.responseField = responseField;
    this.isExecutionInput = isExecutionInput;
  }

  @Builder
  public ParameterField(String expressionValue, boolean expression, T value, boolean typeString,
      InputSetValidator inputSetValidator, boolean jsonResponseField, String responseField) {
    this.expressionValue = expressionValue;
    this.expression = expression;
    this.value = value;
    this.typeString = typeString;
    this.inputSetValidator = inputSetValidator;
    this.jsonResponseField = jsonResponseField;
    this.responseField = responseField;
  }

  public ParameterField(T value, T defaultValue, boolean expression, String expressionValue,
      InputSetValidator inputSetValidator, boolean typeString) {
    this(expressionValue, expression, false, value, defaultValue, typeString, inputSetValidator, false, null);
  }

  public ParameterField(T value, T defaultValue, boolean expression, boolean isExecutionInput, String expressionValue,
      InputSetValidator inputSetValidator, boolean typeString) {
    this(
        expressionValue, expression, isExecutionInput, value, defaultValue, typeString, inputSetValidator, false, null);
  }

  private ParameterField(boolean jsonResponseField, String responseField) {
    this(null, false, false, null, null, false, null, jsonResponseField, responseField);
  }

  public Object get(String key) {
    return expression ? expressionValue : ExpressionEvaluatorUtils.fetchField(value, key).orElse(null);
  }

  public void updateWithExpression(String newExpression) {
    expression = true;
    expressionValue = newExpression;
    value = null;
  }

  public void updateWithValue(Object newValue) {
    expression = false;
    expressionValue = null;
    value = (T) newValue;
  }

  public void updateValueOnly(Object newValue) {
    value = (T) newValue;
  }

  @JsonIgnore
  public Object getJsonFieldValue() {
    if (expression) {
      StringBuilder result = new StringBuilder(expressionValue);
      if (inputSetValidator != null) {
        result.append('.')
            .append(inputSetValidator.getValidatorType().getYamlName())
            .append('(')
            .append(inputSetValidator.getParameters())
            .append(')');
      }
      if (defaultValue != null) {
        result.append(".default(").append(defaultValue.toString()).append(')');
      }
      if (isExecutionInput) {
        result.append(".executionInput()");
      }
      return result.toString();
    } else {
      return jsonResponseField ? responseField : value;
    }
  }

  public Object fetchFinalValue() {
    return expression ? expressionValue : (value != null ? value : defaultValue);
  }

  public static boolean isNotNull(ParameterField<?> actualField) {
    return !isNull(actualField);
  }

  public static boolean isNull(ParameterField<?> actualField) {
    if (actualField == null) {
      return true;
    }

    if (ofNull().equals(actualField)) {
      return true;
    }

    if (actualField.getExpressionValue() != null || actualField.getInputSetValidator() != null
        || actualField.getResponseField() != null || actualField.getValue() != null
        || actualField.getDefaultValue() != null) {
      return false;
    }
    // Every flag should be false.
    return !actualField.isExpression() && !actualField.isJsonResponseField() && !actualField.isTypeString();
  }

  public static <T> boolean isBlank(ParameterField<T> actualField) {
    if (isNull(actualField)) {
      return true;
    }
    return actualField.isTypeString() && StringUtils.isBlank((String) actualField.getValue())
        && StringUtils.isBlank(actualField.getExpressionValue());
  }
}
