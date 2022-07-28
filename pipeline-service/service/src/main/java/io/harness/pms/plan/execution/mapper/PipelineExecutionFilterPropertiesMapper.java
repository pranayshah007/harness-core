/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.entity.PipelineExecutionFilterProperties;

import org.modelmapper.ModelMapper;

public class PipelineExecutionFilterPropertiesMapper
    implements FilterPropertiesMapper<PipelineExecutionFilterPropertiesDTO, PipelineExecutionFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties pipelineExecutionFilterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    FilterPropertiesDTO filterPropertiesDTO =
        modelMapper.map(pipelineExecutionFilterProperties, PipelineExecutionFilterPropertiesDTO.class);
    filterPropertiesDTO.setTags(TagMapper.convertToMap(pipelineExecutionFilterProperties.getTags()));
    return filterPropertiesDTO;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO pipelineExecutionFilterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    PipelineExecutionFilterProperties filterProperties =
        modelMapper.map(pipelineExecutionFilterPropertiesDTO, PipelineExecutionFilterProperties.class);
    filterProperties.setType(pipelineExecutionFilterPropertiesDTO.getFilterType());
    filterProperties.setTags(TagMapper.convertToList(pipelineExecutionFilterPropertiesDTO.getTags()));
    return filterProperties;
  }
}
