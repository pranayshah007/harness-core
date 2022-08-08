/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.InfraUseFromOverridesVisitorHelper;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.InfraUseFromStageVisitorHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@SimpleVisitorHelper(helperClass = InfraUseFromStageVisitorHelper.class)
@TypeAlias("infraUseFromStage")
@RecasterAlias("io.harness.cdng.infra.beans.InfraUseFromStage")
public class InfraUseFromStage implements Serializable, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // Stage identifier of the stage to select from.
  @NotNull String stage;
  Overrides overrides;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @VariableExpression(skipVariableExpression = true)
  String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("overrides", overrides);
    return children;
  }

  @Data
  @Builder
  @ApiModel(value = "InfraOverrides")
  @SimpleVisitorHelper(helperClass = InfraUseFromOverridesVisitorHelper.class)
  @TypeAlias("infraUseFromStage_overrides")
  @RecasterAlias("io.harness.cdng.infra.beans.InfraUseFromStage$Overrides")
  public static class Overrides implements Serializable, Visitable {
    EnvironmentYaml environment;
    InfrastructureDef infrastructureDefinition;

    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

    @Override
    public VisitableChildren getChildrenToWalk() {
      VisitableChildren children = VisitableChildren.builder().build();
      children.add("infrastructureDefinition", infrastructureDefinition);
      children.add("environment", environment);
      return children;
    }
  }
}
