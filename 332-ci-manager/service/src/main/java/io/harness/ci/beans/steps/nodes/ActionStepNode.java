/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.ActionStepInfo;
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
@JsonTypeName("Action")
@TypeAlias("ActionStepNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.ActionStepNode")
public class ActionStepNode extends CIAbstractStepNode {
  @JsonProperty("type") @NotNull ActionStepNode.StepType type = StepType.Action;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ActionStepInfo actionStepInfo;

  @Override
  public String getType() {
    return CIStepInfoType.ACTION.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return actionStepInfo;
  }

  enum StepType {
    Action(CIStepInfoType.ACTION.getDisplayName());
    @Getter final String name;
    StepType(String name) {
      this.name = name;
    }
  }

  @Builder
  public ActionStepNode(String uuid, String identifier, String name,
      ParameterField<List<FailureStrategyConfig>> failureStrategies, ActionStepInfo actionStepInfo,
      ActionStepNode.StepType type, ParameterField<Timeout> timeout) {
    this.setFailureStrategies(failureStrategies);
    this.actionStepInfo = actionStepInfo;
    this.type = type;
    this.setFailureStrategies(failureStrategies);
    this.setTimeout(timeout);
    this.setUuid(uuid);
    this.setIdentifier(identifier);
    this.setName(name);
    this.setDescription(getDescription());
  }
}
