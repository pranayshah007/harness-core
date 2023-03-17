/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.stepinfo;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YamlNode;
import io.harness.ssca.beans.SscaConstants;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(SscaConstants.CD_SSCA_ORCHESTRATION)
@TypeAlias(SscaConstants.CD_SSCA_ORCHESTRATION_STEP_NODE)
@OwnedBy(HarnessTeam.SSCA)
@RecasterAlias("io.harness.ssca.cd.beans.stepinfo.CdSscaOrchestrationStepInfo")
public class CdSscaOrchestrationStepInfo extends CdSscaOrchestrationBaseStepInfo implements PMSStepInfo {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Override
  public StepType getStepType() {
    return SscaConstants.CD_SSCA_ORCHESTRATION_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return null;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CdSscaOrchestrationSpecParameters.builder()
        .tool(this.tool)
        .source(this.source)
        .attestation(this.attestation)
        .infrastructure(this.infrastructure)
        .build();
  }
}
