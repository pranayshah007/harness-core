package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;

import java.util.Set;

@OwnedBy(PIPELINE)
@HarnessRepo
public interface PlanExecutionMetadataRepositoryCustom {
  PlanExecutionMetadata findByPlanExecutionIdUsingProjections(String planExecutionId, Set<String> fieldsToInclude);
}
