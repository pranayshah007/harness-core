/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.stage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.validation.OneOfSet;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.when.beans.StageWhenCondition;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@TypeAlias("stageElementConfig")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OneOfSet(fields = {"skipCondition, when, failureStrategies, type, stageType, variables, tags, delegateSelectors",
              "template"},
    requiredFieldNames = {"type", "template"})
@RecasterAlias("io.harness.plancreator.stages.stage.StageElementConfig")
// @deprecated: Use the AbstractStageNode instead.
@Deprecated
public class StageElementConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull
  @EntityIdentifier
  @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN)
  @VariableExpression
  String identifier;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) @VariableExpression String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<String> skipCondition;

  @ApiModelProperty(dataType = SwaggerConstants.STAGE_WHEN_CLASSPATH)
  @VariableExpression
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<StageWhenCondition> when;

  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;

  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @JsonProperty("skipInstances")
  @YamlSchemaTypes({string})
  ParameterField<Boolean> skipInstances;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @ApiModelProperty(dataType = SwaggerConstants.STRATEGY_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  @JsonProperty("strategy")
  ParameterField<StrategyConfig> strategy;

  @VariableExpression List<NGVariable> variables;
  @VariableExpression Map<String, String> tags;
  @VariableExpression String type;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  @VariableExpression
  StageInfoConfig stageType;
  @VariableExpression(skipVariableExpression = true) TemplateLinkConfig template;

  @Builder
  public StageElementConfig(String uuid, String identifier, String name, ParameterField<String> description,
      ParameterField<List<FailureStrategyConfig>> failureStrategies, List<NGVariable> variables, String type,
      StageInfoConfig stageType, ParameterField<String> skipCondition, ParameterField<StageWhenCondition> when,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.failureStrategies = failureStrategies;
    this.variables = variables;
    this.type = type;
    this.stageType = stageType;
    this.skipCondition = skipCondition;
    this.when = when;
    this.delegateSelectors = delegateSelectors;
  }
}
