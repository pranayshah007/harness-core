/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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

  void validateYamlWithUnresolvedTemplates(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineYaml, String harnessVersion);
}
