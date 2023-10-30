/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.template.entity.GlobalTemplateEntity;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class NGGlobalTemplateDtoMapper {
  public TemplateSummaryResponseDTO prepareTemplateSummaryResponseDto(GlobalTemplateEntity templateEntity) {
    return TemplateSummaryResponseDTO.builder()
        .accountId(templateEntity.getAccountId())
        .yaml(templateEntity.getYaml())
        .identifier(templateEntity.getIdentifier())
        .description(templateEntity.getDescription())
        .name(templateEntity.getName())
        .childType(templateEntity.getChildType())
        .templateEntityType(templateEntity.getTemplateEntityType())
        .templateScope(templateEntity.getTemplateScope())
        .versionLabel(templateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(templateEntity.getTags()))
        .version(templateEntity.getVersion())
        .icon(templateEntity.getIcon())
        .yamlVersion(templateEntity.getHarnessVersion())
        .entityValidityDetails(templateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(templateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .createdAt(templateEntity.getCreatedAt())
        .build();
  }
}
