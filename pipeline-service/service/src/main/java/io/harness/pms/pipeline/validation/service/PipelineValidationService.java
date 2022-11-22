package io.harness.pms.pipeline.validation.service;

public interface PipelineValidationService {
  boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, String pipelineYaml, String harnessVersion);
}
