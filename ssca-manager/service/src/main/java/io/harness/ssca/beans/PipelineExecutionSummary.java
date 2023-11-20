/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineExecutionSummaryKeys")
public class PipelineExecutionSummary {
  @NotNull private String pipelineName;
  @NotNull private String pipelineExecutionId;
  @NotNull private String pipelineExecutionName;
  @NotNull private String sequenceId;

  @NotNull private String deployedById;
  @NotNull private String deployedByName;
  @NotNull private Long deployedAt;
  @NotNull private String triggerType;
}
