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
  public String validateStaticValues(Object templateObject, Object inputSetObject) {
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
        String error = validateStaticValues(templateObject, element);
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
    ParameterField<String> inputSetField;
    inputSetField = getInputSetParameterField(inputSetValue);
    String inputSetFieldValue;
    if (inputSetField == null || inputSetField.getValue() == null) {
      inputSetFieldValue = inputSetValue;
    } else {
      inputSetFieldValue = inputSetField.getValue();
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
              : String.format("The value provided %s does not match the required regex pattern", inputSetFieldValue);
        } else if (inputSetValidator.getValidatorType() == ALLOWED_VALUES) {
          String[] allowedValues = inputSetValidator.getParameters().split(", *");
          boolean matches = false;
          for (String allowedValue : allowedValues) {
            if (NGExpressionUtils.isRuntimeOrExpressionField(allowedValue)) {
              return error;
            } else if (allowedValue.equals(inputSetFieldValue)) {
              matches = true;
            }
          }
          String result = String.join(",", allowedValues);
          error = matches ? ""
                          : String.format("The value provided %s does not match any of the allowed values "
                                  + "[%s]",
                              inputSetFieldValue, result);
        }
      } catch (IOException e) {
        throw new InvalidRequestException(
            "Input set expression " + templateValue + " or " + inputSetFieldValue + " is not valid");
      }
    }
    return error;
  }

  private static ParameterField<String> getInputSetParameterField(String inputSetValue) {
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
