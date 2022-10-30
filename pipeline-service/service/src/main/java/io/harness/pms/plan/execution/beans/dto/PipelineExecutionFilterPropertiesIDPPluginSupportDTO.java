/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.swagger.annotations.ApiModel;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static io.harness.filter.FilterConstants.PIPELINE_EXECUTION_FILTER;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineExecutionFilterPropertiesIDPPlugin")
@JsonTypeName(PIPELINE_EXECUTION_FILTER)
public class PipelineExecutionFilterPropertiesIDPPluginSupportDTO extends FilterPropertiesDTO {
  private List<NGTag> pipelineTags;
  private List<ExecutionStatus> status;
  private String pipelineName;
  private TimeRange timeRange;
  private org.bson.Document modulePropertiesCD;
  private org.bson.Document modulePropertiesCI;

  @Override
  public FilterType getFilterType() {
    return FilterType.PIPELINEEXECUTION;
  }
}
