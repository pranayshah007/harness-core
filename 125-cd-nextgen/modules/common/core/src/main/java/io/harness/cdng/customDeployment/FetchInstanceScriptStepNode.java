/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)
@TypeAlias("fetchInstanceScriptStepNode")
@RecasterAlias("io.harness.cdng.customDeployment.FetchInstanceScriptStepNode")
public class FetchInstanceScriptStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull FetchInstanceScriptStepNode.StepType type = StepType.FetchInstanceScript;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FetchInstanceScriptStepInfo fetchInstanceScriptStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return fetchInstanceScriptStepInfo;
  }

  enum StepType {
    FetchInstanceScript(StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
