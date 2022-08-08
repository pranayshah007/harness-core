/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.entity;

import static io.harness.filter.FilterConstants.PIPELINE_SETUP_FILTER;

import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
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
@ApiModel("PipelineExecutionFilterProperties")
@JsonTypeName(PIPELINE_SETUP_FILTER)
public class PipelineExecutionFilterProperties extends FilterProperties {
  private List<NGTag> pipelineTags;
  private List<ExecutionStatus> status;
  private TimeRange timeRange;
  private String pipelineName;
  private org.bson.Document moduleProperties;
}
