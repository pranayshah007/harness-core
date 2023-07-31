/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.AWS_CDK_BOOTSTRAP)
@TypeAlias("awsCdkBootstrapStepNode")
@RecasterAlias("io.harness.cdng.provision.awscdk.AwsCdkBootstrapStepNode")
public class AwsCdkBootstrapStepNode extends CdAbstractStepNode {
  @JsonProperty("type")
  @NotNull
  AwsCdkBootstrapStepNode.StepType type = AwsCdkBootstrapStepNode.StepType.AwsCdkBootstrap;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  AwsCdkBootstrapStepInfo awsCdkBootstrapStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.AWS_CDK_BOOTSTRAP;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return awsCdkBootstrapStepInfo;
  }

  enum StepType {
    AwsCdkBootstrap(StepSpecTypeConstants.AWS_CDK_BOOTSTRAP);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
