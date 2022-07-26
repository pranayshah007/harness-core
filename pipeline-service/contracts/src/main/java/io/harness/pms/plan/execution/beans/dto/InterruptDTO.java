/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.pms.plan.execution.PlanExecutionInterruptType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("pipelineExecutionInterrupt")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "InterruptResponse",
    description = "Contains the ID and type of the interrupt issued along with the execution id.")
public class InterruptDTO {
  String id;
  @NonNull PlanExecutionInterruptType type;
  @NonNull String planExecutionId;
}
