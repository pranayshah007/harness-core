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
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@SimpleVisitorHelper(helperClass = ContainerStepInfoVisitorHelper.class)
@TypeAlias("InitContainerV2StepInfo")
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.steps.plugin.InitContainerV2StepInfo")
public class InitContainerV2StepInfo implements PMSStepInfo, Visitable, SpecParameters, ContainerStepSpec {
  private String stepGroupIdentifier;
  private String stepGroupName;

  @NotNull @Valid private ContainerStepInfra infrastructure;
  @NotExpression private StepsExecutionConfig stepsExecutionConfig;
  @NotExpression Map<StepInfo, PluginCreationResponse> pluginsData;
  Map<String, StrategyExpansionData> strategyExpansionMap;

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.CONTAINER_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
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