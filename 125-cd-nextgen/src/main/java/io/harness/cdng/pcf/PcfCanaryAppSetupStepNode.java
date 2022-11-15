/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pcf;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
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
@JsonTypeName(StepSpecTypeConstants.PCF_CANARY_APP_SETUP)
@TypeAlias("pcfCanaryAppSetupStepNode")
@RecasterAlias("io.harness.cdng.pcf.PcfCanaryAppSetupStepNode")
public class PcfCanaryAppSetupStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull PcfCanaryAppSetupStepNode.StepType type = StepType.PcfCanaryAppSetup;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  PcfCanaryAppSetupStepInfo pcfCanaryAppSetupStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.PCF_CANARY_APP_SETUP;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return pcfCanaryAppSetupStepInfo;
  }

  enum StepType {
    PcfCanaryAppSetup(StepSpecTypeConstants.PCF_CANARY_APP_SETUP);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
