/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.pipeline.PipelineFilterProperties;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PipelineFilterPropertiesMapper
    implements FilterPropertiesMapper<PipelineFilterPropertiesDto, PipelineFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties pipelineFilterProperties) {
    PipelineFilterProperties properties = (PipelineFilterProperties) pipelineFilterProperties;
    PipelineFilterPropertiesDto pipelineFilterPropertiesDto =
        PipelineFilterPropertiesDto.builder()
            .moduleProperties(properties.getModuleProperties())
            .description(properties.getDescription())
            .pipelineIdentifiers(properties.getPipelineIdentifiers())
            .pipelineTags(properties.getPipelineTags())
            .name(properties.getName())
            .tags(TagMapper.convertToMap(pipelineFilterProperties.getTags()))
            .build();

    return pipelineFilterPropertiesDto;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO pipelineFilterPropertiesDto) {
    PipelineFilterPropertiesDto propertiesDto = (PipelineFilterPropertiesDto) pipelineFilterPropertiesDto;
    PipelineFilterProperties pipelineFilterProperties =
        PipelineFilterProperties.builder()
            .moduleProperties(propertiesDto.getModuleProperties())
            .description(propertiesDto.getDescription())
            .name(propertiesDto.getName())
            .pipelineIdentifiers(propertiesDto.getPipelineIdentifiers())
            .pipelineTags(propertiesDto.getPipelineTags())
            .type(pipelineFilterPropertiesDto.getFilterType())
            .tags(TagMapper.convertToList(pipelineFilterPropertiesDto.getTags()))
            .build();

    return pipelineFilterProperties;
  }
}
