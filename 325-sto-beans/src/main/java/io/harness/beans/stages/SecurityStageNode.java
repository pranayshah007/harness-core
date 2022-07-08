/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.stages;

import static io.harness.annotations.dev.HarnessTeam.STO;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.plancreator.stages.stage.StageInfoConfig;

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
@JsonTypeName(StepSpecTypeConstants.SECURITY_STAGE)
@TypeAlias("SecurityStageNode")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.stages.SecurityStageNode")
public class SecurityStageNode extends IntegrationAbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.SecurityTests;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  SecurityStageConfigImpl securityStageConfig;
  @Override
  public String getType() {
    return StepSpecTypeConstants.SECURITY_STAGE;
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return securityStageConfig;
  }

  public enum StepType {
    SecurityTests(StepSpecTypeConstants.SECURITY_STAGE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
