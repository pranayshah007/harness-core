/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.ASG_ROLLING_ROLLBACK)
@TypeAlias("asgRollingRollbackStepNode")
@RecasterAlias("io.harness.cdng.aws.asg.AsgRollingRollbackStepNode")
public class AsgRollingRollbackStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull AsgRollingRollbackStepNode.StepType type = StepType.AsgRollingRollback;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  AsgRollingRollbackStepInfo asgRollingRollbackStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.ASG_ROLLING_ROLLBACK;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return asgRollingRollbackStepInfo;
  }

  enum StepType {
    AsgRollingRollback(StepSpecTypeConstants.ASG_ROLLING_ROLLBACK);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
