package io.harness.steps.common.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.pipeline.PipelineInfoConfigV1;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@TypeAlias("pipelineStepParameterV1")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.common.pipeline.PipelineStepParameterV1")
public class PipelineStepParameterV1 implements StepParameters {
  String name;
  String id;
  String childNodeId;

  @Builder(builderMethodName = "newBuilder")
  public PipelineStepParameterV1(String name, String id, String childNodeId) {
    this.name = name;
    this.id = id;
    this.childNodeId = childNodeId;
  }

  public static PipelineStepParameterV1 from(PipelineInfoConfigV1 configV1, String childNodeId) {
    return PipelineStepParameterV1.newBuilder()
        .id(configV1.getId())
        .name(configV1.getName())
        .childNodeId(childNodeId)
        .build();
  }
}
