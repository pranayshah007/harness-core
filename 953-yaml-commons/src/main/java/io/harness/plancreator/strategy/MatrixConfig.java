/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.common.NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN_FOR_MATRIX;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.list;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.MatrixConfig")
public class MatrixConfig implements MatrixConfigInterface {
  private static String EXCLUDE_KEYWORD = "exclude";

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // This stores all key/value pair except expressions defined as axis in matrix
  @ApiModelProperty(hidden = true) @Builder.Default Map<String, AxisConfig> axes = new LinkedHashMap<>();

  // This stores key/value pair in which value is an expression
  @ApiModelProperty(hidden = true)
  @Builder.Default
  Map<String, ExpressionAxisConfig> expressionAxes = new LinkedHashMap<>();

  @YamlSchemaTypes(value = {runtime, list}) ParameterField<List<ExcludeConfig>> exclude;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @JsonProperty("maxConcurrency")
  @Min(value = 0)
  @YamlSchemaTypes(value = {expression})
  ParameterField<Integer> maxConcurrency;
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
    axes.put(key, new AxisConfig(ParameterField.createValueField(stringList)));
  }

  private void handleString(String key, Object value) {
    if (NGExpressionUtils.matchesPattern(GENERIC_EXPRESSIONS_PATTERN_FOR_MATRIX, value.toString())) {
      expressionAxes.put(
          key, new ExpressionAxisConfig(ParameterField.createExpressionField(true, (String) value, null, false)));
      return;
    }
    try {
      JsonNode jsonNode = YamlUtils.readTree(value.toString()).getNode().getCurrJsonNode();
      // Check if the string axis value is actually a list. And handle.
      if (jsonNode instanceof ArrayNode) {
        handleList(key, YamlUtils.read(value.toString(), ArrayList.class));
        return;
      }
    } catch (IOException e) {
      // Ignore this exception. Exception is being thrown in the end of this method.
    }
    throw new InvalidYamlException(
        String.format("Value provided for axes [%s] is string. It should either be a List or an Expression.", key));
  }

  @JsonValue
  public Map<String, Object> getJsonValue() {
    Map<String, Object> jsonMap = new HashMap<>(axes);
    jsonMap.putAll(expressionAxes);
    jsonMap.put("exclude", exclude);
    jsonMap.put("maxConcurrency", maxConcurrency);
    return jsonMap;
  }
}
