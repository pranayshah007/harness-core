/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.beans.CVNGAbstractStepNode;
import io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.ANALYZE_DEPLOYMENT_IMPACT)
@OwnedBy(HarnessTeam.CV)
public class CVAnalyzeDeploymentStepNode extends CVNGAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.AnalyzeDeploymentImpact;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CVNGDeploymentStepInfo deploymentStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.ANALYZE_DEPLOYMENT_IMPACT;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return deploymentStepInfo;
  }

  enum StepType {
    AnalyzeDeploymentImpact(StepSpecTypeConstants.ANALYZE_DEPLOYMENT_IMPACT);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
