/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.pms.variables.VariableMergeServiceResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ExecutionInputVariablesResponse")
@Schema(name = "ExecutionInputVariablesResponse", description = "Contains the yaml that was used to execute")
public class ExecutionInputVariablesResponseDTO {
  VariableMergeServiceResponse variableMergeServiceResponse;
  String pipelineYaml;
}
