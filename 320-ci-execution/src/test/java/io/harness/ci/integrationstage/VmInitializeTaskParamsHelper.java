package io.harness.ci.integrationstage;

import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
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

  private static VmPoolYaml getPoolWithName(String poolName) {
    return VmPoolYaml.builder()
        .spec(VmPoolYamlSpec.builder()
                  .harnessImageConnectorRef(ParameterField.<String>builder().build())
                  .poolName(ParameterField.createValueField(poolName))
                  .build())
        .build();
  }
}
