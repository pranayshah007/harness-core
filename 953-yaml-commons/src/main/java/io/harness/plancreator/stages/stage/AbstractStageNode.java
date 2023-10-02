/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.stage;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.when.beans.StageWhenCondition;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
public abstract class AbstractStageNode {
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

  @VariableExpression List<NGVariable> variables;
  Map<String, String> tags;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @ApiModelProperty(dataType = SwaggerConstants.STRATEGY_CLASSPATH)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  @VariableExpression(skipVariableExpression = true)
  @JsonProperty("strategy")
  ParameterField<StrategyConfig> strategy;

  @JsonIgnore public abstract String getType();
  @JsonIgnore public abstract StageInfoConfig getStageInfoConfig();
}
