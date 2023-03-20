package io.harness.advisers.prb;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("onFailPipelineRollbackOutput")
@JsonTypeName("onFailPipelineRollbackOutput")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.advisers.prb.OnFailPipelineRollbackOutput")
public class OnFailPipelineRollbackOutput implements ExecutionSweepingOutput {
  boolean shouldStartPipelineRollback;
}
