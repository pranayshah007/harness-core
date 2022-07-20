/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExpressionResultUtils {
  public static final String STRING = "String";
  public static final String BOOLEAN = "Boolean";
  public static final String INTEGER = "Integer";
  public static final String BYTES = "Bytes";
  public static final String CHARACTER = "Character";
  public static final String SHORT = "Short";
  public static final String LONG = "Long";
  public static final String DOUBLE = "Double";
  public static final String FLOAT = "Float";
  public static final String DATE = "Date";
  public static final String CLASS = "Class";
  public static final String UUID = "Uuid";
  public static final String URI = "Uri";

  public static Map<Class, String> primitivesMap = new HashMap() {
    {
      put(String.class.getSimpleName(), STRING);
      put(Integer.class.getSimpleName(), INTEGER);
      put(Boolean.class.getSimpleName(), BOOLEAN);
      put(Byte.class.getSimpleName(), BYTES);
      put(Character.class.getSimpleName(), CHARACTER);
      put(Short.class.getSimpleName(), SHORT);
      put(Long.class.getSimpleName(), LONG);
      put(Double.class.getSimpleName(), DOUBLE);
      put(Float.class.getSimpleName(), FLOAT);
      put(Date.class.getSimpleName(), DATE);
      put(Class.class.getSimpleName(), CLASS);
      put(java.util.UUID.class.getSimpleName(), UUID);
      put(java.net.URI.class.getSimpleName(), URI);
    }
  };

  public static Object getPrimitiveResponse(String value, String clazz) throws ClassNotFoundException {
    switch (ExpressionResultUtils.primitivesMap.get(clazz)) {
      case ExpressionResultUtils.INTEGER:
        return Integer.parseInt(value);
      case ExpressionResultUtils.BOOLEAN:
        return Boolean.parseBoolean(value);
      case ExpressionResultUtils.BYTES:
        return Byte.valueOf(value);
      case ExpressionResultUtils.CHARACTER:
        return value.charAt(0);
      case ExpressionResultUtils.LONG:
        return Long.valueOf(value);
      case ExpressionResultUtils.SHORT:
        return Short.valueOf(value);
      case ExpressionResultUtils.DOUBLE:
        return Double.valueOf(value);
      case ExpressionResultUtils.FLOAT:
        return Float.valueOf(value);
      case ExpressionResultUtils.DATE:
        return Date.from(Instant.parse(value));
      case ExpressionResultUtils.CLASS:
        return Class.forName(value);
      case ExpressionResultUtils.UUID:
        return java.util.UUID.fromString(value);
      case ExpressionResultUtils.URI:
        return java.net.URI.create(value);
      default:
        return value;
    }
  }
}
