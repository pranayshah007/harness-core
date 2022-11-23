package io.harness.pms.pipeline.governance.service;

import io.harness.governance.GovernanceMetadata;

public interface PipelineGovernanceService {
  GovernanceMetadata validateGovernanceRules(
      String accountId, String orgIdentifier, String projectIdentifier, String yamlWithResolvedTemplates);

  String fetchExpandedPipelineJSONFromYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml, boolean isExecution);
}
