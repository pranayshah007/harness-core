/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.core.properties.CIProperties;
import io.harness.yaml.core.properties.NGProperties;
import io.harness.yaml.core.variables.NGServiceOverrides;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class NGVariablesUtils {
  public Map<String, Object> getMapOfVariables(List<NGVariable> variables, long expressionFunctorToken) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (NGVariable variable : variables) {
      if (variable instanceof SecretNGVariable) {
        SecretNGVariable secretNGVariable = (SecretNGVariable) variable;
        String secretValue = getSecretValue(secretNGVariable);
        if (secretValue != null) {
          String value = "${ngSecretManager.obtain(\"" + secretValue + "\", " + expressionFunctorToken + ")}";
          mapOfVariables.put(variable.getName(), value);
        }
      } else {
        ParameterField<?> value = getNonSecretValue(variable);
        if (value != null) {
          mapOfVariables.put(variable.getName(), value);
        }
      }
    }
    return mapOfVariables;
  }

  public Map<String, Object> getMapOfNGProperties(NGProperties ngProperties) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (ngProperties != null) {
      try {
        CIProperties ciProperties = ngProperties.getCi();
        mapOfVariables.put(YAMLFieldNameConstants.CI, ciProperties);
      } catch (Exception e) {
        log.warn("invalid ci properties");
      }
    }

    return mapOfVariables;
  }

  public Map<String, Object> getMapOfVariables(List<NGVariable> variables) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (NGVariable variable : variables) {
      if (variable instanceof SecretNGVariable) {
        SecretNGVariable secretNGVariable = (SecretNGVariable) variable;
        String secretValue = getSecretValue(secretNGVariable);
        if (secretValue != null) {
          String value = "<+secrets.getValue(\"" + secretValue + "\")>";
          mapOfVariables.put(variable.getName(), value);
        }
      } else {
        ParameterField<?> value = getNonSecretValue(variable);
        if (value != null) {
          mapOfVariables.put(variable.getName(), value);
        }
      }
    }
    return mapOfVariables;
  }

  public Map<String, Object> getMapOfServiceVariables(List<NGServiceOverrides> serviceOverrides) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(serviceOverrides)) {
      return mapOfVariables;
    }
    for (NGServiceOverrides variable : serviceOverrides) {
      mapOfVariables.put(variable.getServiceRef(), getMapOfVariables(variable.getVariables()));
    }
    return mapOfVariables;
  }

  private String getSecretValue(SecretNGVariable variable) {
    ParameterField<SecretRefData> value = (ParameterField<SecretRefData>) variable.getCurrentValue();
    if (ParameterField.isNull(value)
        || (!value.isExpression() && (value.getValue() == null || value.getValue().isNull()))) {
      if (variable.isRequired()) {
        throw new InvalidRequestException(
            String.format("Value not provided for required secret variable: %s", variable.getName()));
      }
      return null;
    }
    return value.isExpression() ? value.getExpressionValue() : value.getValue().toSecretRefStringValue();
  }

  private ParameterField<?> getNonSecretValue(NGVariable variable) {
    ParameterField<?> value = variable.getCurrentValue();
    if (ParameterField.isNull(value) || (!value.isExpression() && value.getValue() == null)) {
      if (variable.isRequired()) {
        throw new InvalidRequestException(
            String.format("Value not provided for required variable: %s", variable.getName()));
      }
      return null;
    }
    return value;
  }

  public Map<String, Object> applyVariableOverrides(
      Map<String, Object> originalVariablesMap, List<NGVariable> overrideVariables, long expressionFunctorToken) {
    if (EmptyPredicate.isEmpty(overrideVariables)) {
      return originalVariablesMap;
    }

    Map<String, Object> overrideVariablesMap = getMapOfVariables(overrideVariables, expressionFunctorToken);
    originalVariablesMap.putAll(overrideVariablesMap);
    return originalVariablesMap;
  }
}
