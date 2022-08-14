/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.buildstate;

import static java.lang.String.format;

import io.harness.beans.yaml.extended.infrastrucutre.*;
import io.harness.ci.integrationstage.*;
import io.harness.delegate.beans.ci.DockerInfraInfo;
import io.harness.delegate.beans.ci.InfraInfo;
import io.harness.delegate.beans.ci.VmInfraInfo;
import io.harness.exception.ngexception.CIStageExecutionException;

public class InfraInfoUtils {
  public static InfraInfo getInfraInfo(Infrastructure infrastructure, String stageRuntimeId) {
    Infrastructure.Type type = infrastructure.getType();
    switch (type) {
      case VM:
        if (((VmInfraYaml) infrastructure).getSpec() == null) {
          throw new CIStageExecutionException("VM input infrastructure can not be empty");
        }

        VmInfraYaml vmInfraYaml = (VmInfraYaml) infrastructure;
        if (vmInfraYaml.getSpec().getType() != VmInfraSpec.Type.POOL) {
          throw new CIStageExecutionException(
              format("Invalid VM infrastructure spec type: %s", vmInfraYaml.getSpec().getType()));
        }
        VmPoolYaml vmPoolYaml = (VmPoolYaml) vmInfraYaml.getSpec();
        String poolId = VmInitializeTaskParamsBuilder.getPoolName(vmPoolYaml);
        String harnessImageConnectorRef = (vmPoolYaml.getSpec().getHarnessImageConnectorRef().getValue());
        return VmInfraInfo.builder().poolId(poolId).harnessImageConnectorRef(harnessImageConnectorRef).build();
      case DOCKER:
        if (((DockerInfraYaml) infrastructure).getSpec() == null) {
          throw new CIStageExecutionException("Docker input infrastructure can not be empty");
        }
        return DockerInfraInfo.builder().stageRuntimeId(stageRuntimeId).build();
      default:
        throw new CIStageExecutionException(String.format("InfraInfo is not supported for %s", type.toString()));
    }
  }

  public static OSType getInfraOS(Infrastructure infrastructure) {
    Infrastructure.Type infraType = infrastructure.getType();

    if (infraType == Infrastructure.Type.VM) {
      return VmInitializeUtils.getOS(infrastructure);
    } else if (infraType == Infrastructure.Type.DOCKER) {
      return DockerInitializeStepUtils.getDockerOS(infrastructure);
    } else {
      throw new CIStageExecutionException(String.format("InfraInfo is not supported for %s", infraType.toString()));
    }
  }

  public static String getPoolId(InfraInfo infraInfo) {
    if (infraInfo.getType() == InfraInfo.Type.VM) {
      return ((VmInfraInfo) infraInfo).getPoolId();
    } else {
      return "";
    }
  }

  public static InfraInfo validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
    Infrastructure.Type type = infrastructure.getType();
    InfraInfo infraInfo;
    if (type == Infrastructure.Type.VM) {
      infraInfo = VmInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
    } else if (type == Infrastructure.Type.DOCKER) {
      infraInfo = DockerInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
    } else {
      infraInfo = DliteVmInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
    }

    return infraInfo;
  }
}
