/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.PipelineInfrastructureVisitorHelper;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "PipelineInfrastructureKeys")
@NoArgsConstructor
@AllArgsConstructor
@OneOfField(fields = {"environment", "environmentRef"})
@OneOfField(fields = {"infrastructureDefinition", "useFromStage"})
@SimpleVisitorHelper(helperClass = PipelineInfrastructureVisitorHelper.class)
@TypeAlias("pipelineInfrastructure")
@RecasterAlias("io.harness.cdng.pipeline.PipelineInfrastructure")
public class PipelineInfrastructure implements StepParameters, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  private InfrastructureDef infrastructureDefinition;
  @Wither @VariableExpression(skipVariableExpression = true) private InfraUseFromStage useFromStage;
  private EnvironmentYaml environment;
  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> allowSimultaneousDeployments;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> infrastructureKey;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> environmentRef;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("infrastructureDefinition", infrastructureDefinition);
    children.add("environment", environment);
    children.add("useFromStage", useFromStage);
    return children;
  }
}
