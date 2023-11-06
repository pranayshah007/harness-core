/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.filter.FilterConstants.PIPELINE_EXECUTION_FILTER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.yaml.core.NGLabel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.bson.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineExecutionFilterProperties")
@JsonTypeName(PIPELINE_EXECUTION_FILTER)
public class PipelineExecutionFilterPropertiesDTO extends FilterPropertiesDTO {
  private List<NGTag> pipelineTags;
  private List<NGLabel> pipelineLabels;
  private List<ExecutionStatus> status;
  private String pipelineName;
  private TimeRange timeRange;
  private org.bson.Document moduleProperties;
  private List<TriggerType> triggerTypes;
  private List<String> triggerIdentifiers;
  private ExecutionModeFilter executionModeFilter;
  @Override
  public FilterType getFilterType() {
    return FilterType.PIPELINEEXECUTION;
  }

  @Builder
  public PipelineExecutionFilterPropertiesDTO(Map<String, String> tags, Map<String, String> labels,
      FilterType filterType, List<NGTag> pipelineTags, List<NGLabel> pipelineLabels, List<ExecutionStatus> status,
      String pipelineName, TimeRange timeRange, Document moduleProperties, List<TriggerType> triggerTypes,
      List<String> triggerIdentifiers, ExecutionModeFilter executionModeFilter) {
    super(tags, labels, filterType);
    this.pipelineTags = pipelineTags;
    this.pipelineLabels = pipelineLabels;
    this.status = status;
    this.pipelineName = pipelineName;
    this.timeRange = timeRange;
    this.moduleProperties = moduleProperties;
    this.triggerTypes = triggerTypes;
    this.triggerIdentifiers = triggerIdentifiers;
    this.executionModeFilter = executionModeFilter;
  }
}
