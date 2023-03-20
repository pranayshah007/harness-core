package io.harness.advisers.prb;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Value
@Builder
@TypeAlias("pipelineRollbackStartParameters")
public class PipelineRollbackStartParameters {
  String pipelineRollbackStageId;
}
