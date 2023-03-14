/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.variables;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.numberString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.common.NGExpressionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.validator.NGVariableName;
import io.harness.visitor.helpers.variables.NumberVariableVisitorHelper;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName(NGVariableConstants.NUMBER_TYPE)
@SimpleVisitorHelper(helperClass = NumberVariableVisitorHelper.class)
@TypeAlias("io.harness.yaml.core.variables.NumberNGVariable")
@OwnedBy(CDC)
public class NumberNGVariable implements NGVariable {
  @NGVariableName
  @Pattern(regexp = NGRegexValidatorConstants.VARIABLE_NAME_PATTERN)
  @VariableExpression(skipVariableExpression = true)
  String name;
  @ApiModelProperty(allowableValues = NGVariableConstants.NUMBER_TYPE)
  @VariableExpression(skipVariableExpression = true)
  NGVariableType type = NGVariableType.NUMBER;
  @NotNull
  @YamlSchemaTypes({numberString})
  @ApiModelProperty(dataType = SwaggerConstants.DOUBLE_CLASSPATH)
  @VariableExpression(policy = REGULAR_WITH_CUSTOM_FIELD, skipInnerObjectTraversal = true)
  ParameterField<Double> value;
  @VariableExpression(skipVariableExpression = true) String description;
  @VariableExpression(skipVariableExpression = true) boolean required;
  @VariableExpression(skipVariableExpression = true) @JsonProperty("default") Double defaultValue;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @JsonIgnore
  @Override
  public ParameterField<?> getCurrentValue() {
    return ParameterField.isNull(value)
            || (value.isExpression() && NGExpressionUtils.matchesInputSetPattern(value.getExpressionValue()))
        ? ParameterField.createValueField(defaultValue)
        : value;
  }
  @JsonIgnore
  public ParameterField<?> fetchValue() {
    return value;
  }
}
