package io.harness.pms.pipeline.validation.service;

import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;

public interface PipelineValidationService {
  PipelineValidationResponse validateYamlAndGovernanceRules(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity);

  PipelineValidationResponse validateYamlAndGetGovernanceMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity);
  boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, String pipelineYaml, String harnessVersion);
}
