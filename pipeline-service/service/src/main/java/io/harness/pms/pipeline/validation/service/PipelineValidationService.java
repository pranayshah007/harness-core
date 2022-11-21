package io.harness.pms.pipeline.validation.service;

import io.harness.pms.pipeline.PipelineEntity;

public interface PipelineValidationService {
  boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, PipelineEntity pipelineEntity);
}
