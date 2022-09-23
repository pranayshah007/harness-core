/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.ECS_ROLLING_DEPLOY)
@TypeAlias("ecsRollingDeployStepNode")
@RecasterAlias("io.harness.cdng.ecs.EcsRollingDeployStepNode")
public class EcsRollingDeployStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull io.harness.cdng.ecs.EcsRollingDeployStepNode.StepType type = StepType.EcsRollingDeploy;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  EcsRollingDeployStepInfo ecsRollingDeployStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.ECS_ROLLING_DEPLOY;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return ecsRollingDeployStepInfo;
  }

  enum StepType {
    EcsRollingDeploy(StepSpecTypeConstants.ECS_ROLLING_DEPLOY);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
