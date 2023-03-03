/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.stages;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.CI_STAGE)
@TypeAlias("IntegrationStageNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.stages.IntegrationStageNode")
public class IntegrationStageNode extends AbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.CI;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  IntegrationStageConfigImpl integrationStageConfig;
  @VariableExpression List<NGVariable> pipelineVariables;

  @Override
  public String getType() {
    return StepSpecTypeConstants.CI_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return integrationStageConfig;
  }

  public enum StepType {
    CI(StepSpecTypeConstants.CI_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;

  @Builder
  public IntegrationStageNode(String uuid, String identifier, String name,
      ParameterField<List<FailureStrategyConfig>> failureStrategies, IntegrationStageConfigImpl integrationStageConfig,
      StepType type, List<NGVariable> variables, List<NGVariable> pipelineVariables) {
    this.failureStrategies = failureStrategies;
    this.integrationStageConfig = integrationStageConfig;
    this.type = type;
    this.setVariables(variables);
    this.pipelineVariables = pipelineVariables;
    this.setUuid(uuid);
    this.setIdentifier(identifier);
    this.setName(name);
    this.setDescription(getDescription());
  }
}
