/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import static io.harness.beans.InputSetValidatorType.ALLOWED_VALUES;
import static io.harness.beans.InputSetValidatorType.REGEX;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
@Slf4j
public class RuntimeInputValuesValidator {
  public String validateStaticValues(Object templateObject, Object inputSetObject, String expressionFqn) {
    /*
      This if block is to validate static values inside an array of primitive values.
      For example, the pipeline can have something like `a: <+input>.regex(a.*a)`, and the input set provides
      the following value `a: [austria, australia, india]`. This block checks each element in the list against the
      regex provided in the pipeline yaml.
     */
    if (inputSetObject instanceof ArrayNode) {
      ArrayNode inputSetValueArray = (ArrayNode) inputSetObject;
      List<JsonNode> invalidJsonNodes = new ArrayList<>();
      for (JsonNode element : inputSetValueArray) {
        String error = validateStaticValues(templateObject, element, expressionFqn);
        if (EmptyPredicate.isNotEmpty(error)) {
          invalidJsonNodes.add(element);
        }
      }
      if (invalidJsonNodes.isEmpty()) {
        return "";
      }
      List<String> invalidValues = invalidJsonNodes.stream().map(JsonNode::textValue).collect(Collectors.toList());
      return "Following values don't match the provided input set validator: " + invalidValues;
    }
    String error = "";
    String templateValue = ((JsonNode) templateObject).asText();
    String inputSetValue = ((JsonNode) inputSetObject).asText();
    ParameterField<?> inputSetField;
    inputSetField = getInputSetParameterField(inputSetValue);
    String inputSetFieldValue;
    if (inputSetField == null || inputSetField.getValue() == null) {
      inputSetFieldValue = inputSetValue;
    } else {
      inputSetFieldValue = inputSetField.getValue().toString();
    }

    if (NGExpressionUtils.matchesInputSetPattern(templateValue)
        && !NGExpressionUtils.isRuntimeOrExpressionField(inputSetFieldValue)) {
      try {
        ParameterField<?> templateField = YamlUtils.read(templateValue, ParameterField.class);
        if (templateField.getInputSetValidator() == null) {
          return error;
        }
        InputSetValidator inputSetValidator = templateField.getInputSetValidator();
        if (inputSetValidator.getValidatorType() == REGEX) {
          boolean matchesPattern =
              NGExpressionUtils.matchesPattern(Pattern.compile(inputSetValidator.getParameters()), inputSetFieldValue);
          error = matchesPattern
              ? ""
              : String.format("The value provided for [%s: %s] does not match the required regex pattern",
                  expressionFqn, inputSetFieldValue);
        } else if (inputSetValidator.getValidatorType() == ALLOWED_VALUES) {
          List<String> allowedValues = AllowedValuesHelper.split(inputSetValidator.getParameters());
          List<String> inputFields = AllowedValuesHelper.split(inputSetFieldValue);
          List<String> invalidInputs = new ArrayList<>();
          boolean isValid = true;
          for (String inputField : inputFields) {
            boolean matches = false;
            for (String allowedValue : allowedValues) {
              if (NGExpressionUtils.isRuntimeOrExpressionField(allowedValue)) {
                return error;
              } else if (allowedValue.equals(inputField)) {
                matches = true;
              }
            }
            if (!matches) {
              invalidInputs.add(inputField);
              isValid = false;
            }
          }
          String result = allowedValues.stream().map(s -> "\\'" + s + "\\'").collect(Collectors.joining(", "));
          String invalidInputValues =
              invalidInputs.stream().map(s -> "\\'" + s + "\\'").collect(Collectors.joining(", "));

          error = isValid ? ""
                          : String.format("The values provided for %s: [%s] do not match any of the allowed values "
                                  + "[%s]",
                              expressionFqn, invalidInputValues, result);
        }
      } catch (IOException e) {
        throw new InvalidRequestException(
            "Input set expression " + templateValue + " or " + inputSetFieldValue + " is not valid");
      }
    }
    return error;
  }

  public boolean validateInputValues(Object sourceObject, Object objectToValidate) {
    /*
      This if block is to validate static values inside an array of primitive values.
      For example, the pipeline can have something like `a: <+input>.regex(a.*a)`, and the input set provides
      the following value `a: [austria, australia, india]`. This block checks each element in the list against the
      regex provided in the pipeline yaml.
     */
    if (objectToValidate instanceof ArrayNode) {
      ArrayNode objectToValidateValueArray = (ArrayNode) objectToValidate;
      for (JsonNode element : objectToValidateValueArray) {
        if (!validateInputValues(sourceObject, element)) {
          return false;
        }
      }
      return true;
    }

    String sourceValue = ((JsonNode) sourceObject).asText();
    String objectToValidateValue = ((JsonNode) objectToValidate).asText();
    ParameterField<?> inputSetField;
    inputSetField = getInputSetParameterField(objectToValidateValue);
    String objectToValidateFieldValue;
    if (inputSetField == null || inputSetField.obtainValue() == null) {
      objectToValidateFieldValue = objectToValidateValue;
    } else {
      objectToValidateFieldValue = inputSetField.getValue().toString();
    }

    if (NGExpressionUtils.matchesInputSetPattern(sourceValue)) {
      try {
        ParameterField<?> sourceField = YamlUtils.read(sourceValue, ParameterField.class);
        if (NGExpressionUtils.matchesInputSetPattern(objectToValidateFieldValue)) {
          // RECONCILIATION LOGIC
          if (sourceField.getInputSetValidator() != null) {
            if (sourceField.getDefaultValue() != null) {
              return sourceField.getInputSetValidator().equals(inputSetField.getInputSetValidator())
                  && sourceField.getDefaultValue().equals(inputSetField.getDefaultValue());
            } else {
              return sourceField.getInputSetValidator().equals(inputSetField.getInputSetValidator());
            }
          } else {
            if (sourceField.getDefaultValue() != null) {
              return sourceField.getDefaultValue().equals(inputSetField.getDefaultValue());
            }
            return true;
          }
        } else if (EngineExpressionEvaluator.hasExpressions(objectToValidateFieldValue)) {
          // if linked input is expression, return true.
          return true;
        } else {
          if (sourceField.getInputSetValidator() == null) {
            return true;
          }

          InputSetValidator inputSetValidator = sourceField.getInputSetValidator();
          if (inputSetValidator.getValidatorType() == REGEX) {
            return NGExpressionUtils.matchesPattern(
                Pattern.compile(inputSetValidator.getParameters()), objectToValidateFieldValue);
          } else if (inputSetValidator.getValidatorType() == ALLOWED_VALUES) {
            String[] allowedValues = inputSetValidator.getParameters().split(", *");
            for (String allowedValue : allowedValues) {
              if (NGExpressionUtils.isRuntimeOrExpressionField(allowedValue)
                  || allowedValue.equals(objectToValidateFieldValue)) {
                return true;
              }
            }
            return false;
          }
        }
      } catch (IOException e) {
        throw new InvalidRequestException(
            "Input set expression " + sourceValue + " or " + objectToValidateFieldValue + " is not valid");
      }
    }

    return true;
  }

  public static ParameterField<String> getInputSetParameterField(String inputSetValue) {
    if (EmptyPredicate.isEmpty(inputSetValue)) {
      return null;
    }
    ParameterField<String> inputSetField;
    try {
      inputSetField = YamlUtils.read(inputSetValue, new TypeReference<ParameterField<String>>() {});
    } catch (IOException e) {
      log.error(String.format("Error mapping input set value %s to ParameterField class", inputSetValue), e);
      return null;
    }
    return inputSetField;
  }
}
