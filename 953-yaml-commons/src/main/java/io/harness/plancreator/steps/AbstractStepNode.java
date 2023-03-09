/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.plancreator.policy.PolicyConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDC)
@JsonTypeInfo(use = NAME, property = "type", include = PROPERTY, visible = true)
public abstract class AbstractStepNode {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  String description;

  @ApiModelProperty(dataType = SwaggerConstants.STEP_WHEN_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<StepWhenCondition> when;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<String> skipCondition;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<List<String>> delegateSelectors;

  @ApiModelProperty(dataType = SwaggerConstants.STRATEGY_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  @VariableExpression(skipVariableExpression = true)
  @JsonProperty("strategy")
  ParameterField<StrategyConfig> strategy;
  @VariableExpression(skipVariableExpression = true) PolicyConfig enforce;

  @JsonIgnore public abstract String getType();

  @JsonIgnore public abstract StepSpecType getStepSpecType();
}
