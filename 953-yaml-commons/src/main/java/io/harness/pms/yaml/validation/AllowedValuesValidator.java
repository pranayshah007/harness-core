/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This validator handles currentValue to be String, Number, List - of String or Number.
 * Examples -
 * ${input}.allowedValues(dev, prod) #render
 * ${input}.allowedValues(dev, ${env}, ${env2}, stage) #render
 *
 * ${input}.allowedValues(jexl(${env} == 'prod' ? 'dev, qa':'prod, stage')) #evaluate
 * ${input}.allowedValues(jexl(${env} == 'dev'?(${team} == 'a' ?'dev_a, dev_b':'dev_qa, dev_qb'):'prod,stage'))
 * #evaluate
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class AllowedValuesValidator implements RuntimeValidator {
  private final EngineExpressionResolver engineExpressionResolver;
  private final ExpressionMode expressionMode;
  private static final Pattern JEXL_PATTERN = Pattern.compile("jexl\\(");

  public AllowedValuesValidator(EngineExpressionResolver engineExpressionResolver, ExpressionMode expressionMode) {
    this.engineExpressionResolver = engineExpressionResolver;
    this.expressionMode = expressionMode;
  }

  @Override
  public RuntimeValidatorResponse isValidValue(Object currentValue, String parameters) {
    if (currentValue == null) {
      return RuntimeValidatorResponse.builder().errorMessage("Current value is null").build();
    }

    boolean isJexlExpression = false;

    // Check whether this is jexl() or not. If yes, get content between braces.
    if (ExpressionUtils.containsPattern(JEXL_PATTERN, parameters)) {
      isJexlExpression = true;
      parameters = JEXL_PATTERN.split(parameters)[1];
      parameters = parameters.substring(0, parameters.length() - 1);
    }

    if (isJexlExpression) {
      parameters = (String) engineExpressionResolver.evaluateExpression(parameters, expressionMode);
    } else {
      parameters = engineExpressionResolver.renderExpression(parameters, expressionMode);
    }

    List<String> parametersList = AllowedValuesHelper.split(parameters);

    if (checkIfPrimitiveType(currentValue)) {
      return checkIsPrimitiveTypeValid(currentValue, parametersList);
    } else if (currentValue instanceof List) {
      List<?> currentValuesList = (List<?>) currentValue;
      if (currentValuesList.isEmpty()) {
        return RuntimeValidatorResponse.builder()
            .errorMessage("AllowedValues do not handle value of type: " + currentValue.getClass())
            .build();
      }
      if (!checkIfPrimitiveType(currentValuesList.get(0))) {
        return RuntimeValidatorResponse.builder()
            .errorMessage("AllowedValues do not handle value of type: " + currentValuesList.get(0).getClass())
            .build();
      }
      for (Object value : currentValuesList) {
        RuntimeValidatorResponse response = checkIsPrimitiveTypeValid(value, parametersList);
        if (!response.isValid()) {
          return response;
        }
      }
      return RuntimeValidatorResponse.builder().isValid(true).build();
    } else {
      return RuntimeValidatorResponse.builder()
          .errorMessage("AllowedValues do not handle value of type: " + currentValue.getClass())
          .build();
    }
  }

  private boolean checkIfPrimitiveType(Object currentValue) {
    return currentValue instanceof String || currentValue instanceof Number;
  }

  private RuntimeValidatorResponse checkIsPrimitiveTypeValid(Object currentValue, List<String> parametersList) {
    if (currentValue instanceof String) {
      return isStringValueAllowed((String) currentValue, parametersList);
    } else if (currentValue instanceof Number) {
      return isNumberValueAllowed(String.valueOf(currentValue), parametersList);
    } else {
      return RuntimeValidatorResponse.builder()
          .errorMessage("AllowedValues do not handle value of type: " + currentValue.getClass())
          .build();
    }
  }

  private RuntimeValidatorResponse isStringValueAllowed(String currentValue, List<String> parametersList) {
    Set<String> parametersSet = parametersList.stream().map(String::trim).collect(Collectors.toSet());
    List<String> inputValues = AllowedValuesHelper.split(currentValue.trim());
    return isAllowedStringsFromSet(inputValues, parametersSet);
  }

  private RuntimeValidatorResponse isNumberValueAllowed(String currentValue, List<String> parametersList) {
    Set<Double> parametersSet = parametersList.stream().map(Double::valueOf).collect(Collectors.toSet());
    return isAllowedNumbersFromSet(Double.valueOf(currentValue), parametersSet);
  }

  private RuntimeValidatorResponse isAllowedNumbersFromSet(Double currentValue, Set<Double> allowedValues) {
    if (!allowedValues.contains(currentValue)) {
      return RuntimeValidatorResponse.builder()
          .errorMessage("Current value " + currentValue + " is not in allowed values list")
          .build();
    }
    return RuntimeValidatorResponse.builder().isValid(true).build();
  }

  private RuntimeValidatorResponse isAllowedStringsFromSet(List<String> inputValues, Set<String> allowedValues) {
    boolean isValid = true;
    List<String> invalidValues = new ArrayList<>();
    for (String inputValue : inputValues) {
      if (!allowedValues.contains(inputValue)) {
        isValid = false;
        invalidValues.add(inputValue);
      }
    }
    if (!isValid) {
      return RuntimeValidatorResponse.builder()
          .errorMessage("Current values "
              + invalidValues.stream().map(s -> "\\'" + s + "\\'").collect(Collectors.joining(", "))
              + " are not in allowed values list")
          .build();
    }
    return RuntimeValidatorResponse.builder().isValid(true).build();
  }
}
