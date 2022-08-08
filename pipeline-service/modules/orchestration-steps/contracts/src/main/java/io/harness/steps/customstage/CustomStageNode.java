/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.PmsAbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.CUSTOM_STAGE)
@TypeAlias("CustomStageNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.customstage.CustomStageNode")
public class CustomStageNode extends PmsAbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Custom;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CustomStageConfig customStageConfig;
  @Override
  public String getType() {
    return StepSpecTypeConstants.CUSTOM_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return customStageConfig;
  }

  public enum StepType {
    Custom(StepSpecTypeConstants.CUSTOM_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
