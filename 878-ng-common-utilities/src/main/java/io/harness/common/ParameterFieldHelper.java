/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;

import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ParameterFieldHelper {
  public <T> T getParameterFieldValue(ParameterField<T> fieldValue) {
    if (fieldValue == null) {
      return null;
    }
    return fieldValue.getValue();
  }

  public String getParameterFieldFinalValueString(ParameterField<String> fieldValue) {
    if (fieldValue == null) {
      return null;
    }
    return (String) fieldValue.fetchFinalValue();
  }

  public Boolean getBooleanParameterFieldValue(ParameterField<?> fieldValue) {
    Object value = getParameterFieldValue(fieldValue);

    if (value == null) {
      return Boolean.FALSE;
    }

    if (value instanceof String) {
      String valueString = (String) value;
      if (!valueString.equalsIgnoreCase("true") && !valueString.equalsIgnoreCase("false")) {
        throw new IllegalArgumentException(format("Expected 'true' or 'false' value, got %s", valueString));
      }

      return Boolean.valueOf((String) value);
    }

    return (Boolean) value;
  }

  public String getParameterFieldValueHandleValueNull(ParameterField<String> fieldValue) {
    if (isNull(fieldValue)) {
      return null;
    }

    return isNull(fieldValue.getValue()) ? "" : fieldValue.getValue();
  }

  public Optional<String> getParameterFieldFinalValue(ParameterField<String> fieldValue) {
    if (fieldValue == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(fieldValue.fetchFinalValue().toString());
  }

  public <T> boolean hasListValue(ParameterField<List<T>> listParameterField, boolean allowExpression) {
    if (ParameterField.isNull(listParameterField)) {
      return false;
    }

    if (allowExpression && listParameterField.isExpression()) {
      return true;
    }

    return isNotEmpty(getParameterFieldValue(listParameterField));
  }

  public boolean hasStringValue(ParameterField<String> parameterField, boolean allowExpression) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    if (allowExpression && parameterField.isExpression()) {
      return true;
    }

    return isNotEmpty(getParameterFieldValue(parameterField));
  }

  public List<String> getParameterFieldListValue(
      ParameterField<List<String>> parameterFieldList, boolean allowGenericExpression) {
    if (!hasListValue(parameterFieldList, false)) {
      return Collections.emptyList();
    }

    List<String> parameterFieldListValue = getParameterFieldValue(parameterFieldList);
    return allowGenericExpression
        ? parameterFieldListValue
        : parameterFieldListValue.stream()
              .filter(listItem -> !NGExpressionUtils.matchesGenericExpressionPattern(listItem))
              .collect(Collectors.toList());
  }

  public List<String> getParameterFieldListValueBySeparator(
      ParameterField<?> parameterField, final String separatorPattern) {
    if (parameterField == null || parameterField.getValue() == null) {
      return Collections.emptyList();
    } else if (parameterField.getValue() instanceof String) {
      String strValue = (String) parameterField.getValue();
      return isEmpty(strValue)
          ? Collections.emptyList()
          : Splitter.onPattern(separatorPattern).omitEmptyStrings().trimResults().splitToList(strValue);
    } else if (parameterField.getValue() instanceof List) {
      return (List<String>) parameterField.getValue();
    } else {
      throw new InvalidArgumentsException(
          format("Unable for parse parameter field value, finalValue: %s", parameterField.fetchFinalValue()));
    }
  }

  public Map<String, String> getParameterFieldMapValueBySeparator(
      ParameterField<?> parameterField, final String separatorPattern, String keyValueSeparator) {
    if (parameterField == null || parameterField.getValue() == null) {
      return Collections.emptyMap();
    } else if (parameterField.getValue() instanceof String) {
      String strValue = (String) parameterField.getValue();
      return isEmpty(strValue) ? Collections.emptyMap()
                               : Splitter.onPattern(separatorPattern)
                                     .omitEmptyStrings()
                                     .trimResults()
                                     .withKeyValueSeparator(keyValueSeparator)
                                     .split(strValue);
    } else if (parameterField.getValue() instanceof Map) {
      return (Map<String, String>) parameterField.getValue();
    } else {
      throw new InvalidArgumentsException(
          format("Unable for parse parameter field value, finalValue: %s", parameterField.fetchFinalValue()));
    }
  }

  public static <T> boolean hasValueOrExpression(ParameterField<T> parameterField, boolean allowExpression) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    T parameterFieldValue = getParameterFieldValue(parameterField);
    if (parameterFieldValue instanceof String) {
      return allowExpression ? (parameterField.isExpression() || !isEmpty((String) parameterFieldValue))
                             : !isEmpty((String) parameterFieldValue);
    }
    if (parameterFieldValue instanceof Map) {
      return allowExpression ? (parameterField.isExpression() || !isEmpty((Map) parameterFieldValue))
                             : !isEmpty((Map) parameterFieldValue);
    }

    throw new InvalidArgumentsException(format(
        "Unsupported value validation for parameter field, parameter field class: %s", parameterField.getClass()));
  }

  public boolean hasValueOrExpression(ParameterField<String> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(getParameterFieldValue(parameterField));
  }

  public <T> boolean hasValueListOrExpression(ParameterField<List<T>> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(getParameterFieldValue(parameterField));
  }

  /**
   *
   * @param fieldValue
   * @return Integer value.
   *
   * In cases with expressions value is coming as Double and then new BigDecimal(1.0).intValueExact() is throwing class
   * cast exception
   */
  public Integer getIntegerParameterFieldValue(ParameterField<?> fieldValue) {
    Object value = getParameterFieldValue(fieldValue);

    if (value == null) {
      return null;
    }

    if (value instanceof String) {
      String valueStr = (String) value;
      if (isEmpty(valueStr)) {
        return null;
      }

      return Integer.parseInt(valueStr);
    }

    if (value instanceof Double) {
      return ((Double) value).intValue();
    }

    if (value instanceof Long) {
      return ((Long) value).intValue();
    }

    if (value instanceof Integer) {
      return (Integer) value;
    }

    throw new InvalidArgumentsException(
        format("Unsupported value validation for parameter field, parameter field class: %s", value.getClass()));
  }
}
