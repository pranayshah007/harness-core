/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.dto;

import io.harness.ng.core.variable.VariableValueType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = StringVariableConfigDTO.class, name = "String") })
public abstract class VariableConfigDTO {
  public static final String FIXED_SET_VALUE_FORMAT = "<+input>.allowedValues(%s)";
  public static final String REGEX_VALUE_FORMAT = "<+input>.regex(%s)";
  @Schema(description = VariableConstants.VARIABLE_VALUE_TYPE) @NotNull VariableValueType valueType;
  public abstract Object getValue();
  public void validate() {
    // no op implementation
  }
}
