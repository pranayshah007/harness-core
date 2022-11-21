package io.harness.pms.pipeline.validation.service;

import io.harness.pms.pipeline.service.PMSYamlSchemaService;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class PipelineValidationServiceImpl implements PipelineValidationService {
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;

  @Override
  public boolean validateYaml(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String yamlWithTemplatesResolved) {
    pmsYamlSchemaService.validateYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved);
    // validate unique fqn in resolveTemplateRefsInPipeline
    pmsYamlSchemaService.validateUniqueFqn(yamlWithTemplatesResolved);
    return true;
  }
}
