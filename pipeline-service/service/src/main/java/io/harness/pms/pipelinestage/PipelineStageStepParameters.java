/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@FieldNameConstants(innerTypeName = "PipelineStageStepParametersKeys")
@Data
@Builder
@TypeAlias("pipelineStageStepParameters")
@RecasterAlias("io.harness.pms.pipelinestage.PipelineStageStepParameters")
public class PipelineStageStepParameters implements StepParameters {
  @NotNull String pipeline;
  @NotNull String project;
  @NotNull String org;

  String stageNodeId;
  JsonNode pipelineInputsJsonNode;

  @SkipAutoEvaluation private ParameterField<Map<String, ParameterField<String>>> outputs;
  private List<String> inputSetReferences;
}
