/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.chaos;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.CHAOS_STEP)
@TypeAlias("chaosStepInfo")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.cdng.chaos.ChaosStepInfo")
public class ChaosStepInfo implements CDStepInfo {
  @JsonProperty("experimentRef") @NotNull String experimentRef;
  @JsonProperty("expectedResilienceScore") @NotNull Double expectedResilienceScore;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> assertion;

  @Builder
  @ConstructorProperties({"experimentRef", "expectedResilienceScore", "assertion"})
  public ChaosStepInfo(String experimentRef, Double expectedResilienceScore, ParameterField<String> assertion) {
    this.experimentRef = experimentRef;
    this.expectedResilienceScore = expectedResilienceScore;
    this.assertion = assertion;
  }

  @Override
  public StepType getStepType() {
    return ChaosStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return null;
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {}

  @Override
  public SpecParameters getSpecParameters() {
    return ChaosStepParameters.builder()
        .experimentRef(experimentRef)
        .expectedResilienceScore(expectedResilienceScore)
        .assertion(assertion)
        .build();
  }
}
