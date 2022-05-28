/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.DEPLOYMENT_STAGE)
@TypeAlias("DeploymentStageNode")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.creator.plan.stage.DeploymentStageNode")
public class DeploymentStageNode extends DeploymentAbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Deployment;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  DeploymentStageConfig deploymentStageConfig;
  @Override
  public String getType() {
    return StepSpecTypeConstants.DEPLOYMENT_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return deploymentStageConfig;
  }

  public enum StepType {
    Deployment(StepSpecTypeConstants.DEPLOYMENT_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
