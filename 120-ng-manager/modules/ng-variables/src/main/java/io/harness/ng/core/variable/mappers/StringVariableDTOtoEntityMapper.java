/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.mappers;

import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.entity.StringVariable;

public class StringVariableDTOtoEntityMapper {
  private StringVariableDTOtoEntityMapper() {}

  public static StringVariable toVariableEntity(StringVariableConfigDTO variableConfigDTO) {
    return StringVariable.builder()
        .fixedValue(variableConfigDTO.getFixedValue())
        .allowedValues(variableConfigDTO.getAllowedValues())
        .defaultValue(variableConfigDTO.getDefaultValue())
        .regex(variableConfigDTO.getRegex())
        .build();
  }
}
