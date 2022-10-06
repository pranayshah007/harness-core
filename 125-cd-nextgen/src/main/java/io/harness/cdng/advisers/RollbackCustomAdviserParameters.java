package io.harness.cdng.advisers;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@TypeAlias("rollbackCustomAdviserParameters")
public class RollbackCustomAdviserParameters {
  Boolean canAdviseOnPipelineRollback;
}
