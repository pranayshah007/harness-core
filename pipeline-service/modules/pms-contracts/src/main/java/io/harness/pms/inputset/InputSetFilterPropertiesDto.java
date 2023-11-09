/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterConstants.INPUTSET_FILTER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("InputSetFilterProperties")
@JsonTypeName(INPUTSET_FILTER)
@OwnedBy(PIPELINE)
@Schema(name = "InputSetFilterProperties", description = "Properties of the InputSet Filter defined in Harness")
public class InputSetFilterPropertiesDto extends FilterPropertiesDTO {
  @Schema(
      description =
          "This is the list of the InputSet Identifiers appended with Pipeline Identifiers on which the filter will be applied.")
  private List<String> inputSetIdsWithPipelineIds;

  @Builder
  public InputSetFilterPropertiesDto(List<String> inputSetIdsWithPipelineIds, Map<String, String> tags,
      Map<String, String> labels, FilterType filterType) {
    super(tags, labels, filterType);
    this.inputSetIdsWithPipelineIds = inputSetIdsWithPipelineIds;
  }

  @Override
  public FilterType getFilterType() {
    return FilterType.INPUTSET;
  }
}
