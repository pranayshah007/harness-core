/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterConstants.PIPELINE_SETUP_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineFilterProperties")
@JsonTypeName(PIPELINE_SETUP_FILTER)
@OwnedBy(PIPELINE)
@Schema(name = "PipelineFilterProperties", description = "Properties of the Pipelines Filter defined in Harness")
public class PipelineFilterPropertiesDto extends FilterPropertiesDTO {
  @Schema(description = "This is the list of the Pipeline Tags on which the filter will be applied.")
  private List<NGTag> pipelineTags;
  @Schema(description = "This is the list of the Pipeline Identifiers on which the filter will be applied.")
  private List<String> pipelineIdentifiers;
  @Schema(description = "This is the Pipeline Name on which the filter will be applied.") private String name;
  @Schema(description = "This is the Pipeline Description on which the filter will be applied.")
  private String description;
  @Schema(description = "These are the Module Properties on which the filter will be applied.")
  private org.bson.Document moduleProperties;

  @Override
  public FilterType getFilterType() {
    return FilterType.PIPELINESETUP;
  }
}
