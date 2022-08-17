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
