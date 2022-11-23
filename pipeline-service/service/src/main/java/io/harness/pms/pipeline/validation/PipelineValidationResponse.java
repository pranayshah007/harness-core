package io.harness.pms.pipeline.validation;

import io.harness.governance.GovernanceMetadata;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PipelineValidationResponse {
  GovernanceMetadata governanceMetadata;
}
