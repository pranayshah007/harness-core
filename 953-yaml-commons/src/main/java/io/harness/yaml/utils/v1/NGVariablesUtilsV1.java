/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils.v1;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariableV1;
import io.harness.yaml.core.variables.v1.SecretNGVariableV1;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class NGVariablesUtilsV1 {
  public Map<String, Object> getMapOfVariables(Map<String, NGVariableV1> variables, long expressionFunctorToken) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      if (entry.getValue() instanceof SecretNGVariableV1) {
        SecretNGVariableV1 secretNGVariable = (SecretNGVariableV1) entry.getValue();
        String secretValue = getSecretValue(entry.getKey(), secretNGVariable);
        if (secretValue != null) {
          String value = fetchSecretExpressionWithExpressionToken(secretValue, expressionFunctorToken);
          mapOfVariables.put(entry.getKey(), value);
        }
      } else {
        ParameterField<?> value = getNonSecretValue(entry.getKey(), entry.getValue());
        if (value != null) {
          mapOfVariables.put(entry.getKey(), value);
        }
      }
    }
    return mapOfVariables;
  }

  public Map<String, Object> getMapOfVariablesWithoutSecretExpression(Map<String, NGVariableV1> variables) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      if (entry.getValue() instanceof SecretNGVariableV1) {
        // value is the name of the output variable, not a secret ref
        SecretNGVariableV1 secretNGVariable = (SecretNGVariableV1) entry.getValue();
        String secretValue = getSecretValue(entry.getKey(), secretNGVariable);
        if (secretValue != null) {
          mapOfVariables.put(entry.getKey(), ParameterField.createValueField(secretValue));
        }
      } else {
        ParameterField<?> value = getNonSecretValue(entry.getKey(), entry.getValue());
        if (value != null) {
          mapOfVariables.put(entry.getKey(), value);
        }
      }
    }
    return mapOfVariables;
  }

  public Map<String, Object> getMapOfVariables(Map<String, NGVariableV1> variables) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return mapOfVariables;
    }
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      if (entry.getValue() instanceof SecretNGVariableV1) {
        SecretNGVariableV1 secretNGVariable = (SecretNGVariableV1) entry.getValue();
        String secretValue = getSecretValue(entry.getKey(), secretNGVariable);
        if (secretValue != null) {
          String value = fetchSecretExpression(secretValue);
          mapOfVariables.put(entry.getKey(), value);
        }
      } else {
        ParameterField<?> value = getNonSecretValue(entry.getKey(), entry.getValue());
        if (value != null) {
          mapOfVariables.put(entry.getKey(), value);
        }
      }
    }
    return mapOfVariables;
  }

  public Set<String> getSetOfSecretVars(Map<String, NGVariableV1> variables) {
    Set<String> secretVars = new HashSet<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return secretVars;
    }
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      if (entry.getValue() instanceof SecretNGVariableV1) {
        SecretNGVariableV1 secretNGVariable = (SecretNGVariableV1) entry.getValue();
        secretVars.add(entry.getKey());
      }
    }
    return secretVars;
  }

  public Set<String> getSetOfVars(Map<String, NGVariableV1> variables) {
    Set<String> vars = new HashSet<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return vars;
    }
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      vars.add(entry.getKey());
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

  private String getSecretValue(String name, SecretNGVariableV1 variable) {
    ParameterField<SecretRefData> value = (ParameterField<SecretRefData>) variable.getCurrentValue();
    if (ParameterField.isNull(value)
        || (!value.isExpression() && (value.getValue() == null || value.getValue().isNull()))) {
      if (variable.isRequired()) {
        throw new InvalidRequestException(String.format("Value not provided for required secret variable: %s", name));
      }
      return null;
    }
    return value.isExpression() ? value.getExpressionValue() : value.getValue().toSecretRefStringValue();
  }

  private ParameterField<?> getNonSecretValue(String name, NGVariableV1 variable) {
    ParameterField<?> value = variable.getCurrentValue();
    if (ParameterField.isNull(value) || (!value.isExpression() && value.getValue() == null)) {
      if (variable.isRequired()) {
        throw new InvalidRequestException(String.format("Value not provided for required variable: %s", name));
      }
      return null;
    }
    if (variable.isRequired() && !value.isExpression() && ObjectUtils.isEmpty(value.getValue())) {
      throw new InvalidRequestException(String.format("Value not provided for required variable: %s", name));
    }
    return value;
  }

  public Map<String, Object> applyVariableOverrides(Map<String, Object> originalVariablesMap,
      Map<String, NGVariableV1> overrideVariables, long expressionFunctorToken) {
    if (EmptyPredicate.isEmpty(overrideVariables)) {
      return originalVariablesMap;
    }

    Map<String, Object> overrideVariablesMap = getMapOfVariables(overrideVariables, expressionFunctorToken);
    originalVariablesMap.putAll(overrideVariablesMap);
    return originalVariablesMap;
  }

  public Map<String, String> getStringMapVariables(Map<String, NGVariableV1> variables, long expressionFunctorToken) {
    Map<String, Object> inputVariables = getMapOfVariables(variables, expressionFunctorToken);
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.fetchFinalValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.fetchFinalValue().toString());
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
