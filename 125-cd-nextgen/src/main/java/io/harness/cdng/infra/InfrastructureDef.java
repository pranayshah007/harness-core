/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.InfrastructureDefVisitorHelper;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@SimpleVisitorHelper(helperClass = InfrastructureDefVisitorHelper.class)
@TypeAlias("infrastructureDef")
@RecasterAlias("io.harness.cdng.infra.InfrastructureDef")
public class InfrastructureDef implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("type") InfrastructureType type;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  @NotNull
  Infrastructure spec;

  @JsonProperty("provisioner")
  @Nullable
  @VariableExpression(skipVariableExpression = true)
  ExecutionElementConfig provisioner;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  // Use Builder as Constructor then only external property(visible) will be filled.
  @Builder
  public InfrastructureDef(InfrastructureType type, Infrastructure spec, ExecutionElementConfig provisioner) {
    this.type = type;
    this.spec = spec;
    this.provisioner = provisioner;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("spec", spec);
    children.add("provisioner", provisioner);
    return children;
  }
}
