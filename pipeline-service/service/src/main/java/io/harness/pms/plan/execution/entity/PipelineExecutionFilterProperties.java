/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.entity;

import static io.harness.filter.FilterConstants.PIPELINE_SETUP_FILTER;

import io.harness.filter.FilterType;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.bson.Document;

@Value
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineExecutionFilterProperties")
@JsonTypeName(PIPELINE_SETUP_FILTER)
public class PipelineExecutionFilterProperties extends FilterProperties {
  private List<NGTag> pipelineTags;
  private List<ExecutionStatus> status;
  private TimeRange timeRange;
  private String pipelineName;
  private org.bson.Document moduleProperties;
  private List<TriggerType> triggerTypes;
  private List<String> triggerIdentifiers;

  @Builder
  public PipelineExecutionFilterProperties(List<NGTag> tags, FilterType type, List<NGTag> pipelineTags,
      List<ExecutionStatus> status, TimeRange timeRange, String pipelineName, Document moduleProperties,
      List<TriggerType> triggerTypes, List<String> triggerIdentifiers) {
    super(tags, type);
    this.pipelineTags = pipelineTags;
    this.status = status;
    this.timeRange = timeRange;
    this.pipelineName = pipelineName;
    this.moduleProperties = moduleProperties;
    this.triggerTypes = triggerTypes;
    this.triggerIdentifiers = triggerIdentifiers;
  }
}
