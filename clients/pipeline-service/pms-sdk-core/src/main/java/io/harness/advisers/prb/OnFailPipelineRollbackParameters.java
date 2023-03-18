package io.harness.advisers.prb;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureType;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Value
@Builder
@TypeAlias("onFailPipelineRollbackParameters")
public class OnFailPipelineRollbackParameters {
  String pipelineRollbackStageId;
  Set<FailureType> applicableFailureTypes;
}
