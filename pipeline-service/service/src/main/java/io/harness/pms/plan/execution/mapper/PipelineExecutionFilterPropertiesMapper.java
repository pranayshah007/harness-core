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

public class PipelineExecutionFilterPropertiesMapper
    implements FilterPropertiesMapper<PipelineExecutionFilterPropertiesDTO, PipelineExecutionFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties pipelineExecutionFilterProperties) {
    PipelineExecutionFilterProperties executionFilterProperties =
        (PipelineExecutionFilterProperties) pipelineExecutionFilterProperties;
    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .moduleProperties(executionFilterProperties.getModuleProperties())
            .pipelineName(executionFilterProperties.getPipelineName())
            .pipelineTags(executionFilterProperties.getPipelineTags())
            .status(executionFilterProperties.getStatus())
            .timeRange(executionFilterProperties.getTimeRange())
            .tags(TagMapper.convertToMap(pipelineExecutionFilterProperties.getTags()))
            .build();
    return pipelineExecutionFilterPropertiesDTO;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO pipelineExecutionFilterPropertiesDTO) {
    PipelineExecutionFilterPropertiesDTO executionFilterPropertiesDTO =
        (PipelineExecutionFilterPropertiesDTO) pipelineExecutionFilterPropertiesDTO;
    PipelineExecutionFilterProperties pipelineExecutionFilterProperties =
        PipelineExecutionFilterProperties.builder()
            .moduleProperties(executionFilterPropertiesDTO.getModuleProperties())
            .pipelineName(executionFilterPropertiesDTO.getPipelineName())
            .pipelineTags(executionFilterPropertiesDTO.getPipelineTags())
            .status(executionFilterPropertiesDTO.getStatus())
            .timeRange(executionFilterPropertiesDTO.getTimeRange())
            .tags(TagMapper.convertToList(pipelineExecutionFilterPropertiesDTO.getTags()))
            .type(pipelineExecutionFilterPropertiesDTO.getFilterType())
            .build();
    return pipelineExecutionFilterProperties;
  }
}
