/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes.iacm;

import static io.harness.annotations.dev.HarnessTeam.IACM;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.IACMCostEstimationInfo;
import io.harness.beans.steps.stepinfo.IACMStepInfoType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("IACMCostEstimation")
@TypeAlias("IACMCostEstimationStepNode")
@OwnedBy(IACM)
@RecasterAlias("io.harness.beans.steps.nodes.iacm.IACMCostEstimationStepNode")

public class IACMCostEstimationStepNode extends CIAbstractStepNode {
  @JsonProperty("type")
  @NotNull
  IACMCostEstimationStepNode.StepType type = IACMCostEstimationStepNode.StepType.IACMCostEstimation;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  IACMCostEstimationInfo iacmCostEstimationInfo;

  @Override
  public String getType() {
    return IACMStepInfoType.IACM_COST_ESTIMATION.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return iacmCostEstimationInfo;
  }

  public enum StepType {
    IACMCostEstimation(IACMStepInfoType.IACM_COST_ESTIMATION.getDisplayName());
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }

  @Builder
  public IACMCostEstimationStepNode(String uuid, String identifier, String name,
      ParameterField<List<FailureStrategyConfig>> failureStrategies, IACMCostEstimationInfo iacmCostEstimationInfo,
      IACMCostEstimationStepNode.StepType type, ParameterField<Timeout> timeout) {
    this.setFailureStrategies(failureStrategies);
    this.iacmCostEstimationInfo = iacmCostEstimationInfo;
    this.type = type;
    this.setFailureStrategies(failureStrategies);
    this.setTimeout(timeout);
    this.setUuid(uuid);
    this.setIdentifier(identifier);
    this.setName(name);
    this.setDescription(getDescription());
  }
}
