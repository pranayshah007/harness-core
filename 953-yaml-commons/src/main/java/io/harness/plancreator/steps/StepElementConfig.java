/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
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
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("stepElementConfig")
@OwnedBy(CDC)
@RecasterAlias("io.harness.plancreator.steps.StepElementConfig")
// @deprecated: Use the AbstractStepNode instead.
@Deprecated
public class StepElementConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  String description;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  ParameterField<Timeout> timeout;
  @VariableExpression(skipVariableExpression = true) PolicyConfig enforce;
  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;

  @ApiModelProperty(dataType = SwaggerConstants.STRATEGY_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  @JsonProperty("strategy")
  ParameterField<StrategyConfig> strategy;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<String> skipCondition;

  @ApiModelProperty(dataType = SwaggerConstants.STEP_WHEN_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<StepWhenCondition> when;

  @NotNull String type;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  io.harness.yaml.core.StepSpecType stepSpecType;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<List<String>> delegateSelectors;

  @Builder
  public StepElementConfig(String uuid, String identifier, String name, String description,
      ParameterField<Timeout> timeout, ParameterField<List<FailureStrategyConfig>> failureStrategies, String type,
      StepSpecType stepSpecType, ParameterField<String> skipCondition, ParameterField<StepWhenCondition> when,
      ParameterField<List<String>> delegateSelectors, PolicyConfig enforce) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.timeout = timeout;
    this.failureStrategies = failureStrategies;
    this.type = type;
    this.stepSpecType = stepSpecType;
    this.skipCondition = skipCondition;
    this.delegateSelectors = delegateSelectors;
    this.when = when;
    this.enforce = enforce;
  }
}
