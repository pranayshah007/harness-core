/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_END_ESC;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_START_ESC;

import io.harness.annotations.dev.OwnedBy;
@OwnedBy(DX)
public class SchemaConstants {
  public static final String PIPELINE_NODE = "pipeline";
  public static final String STAGES_NODE = "stages";
  public static final String PARALLEL_NODE = "parallel";
  public static final String IF_NODE = "if";
  public static final String THEN_NODE = "then";
  public static final String ALL_OF_NODE = "allOf";
  public static final String ONE_OF_NODE = "oneOf";
  public static final String ANY_OF_NODE = "anyOf";
  public static final String PROPERTIES_NODE = "properties";
  public static final String DEFINITIONS_NODE = "definitions";
  public static final String EXECUTION_WRAPPER_CONFIG_NODE = "ExecutionWrapperConfig";
  public static final String STEP_NODE = "step";
  public static final String SCHEMA_NODE = "$schema";
  public static final String REF_NODE = "$ref";
  public static final String CONST_NODE = "const";
  public static final String DEFINITIONS_STRING_PREFIX = "#/" + DEFINITIONS_NODE + "/";
  public static final String DEFINITIONS_NAMESPACE_STRING_PATTERN = "#/" + DEFINITIONS_NODE + "/%s/%s";
  public static final String JSON_SCHEMA_7 = "http://json-schema.org/draft-07/schema#";
  public static final String ENUM_NODE = "enum";
  public static final String REQUIRED_NODE = "required";
  public static final String ADDITIONAL_PROPERTIES_NODE = "additionalProperties";
  public static final String TYPE_NODE = "type";
  public static final String STRING_TYPE_NODE = "string";
  public static final String NUMBER_TYPE_NODE = "number";
  public static final String INTEGER_TYPE_NODE = "integer";
  public static final String BOOL_TYPE_NODE = "boolean";
  public static final String OBJECT_TYPE_NODE = "object";
  public static final String ARRAY_TYPE_NODE = "array";
  public static final String ITEMS_NODE = "items";
  public static final String PATTERN_NODE = "pattern";
  public static final String MIN_LENGTH_NODE = "minLength";
  public static final String RUNTIME_INPUT_PATTERN = "^<\\+input>(\\.(allowedValues|regex)\\(.+?\\))*$";
  public static final String EXPRESSION_PATTERN = EXPR_START_ESC + "([a-zA-Z]\\w*\\.?)*([a-zA-Z]\\w*)" + EXPR_END_ESC;
  public static final String SPEC_NODE = "spec";
  public static final String STAGE_ELEMENT_WRAPPER_CONFIG = "StageElementWrapperConfig";
  public static final String STAGE_NODE = "stage";
}
