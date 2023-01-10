/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;

import static io.harness.common.NGExpressionUtils.matchesInputSetPattern;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import static java.lang.String.format;

import io.harness.encryption.SecretRefData;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ExpressionResolverUtils {
  public static Integer resolveIntegerParameter(ParameterField<Integer> parameterField, Integer defaultValue) {
    if (parameterField == null || parameterField.isExpression() || parameterField.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(parameterField.fetchFinalValue().toString());
      } catch (Exception exception) {
        throw new ExpresionResolutionExecption(
            format("Invalid value %s, Value should be number", parameterField.fetchFinalValue().toString()));
      }
    }
  }

  public static Integer resolveIntegerParameterFromString(ParameterField<String> parameterField, Integer defaultValue) {
    if (parameterField == null || parameterField.isExpression() || parameterField.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(parameterField.fetchFinalValue().toString());
      } catch (Exception exception) {
        throw new ExpresionResolutionExecption(
            format("Invalid value %s, Value should be number", parameterField.fetchFinalValue().toString()));
      }
    }
  }

  public static String resolveStringParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<String> parameterField, boolean isMandatory) {
    if (parameterField == null) {
      if (isMandatory) {
        throw new ExpresionResolutionExecption(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    // It only checks input set pattern. Variable can be resolved on lite engine.
    if (parameterField.isExpression() && matchesInputSetPattern(parameterField.getExpressionValue())) {
      if (isMandatory) {
        throw new ExpresionResolutionExecption(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return null;
      }
    }

    return (String) parameterField.fetchFinalValue();
  }

  public static SecretRefData resolveSecretRefWithDefaultValue(String fieldName, String stepType, String stepIdentifier,
      ParameterField<SecretRefData> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new ExpresionResolutionExecption(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    return parameterField.getValue();
  }

  public static Map<String, String> resolveMapParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<Map<String, String>> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new ExpresionResolutionExecption(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    if (parameterField.isExpression()) {
      if (isMandatory) {
        throw new ExpresionResolutionExecption(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return null;
      }
    }

    Map<String, String> m = parameterField.getValue();
    if (isNotEmpty(m)) {
      m.remove(UUID_FIELD_NAME);
    }
    return m;
  }
}
