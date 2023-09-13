/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Singleton;

@Singleton
@OwnedBy(CI)
public class VmBuildJobTestHelper {
  public IntegrationStageNode getVmStage(String poolId) {
    VmInfraYaml awsVmInfraYaml = VmInfraYaml.builder()
                                     .spec(VmPoolYaml.builder()
                                               .spec(VmPoolYamlSpec.builder()
                                                         .identifier(poolId)
                                                         .poolName(ParameterField.createValueField(null))
                                                         .build())
                                               .build())
                                     .build();
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder()
            .type(IntegrationStageNode.StepType.CI)
            .integrationStageConfig(IntegrationStageConfigImpl.builder().infrastructure(awsVmInfraYaml).build())
            .build();
    return stageNode;
  }
}
