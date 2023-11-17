/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2;

import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterProperties;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;

import com.google.inject.Singleton;
import org.modelmapper.ModelMapper;

@Singleton
public class OverrideFilterPropertiesMapper
    implements FilterPropertiesMapper<OverrideFilterPropertiesDTO, OverrideFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties overrideFilterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    FilterPropertiesDTO filterPropertiesDTO =
        modelMapper.map(overrideFilterProperties, OverrideFilterPropertiesDTO.class);
    filterPropertiesDTO.setTags(TagMapper.convertToMap(overrideFilterProperties.getTags()));
    return filterPropertiesDTO;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO overrideFilterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    OverrideFilterProperties filterProperties =
        modelMapper.map(overrideFilterPropertiesDTO, OverrideFilterProperties.class);
    filterProperties.setType(overrideFilterPropertiesDTO.getFilterType());
    filterProperties.setTags(TagMapper.convertToList(overrideFilterPropertiesDTO.getTags()));
    return filterProperties;
  }
}
