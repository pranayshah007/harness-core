/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.pms.inputset.InputSetFilterProperties;
import io.harness.pms.inputset.InputSetFilterPropertiesDto;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class InputSetFilterPropertiesMapper
    implements FilterPropertiesMapper<InputSetFilterPropertiesDto, InputSetFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties inputSetFilterProperties) {
    InputSetFilterProperties properties = (InputSetFilterProperties) inputSetFilterProperties;
    InputSetFilterPropertiesDto inputSetFilterPropertiesDto =
        InputSetFilterPropertiesDto.builder()
            .inputSetIdsWithPipelineIds(properties.getInputSetIdsWithPipelineIds())
            .build();

    return inputSetFilterPropertiesDto;
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO inputSetFilterPropertiesDto) {
    InputSetFilterPropertiesDto propertiesDto = (InputSetFilterPropertiesDto) inputSetFilterPropertiesDto;
    InputSetFilterProperties inputSetFilterProperties =
        InputSetFilterProperties.builder()
            .inputSetIdsWithPipelineIds(propertiesDto.getInputSetIdsWithPipelineIds())
            .type(inputSetFilterPropertiesDto.getFilterType())
            .build();

    return inputSetFilterProperties;
  }
}
