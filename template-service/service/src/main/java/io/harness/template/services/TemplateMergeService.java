/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateRetainVariablesResponse;

import com.fasterxml.jackson.databind.JsonNode;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
public interface TemplateMergeService {
  String getTemplateInputs(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      String versionLabel, boolean loadFromCache);

  TemplateMergeResponseDTO applyTemplatesToYaml(String accountId, String orgId, String projectId, String yaml,
      boolean getMergedYamlWithTemplateField, boolean loadFromCache, boolean appendInputSetValidator);

  TemplateMergeResponseDTO applyTemplatesToYamlV2(String accountId, String orgId, String projectId,
      JsonNode entityJsonNode, boolean getMergedYamlWithTemplateField, boolean loadFromCache,
      boolean appendInputSetValidator);

  TemplateRetainVariablesResponse mergeTemplateInputs(String newTemplateInputs, String originalTemplateInputs);
}
