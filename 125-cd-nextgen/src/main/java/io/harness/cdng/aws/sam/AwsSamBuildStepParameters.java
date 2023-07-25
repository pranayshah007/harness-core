/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("awsSamBuildStepParameters")
@RecasterAlias("io.harness.cdng.aws.sam.AwsSamBuildStepParameters")
public class AwsSamBuildStepParameters extends AwsSamBaseStepInfo implements AwsSamSpecParameters, StepParameters {
  ParameterField<List<String>> buildCommandOptions;
  ParameterField<String> samBuildDockerRegistryConnectorRef;

  @JsonIgnore String downloadManifestsFqn;

  @Builder(builderMethodName = "infoBuilder")
  public AwsSamBuildStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> image, ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,

      ParameterField<List<String>> buildCommandOptions, ParameterField<String> samBuildDockerRegistryConnectorRef,
      ParameterField<String> samVersion, String downloadManifestsFqn) {
    super(delegateSelectors, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy,
        samVersion);
    this.buildCommandOptions = buildCommandOptions;
    this.samBuildDockerRegistryConnectorRef = samBuildDockerRegistryConnectorRef;
    this.downloadManifestsFqn = downloadManifestsFqn;
  }
}
