/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("awsCdkBootstrapStepParameters")
@RecasterAlias("io.harness.cdng.provision.awscdk.AwsCdkBootstrapStepParameters")
public class AwsCdkBootstrapStepParameters extends AwsCdkBaseStepInfo implements SpecParameters, StepParameters {
  ParameterField<Boolean> exportTemplate;

  @Builder(builderMethodName = "infoBuilder")
  public AwsCdkBootstrapStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> image, ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,
      ParameterField<List<String>> commandOptions, ParameterField<String> appPath,
      ParameterField<Boolean> exportTemplate) {
    super(delegateSelectors, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy,
        commandOptions, appPath);
    this.exportTemplate = exportTemplate;
  }
}
