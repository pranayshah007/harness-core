/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment;

import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.environment.beans.EnvironmentFilterProperties;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Singleton;
import org.modelmapper.ModelMapper;

@Singleton
public class EnvironmentFilterPropertiesMapper
    implements FilterPropertiesMapper<EnvironmentFilterPropertiesDTO, EnvironmentFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties environmentFilterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    FilterPropertiesDTO filterPropertiesDTO =
        modelMapper.map(environmentFilterProperties, EnvironmentFilterPropertiesDTO.class);
    filterPropertiesDTO.setTags(TagMapper.convertToMap(environmentFilterProperties.getTags()));
    return filterPropertiesDTO;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO environmentFilterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    EnvironmentFilterProperties filterProperties =
        modelMapper.map(environmentFilterPropertiesDTO, EnvironmentFilterProperties.class);
    filterProperties.setType(environmentFilterPropertiesDTO.getFilterType());
    filterProperties.setTags(TagMapper.convertToList(environmentFilterPropertiesDTO.getTags()));
    return filterProperties;
  }
}
