/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.enforcement;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.ssca.beans.SscaConstants;
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
@JsonTypeName(SscaConstants.CD_SSCA_ENFORCEMENT)
@TypeAlias(SscaConstants.CD_SSCA_ENFORCEMENT_STEP_NODE)
@OwnedBy(HarnessTeam.SSCA)
@RecasterAlias("io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementStepNode")
public class CdSscaEnforcementStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") CdSscaEnforcementStepNode.StepType stepType = StepType.CdSscaEnforcement;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CdSscaEnforcementStepInfo stepInfo;

  @Override
  public String getType() {
    return StepType.CdSscaEnforcement.name;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return stepInfo;
  }

  enum StepType {
    CdSscaEnforcement(SscaConstants.CD_SSCA_ENFORCEMENT);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
