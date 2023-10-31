/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
public interface NGGlobalTemplateService {
  List<TemplateWrapperResponseDTO> createUpdateGlobalTemplate(String accountId, String connectorRef,
      Map<String, Object> webhookEvent, String comments, String orgIdentifier, String projectIdentifier);
  Page<GlobalTemplateEntity> getAllGlobalTemplate(boolean notDeleted, boolean getMetadataOnly, Pageable pageable);
  Optional<GlobalTemplateEntity> getGlobalTemplateWithVersionLabel(String templateIdentifier, String versionLabel,
      boolean deleted, boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch);
}
