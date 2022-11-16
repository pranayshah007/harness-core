/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
