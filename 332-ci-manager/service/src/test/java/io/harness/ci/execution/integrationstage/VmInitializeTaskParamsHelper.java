/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;

public class VmInitializeTaskParamsHelper {
  public static final String POOL_NAME = "pool";

  public static InitializeStepInfo getInitializeStep() {
    VmPoolYaml vmPoolYaml = getPoolWithName(POOL_NAME);
    vmPoolYaml.getSpec().setOs(ParameterField.createValueField(OSType.Linux));
    VmInfraYaml vmInfraYaml = VmInfraYaml.builder().spec(vmPoolYaml).build();

    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder()
            .sharedPaths(ParameterField.createValueField(Arrays.asList("/shared")))
            .build();
    return InitializeStepInfo.builder().infrastructure(vmInfraYaml).stageElementConfig(integrationStageConfig).build();
  }

  public static InitializeStepInfo getHostedVmInitializeStep() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraSpec.builder().platform(ParameterField.createValueField(platform)).build())
            .build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder()
            .sharedPaths(ParameterField.createValueField(Arrays.asList("/shared")))
            .build();
    return InitializeStepInfo.builder()
        .infrastructure(hostedVmInfraYaml)
        .stageElementConfig(integrationStageConfig)
        .build();
  }

  private static VmPoolYaml getPoolWithName(String poolName) {
    return VmPoolYaml.builder()
        .spec(VmPoolYamlSpec.builder()
                  .harnessImageConnectorRef(ParameterField.<String>builder().build())
                  .poolName(ParameterField.createValueField(poolName))
                  .build())
        .build();
  }
}
