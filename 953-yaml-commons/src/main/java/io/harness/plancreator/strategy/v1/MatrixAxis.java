/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.strategy.v1;

import static io.harness.common.NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN_FOR_MATRIX;

import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExpressionAxisConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatrixAxis {
  @ApiModelProperty(hidden = true) @Builder.Default Map<String, AxisConfig> axes = new LinkedHashMap<>();
  // This stores key/value pair in which value is an expression
  @ApiModelProperty(hidden = true)
  @Builder.Default
  Map<String, ExpressionAxisConfig> expressionAxes = new LinkedHashMap<>();

  // TODO BRIJESH: Check if the below Methods can be extracted out for common code for here and for MatrixConfig class.
  @JsonAnySetter
  void setAxis(String key, Object value) {
    try {
      populateMapsIfNull();
      if (key.equals(YamlNode.UUID_FIELD_NAME)) {
        return;
      }

      // Value can be either a list or string only. It cannot be other than that.
      if (value instanceof List) {
        handleList(key, value);
      } else if (value instanceof String) {
        handleString(key, value);
      }
    } catch (Exception ex) {
      throw new InvalidYamlException("Unable to parse Matrix yaml. Please ensure that it is in correct format", ex);
    }
  }

  private void populateMapsIfNull() {
    if (axes == null) {
      axes = new HashMap<>();
    }
    if (expressionAxes == null) {
      expressionAxes = new HashMap<>();
    }
  }

  private void handleList(String key, Object value) {
    List<String> stringList = new ArrayList<>();

    for (Object val : (List<Object>) value) {
      // If val is a map then we should treat it as an object else as a string
      if (val instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) val;
        map.remove(YamlNode.UUID_FIELD_NAME);
        stringList.add(JsonUtils.asJson(map));
      } else {
        stringList.add(String.valueOf(val));
      }
    }
    axes.put(key, new io.harness.plancreator.strategy.AxisConfig(ParameterField.createValueField(stringList)));
  }

  private void handleString(String key, Object value) {
    if (NGExpressionUtils.matchesPattern(GENERIC_EXPRESSIONS_PATTERN_FOR_MATRIX, value.toString())) {
      expressionAxes.put(
          key, new ExpressionAxisConfig(ParameterField.createExpressionField(true, (String) value, null, false)));
    } else {
      throw new InvalidYamlException(
          String.format("Value provided for axes [%s] is string. It should either be a List or an Expression.", key));
    }
  }

  @JsonValue
  public Map<String, Object> getJsonValue() {
    Map<String, Object> jsonMap = new HashMap<>(axes);
    jsonMap.putAll(expressionAxes);
    return jsonMap;
  }
}
