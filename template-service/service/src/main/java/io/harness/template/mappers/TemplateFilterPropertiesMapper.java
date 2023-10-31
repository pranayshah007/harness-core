/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
public class TemplateFilterPropertiesMapper
    implements FilterPropertiesMapper<TemplateFilterPropertiesDTO, TemplateFilterProperties> {
  @Override
  public FilterProperties toEntity(FilterPropertiesDTO filterPropertiesDTO) {
    TemplateFilterPropertiesDTO propertiesDTO = (TemplateFilterPropertiesDTO) filterPropertiesDTO;
    TemplateFilterProperties templateFilterProperties =
        TemplateFilterProperties.builder()
            .templateNames(propertiesDTO.getTemplateNames())
            .templateIdentifiers(propertiesDTO.getTemplateIdentifiers())
            .childTypes(propertiesDTO.getChildTypes())
            .templateEntityTypes(propertiesDTO.getTemplateEntityTypes())
            .description(propertiesDTO.getDescription())
            .repoName(propertiesDTO.getRepoName())
            .tags(TagMapper.convertToList(filterPropertiesDTO.getTags()))
            .type(filterPropertiesDTO.getFilterType())
            .build();

    return templateFilterProperties;
  }

  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties filterProperties) {
    TemplateFilterProperties properties = (TemplateFilterProperties) filterProperties;
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateNames(properties.getTemplateNames())
            .templateIdentifiers(properties.getTemplateIdentifiers())
            .childTypes(properties.getChildTypes())
            .description(properties.getDescription())
            .templateEntityTypes(properties.getTemplateEntityTypes())
            .repoName(properties.getRepoName())
            .tags(TagMapper.convertToMap(filterProperties.getTags()))
            .filterType(filterProperties.getType())
            .build();

    return templateFilterPropertiesDTO;
  }
}
