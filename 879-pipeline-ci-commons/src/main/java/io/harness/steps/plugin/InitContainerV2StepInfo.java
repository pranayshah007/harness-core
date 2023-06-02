/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.NotExpression;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.container.ContainerStepSpecTypeConstants;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("InitContainerV2StepInfo")
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.steps.plugin.InitContainerV2StepInfo")
public class InitContainerV2StepInfo implements Visitable, SpecParameters, ContainerStepSpec, StepSpecType {
  private String stepGroupIdentifier;
  private String stepGroupName;

  @NotNull @Valid private ContainerStepInfra infrastructure;
  @NotExpression private StepsExecutionConfig stepsExecutionConfig;
  @NotExpression Map<StepInfo, PluginCreationResponseList> pluginsData;
  Map<String, StrategyExpansionData> strategyExpansionMap;
  ParameterField<List<String>> sharedPaths;
  @Override
  @JsonIgnore
  public StepType getStepType() {
    return ContainerStepSpecTypeConstants.CONTAINER_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  public SpecParameters getSpecParameters() {
    return this;
  }

  @Override
  public void setName(String name) {
    this.stepGroupName = name;
  }

  @Override
  public void setIdentifier(String identifier) {
    this.stepGroupIdentifier = identifier;
  }

  @Override
  public String getIdentifier() {
    return stepGroupIdentifier;
  }

  @Override
  public String getName() {
    return stepGroupName;
  }

  @Override
  public ContainerStepType getType() {
    return ContainerStepType.INIT_CONTAINER_V2;
  }
}
