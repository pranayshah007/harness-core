/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.node;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.idp.pipeline.IDPStageSpecTypeConstants;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(IDPStageSpecTypeConstants.IDP_STAGE)
@TypeAlias("IDPStageNode")
@OwnedBy(IDP)
@RecasterAlias("io.harness.idp.pipeline.stages.node.IDPStageNode")
public class IDPStageNode extends AbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.IDPStage;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  IDPStageConfigImpl idpStageConfig;
  @Override
  public String getType() {
    return IDPStageSpecTypeConstants.IDP_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return idpStageConfig;
  }

  public enum StepType {
    IDPStage(IDPStageSpecTypeConstants.IDP_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }

  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;
}
