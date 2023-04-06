/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions.deploygenone;

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
@JsonTypeName(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY)
@TypeAlias("googleFunctionsGenOneDeployStepNode")
@RecasterAlias("io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStepNode")
public class GoogleFunctionsGenOneDeployStepNode extends CdAbstractStepNode {
  @JsonProperty("type")
  @NotNull
  GoogleFunctionsGenOneDeployStepNode.StepType type =
      GoogleFunctionsGenOneDeployStepNode.StepType.DeployCloudFunctionGenOne;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  GoogleFunctionsGenOneDeployStepInfo googleFunctionsGenOneDeployStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return googleFunctionsGenOneDeployStepInfo;
  }

  enum StepType {
    DeployCloudFunctionGenOne(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
