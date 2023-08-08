/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.utils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public class ParameterFieldUtils {
  public static String getValueFromParameterFieldWithInputSetValidator(String value, String fqnForNode) {
    if (EmptyPredicate.isEmpty(value)) {
      return value;
    }
    try {
      ParameterField<?> parameterField = YamlPipelineUtils.read(value, ParameterField.class);
      if (parameterField.getInputSetValidator() == null) {
        return value;
      }
      return parameterField.getValue().toString();
    } catch (IOException e) {
      if (EmptyPredicate.isEmpty(value)) {
        throw new InvalidRequestException("Value for the field at path [" + fqnForNode + "] is not provided!");
      } else {
        throw new InvalidRequestException(value + " is not a valid value for runtime input");
      }
    }
  }

  /**
   * @param p Parameter field whose value is either a boolean or a string
   * @return return a boolean derived from the above value
   * @throws InvalidRequestException if the parameter field value cannot be converted to a boolean value
   */
  public static boolean getBooleanValue(ParameterField<?> p) {
    if (ParameterField.isNull(p)) {
      throw new InvalidRequestException("Value for field is null");
    }

    if (p.isExpression()) {
      throw new InvalidRequestException("Value for field is an expression");
    }

    final Object o = p.fetchFinalValue();

    if (o instanceof Boolean) {
      return (boolean) o;
    } else if (o instanceof String) {
      return Boolean.parseBoolean((String) o);
    } else {
      throw new InvalidRequestException(String.format("Value for field [%s] is not a boolean", o));
    }
  }
}
