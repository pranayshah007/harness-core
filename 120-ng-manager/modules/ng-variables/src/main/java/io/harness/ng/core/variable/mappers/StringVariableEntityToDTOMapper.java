/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.mappers;

import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.entity.StringVariable;

public class StringVariableEntityToDTOMapper {
  private StringVariableEntityToDTOMapper() {}

  public static StringVariableConfigDTO createVariableDTO(StringVariable variable) {
    return StringVariableConfigDTO.builder()
        .fixedValue(variable.getFixedValue())
        .allowedValues(variable.getAllowedValues())
        .defaultValue(variable.getDefaultValue())
        .regex(variable.getRegex())
        .valueType(variable.getValueType())
        .build();
  }
}
