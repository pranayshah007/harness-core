package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
public class PipelineCRUDResult {
  PipelineEntity pipelineEntity;
  GovernanceMetadata governanceMetadata;
}
