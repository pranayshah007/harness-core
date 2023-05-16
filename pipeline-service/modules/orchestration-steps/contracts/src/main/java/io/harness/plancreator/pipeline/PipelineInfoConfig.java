/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.pipeline;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.notification.bean.NotificationRules;
import io.harness.plancreator.flowcontrol.FlowControlConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.properties.NGProperties;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("pipelineInfoConfig")
public class PipelineInfoConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) @VariableExpression String name;
  @NotNull
  @EntityIdentifier
  @Pattern(regexp = NGRegexValidatorConstants.PIPELINE_IDENTIFIER_PATTERN)
  @VariableExpression
  String identifier;

  @VariableExpression(skipVariableExpression = true) FlowControlConfig flowControl;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;
  @VariableExpression Map<String, String> tags;

  @VariableExpression List<NGVariable> variables;
  @VariableExpression NGProperties properties;

  @Singular @Size(min = 1) @VariableExpression(skipVariableExpression = true) List<StageElementWrapperConfig> stages;
  @VariableExpression(skipVariableExpression = true) List<NotificationRules> notificationRules;

  @VariableExpression(skipVariableExpression = true) String orgIdentifier;
  @VariableExpression(skipVariableExpression = true) String projectIdentifier;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipVariableExpression = true)
  ParameterField<Timeout> timeout;

  @VariableExpression(skipVariableExpression = true) TemplateLinkConfig template;

  @VariableExpression(skipVariableExpression = true) boolean allowStageExecutions;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
}
