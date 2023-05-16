/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.pipelinerollback;

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
@JsonTypeName(StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE)
@TypeAlias("PipelineRollbackStageNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.plancreator.prb.PipelineRollbackStageNode")
public class PipelineRollbackStageNode extends PmsAbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.PipelineRollback;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  PipelineRollbackStageConfig pipelineRollbackStageConfig;

  @Override
  public String getType() {
    return StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return pipelineRollbackStageConfig;
  }

  public enum StepType {
    PipelineRollback(StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
