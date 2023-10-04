/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.core.timeout.Timeout;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ClassUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class ParameterFieldUtils {
  // Handling primitive types, wrappers, enum, and Timeout when value class of document field doesn't match the class of
  // finalValue
  // This handling is for cases like when an integer field is assigned an expression of string pipeline var with integer
  // value
  Object getCastedFinalValueForPrimitiveTypesAndWrappers(Object finalValue, ParameterDocumentField field) {
    if (field.getValueClass() == null || finalValue == null) {
      return finalValue;
    }
    try {
      Class<?> fieldClass = Class.forName(field.getValueClass());
      if ((ClassUtils.isPrimitiveOrWrapper(fieldClass) || fieldClass.equals(Timeout.class) || fieldClass.isEnum())
          && !fieldClass.isAssignableFrom(finalValue.getClass())) {
        if (fieldClass.equals(Integer.class)) {
          finalValue = getIntegerValue(finalValue);
        } else if (fieldClass.equals(Double.class)) {
          finalValue = getDoubleValue(finalValue);
        } else if (fieldClass.equals(Boolean.class)) {
          finalValue = getBooleanValue(finalValue);
        } else if (fieldClass.equals(Timeout.class)) {
          finalValue = getTimeoutValue(finalValue);
        } else if (fieldClass.isEnum()) {
          finalValue = getEnumValue(finalValue, fieldClass);
        }
      }
    } catch (Exception ex) {
      log.warn(
          String.format(
              "[ParameterDocumentFieldProcessor] Exception in casting newValue of type %s into parameter field of type %s",
              finalValue.getClass().toString(), field.getValueClass()),
          ex);
    }
    return finalValue;
  }

  private Object getIntegerValue(Object finalValue) {
    if (finalValue.getClass().equals(String.class)) {
      finalValue = Integer.parseInt(finalValue.toString());
    } else if (finalValue.getClass().equals(Double.class)) {
      finalValue = ((Double) finalValue).intValue();
    } else if (finalValue.getClass().equals(Long.class)) {
      finalValue = ((Long) finalValue).intValue();
    }
    return finalValue;
  }

  private Object getDoubleValue(Object finalValue) {
    if (finalValue.getClass().equals(String.class)) {
      finalValue = Double.valueOf((String) finalValue);
    } else if (finalValue.getClass().equals(Integer.class)) {
      finalValue = new Double((Integer) finalValue);
    } else if (finalValue.getClass().equals(Long.class)) {
      finalValue = new Double((Long) finalValue);
    }
    return finalValue;
  }

  private Object getBooleanValue(Object finalValue) {
    if (finalValue.getClass().equals(String.class)) {
      Object booleanValue = BooleanUtils.toBooleanObject((String) finalValue);
      finalValue = booleanValue == null ? finalValue : booleanValue;
    }
    return finalValue;
  }

  private Object getTimeoutValue(Object finalValue) {
    if (finalValue.getClass().equals(String.class)) {
      finalValue = Timeout.fromString((String) finalValue);
    }
    return finalValue;
  }

  private Object getEnumValue(Object finalValue, Class<?> fieldClass) {
    if (finalValue.getClass().equals(String.class)) {
      for (Object object : fieldClass.getEnumConstants()) {
        if (((Enum) object).name().equalsIgnoreCase((String) finalValue)
            || object.toString().equalsIgnoreCase((String) finalValue)) {
          return object;
        }
      }
    }
    return finalValue;
  }
}
