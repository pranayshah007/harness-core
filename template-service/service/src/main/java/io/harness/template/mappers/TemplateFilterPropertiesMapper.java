/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

import org.modelmapper.ModelMapper;

@OwnedBy(HarnessTeam.CDC)
public class TemplateFilterPropertiesMapper
    implements FilterPropertiesMapper<TemplateFilterPropertiesDTO, TemplateFilterProperties> {
  @Override
  public FilterProperties toEntity(FilterPropertiesDTO filterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    TemplateFilterProperties templateFilterProperties =
        modelMapper.map(filterPropertiesDTO, TemplateFilterProperties.class);
    templateFilterProperties.setType(filterPropertiesDTO.getFilterType());
    templateFilterProperties.setTags(TagMapper.convertToList(filterPropertiesDTO.getTags()));
    return templateFilterProperties;
  }

  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties filterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        modelMapper.map(filterProperties, TemplateFilterPropertiesDTO.class);
    templateFilterPropertiesDTO.setFilterType(filterProperties.getType());
    templateFilterPropertiesDTO.setTags(TagMapper.convertToMap(filterProperties.getTags()));
    return templateFilterPropertiesDTO;
  }
}
