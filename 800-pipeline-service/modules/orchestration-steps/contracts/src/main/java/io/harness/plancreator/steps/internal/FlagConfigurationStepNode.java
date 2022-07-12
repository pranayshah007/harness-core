/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.CF;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.StepSpecTypeConstants;
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

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.FLAG_CONFIGURATION)
@TypeAlias("FlagConfigurationStepNode")
@OwnedBy(CF)
@RecasterAlias("io.harness.plancreator.steps.internal.FlagConfigurationStepNode")
public class FlagConfigurationStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.FlagConfiguration;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FlagConfigurationStepInfo flagConfigurationStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.FLAG_CONFIGURATION;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return flagConfigurationStepInfo;
  }

  enum StepType {
    FlagConfiguration(StepSpecTypeConstants.FLAG_CONFIGURATION);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
