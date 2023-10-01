/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterProperties;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Singleton;
import org.modelmapper.ModelMapper;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
public class EnvironmentGroupFilterPropertiesMapper
    implements FilterPropertiesMapper<EnvironmentGroupFilterPropertiesDTO, EnvironmentGroupFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties environmentGroupFilterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    FilterPropertiesDTO filterPropertiesDTO =
        modelMapper.map(environmentGroupFilterProperties, EnvironmentGroupFilterPropertiesDTO.class);
    filterPropertiesDTO.setTags(TagMapper.convertToMap(environmentGroupFilterProperties.getTags()));
    return filterPropertiesDTO;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO environmentGroupFilterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    EnvironmentGroupFilterProperties filterProperties =
        modelMapper.map(environmentGroupFilterPropertiesDTO, EnvironmentGroupFilterProperties.class);
    filterProperties.setType(environmentGroupFilterPropertiesDTO.getFilterType());
    filterProperties.setTags(TagMapper.convertToList(environmentGroupFilterPropertiesDTO.getTags()));
    return filterProperties;
  }
}
