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
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.core.properties.CIProperties;
import io.harness.yaml.core.properties.NGProperties;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

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
          String value = fetchSecretExpressionWithExpressionToken(secretValue, expressionFunctorToken);
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

  public Map<String, Object> getMapOfVariablesWithoutSecretExpression(List<NGVariable> variables) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (NGVariable variable : variables) {
      if (variable instanceof SecretNGVariable) {
        // value is the name of the output variable, not a secret ref
        SecretNGVariable secretNGVariable = (SecretNGVariable) variable;
        String secretValue = getSecretValue(secretNGVariable);
        if (secretValue != null) {
          mapOfVariables.put(variable.getName(), ParameterField.createValueField(secretValue));
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
          String value = fetchSecretExpression(secretValue);
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

  public Set<String> getSetOfSecretVars(List<NGVariable> variables) {
    Set<String> secretVars = new HashSet<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return secretVars;
    }
    for (NGVariable variable : variables) {
      if (variable instanceof SecretNGVariable) {
        SecretNGVariable secretNGVariable = (SecretNGVariable) variable;
        secretVars.add(secretNGVariable.getName());
      }
    }
    return secretVars;
  }

  public Set<String> getSetOfVars(List<NGVariable> variables) {
    Set<String> vars = new HashSet<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return vars;
    }
    for (NGVariable variable : variables) {
      vars.add(variable.getName());
    }
    return vars;
  }

  public String fetchSecretExpression(String secretValue) {
    /*
    if secretValue is a string, then add it with quotes else add it as a variable
     */
    if (EngineExpressionEvaluator.hasExpressions(secretValue)) {
      return "<+secrets.getValue(" + secretValue + ")>";
    }
    return "<+secrets.getValue(\"" + secretValue + "\")>";
  }

  public String fetchSecretExpressionWithExpressionToken(String secretValue, long expressionFunctorToken) {
    /*
    if secretValue is a string, then add it with quotes else add it as a variable
     */
    if (EngineExpressionEvaluator.hasExpressions(secretValue)) {
      return "${ngSecretManager.obtain(" + secretValue + ", " + expressionFunctorToken + ")}";
    }
    return "${ngSecretManager.obtain(\"" + secretValue + "\", " + expressionFunctorToken + ")}";
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
    if (variable.isRequired() && !value.isExpression() && ObjectUtils.isEmpty(value.getValue())) {
      throw new InvalidRequestException(
          String.format("Value not provided for required variable: %s", variable.getName()));
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

  public Map<String, String> getStringMapVariables(List<NGVariable> variables, long expressionFunctorToken) {
    Map<String, Object> inputVariables = getMapOfVariables(variables, expressionFunctorToken);
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }
}
