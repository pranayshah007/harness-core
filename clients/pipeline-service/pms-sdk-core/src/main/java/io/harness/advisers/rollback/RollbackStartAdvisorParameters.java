package io.harness.advisers.rollback;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("rollbackStartAdvisorParameters")
public class RollbackStartAdvisorParameters {
  Boolean canAdviseOnPipelineRollback;
}
