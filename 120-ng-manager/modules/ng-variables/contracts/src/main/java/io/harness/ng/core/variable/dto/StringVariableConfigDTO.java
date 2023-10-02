/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.dto;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ng.core.variable.VariableValueType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.annotation.RegEx;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.regex.RegexUtil;

@OwnedBy(HarnessTeam.PL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "StringVariableConfigDTOKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StringVariableConfigDTO extends VariableConfigDTO {
  @Schema(description = VariableConstants.FIXED_VALUE) String fixedValue;
  @Schema(description = VariableConstants.DEFAULT_VALUE, hidden = true) String defaultValue;
  @Schema(description = VariableConstants.ALLOWED_VALUES, hidden = true) Set<String> allowedValues;
  @Schema(hidden = true) @ApiModelProperty(hidden = true) @RegEx String regex;

  @Override
  public Object getValue() {
    switch (getValueType()) {
      case FIXED:
        return fixedValue;
      case FIXED_SET:
        return String.format(FIXED_SET_VALUE_FORMAT, String.join(",", allowedValues));
      case REGEX:
        return String.format(REGEX_VALUE_FORMAT, regex);
      default:
        throw new UnknownEnumTypeException("variableValueType", getValueType().name());
    }
  }

  @Override
  public void validate() {
    switch (getValueType()) {
      case FIXED:
        validateForFixedValueType();
        break;
      case FIXED_SET:
      case REGEX:
        throw new UnsupportedOperationException(
            String.format("Value Type [%s] is not supported", getValueType().name()));
      default:
        throw new UnknownEnumTypeException("variableValueType", getValueType().name());
    }
  }

  private void validateForFixedValueType() {
    if (StringUtils.isBlank(fixedValue)) {
      throw new InvalidRequestException(String.format("Value for field [%s] must be provide when value type is [%s]",
          StringVariableConfigDTOKeys.fixedValue, VariableValueType.FIXED));
    }
  }

  private void validateForFixedSetValueType() {
    if (isEmpty(allowedValues)) {
      throw new InvalidRequestException(String.format("Value(s) for field [%s] must be provide when value type is [%s]",
          StringVariableConfigDTOKeys.allowedValues, VariableValueType.FIXED_SET));
    }
  }

  private void validateForRegexValueType() {
    if (isEmpty(regex)) {
      throw new InvalidRequestException(String.format("Value for field [%s] must be provide when value type is [%s]",
          StringVariableConfigDTOKeys.regex, VariableValueType.REGEX));
    }
    if (!RegexUtil.isRegex(regex)) {
      throw new InvalidRequestException(String.format("[%s] is not a valid regex", regex));
    }
  }
}
