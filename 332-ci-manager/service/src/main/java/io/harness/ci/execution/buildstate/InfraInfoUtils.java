/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate;

import static java.lang.String.format;

import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.integrationstage.DliteVmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.DockerInitializeStepUtils;
import io.harness.ci.execution.integrationstage.DockerInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.VmInitializeUtils;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.exception.ngexception.CIStageExecutionException;

public class InfraInfoUtils {
  public static OSType getInfraOS(Infrastructure infrastructure) {
    Infrastructure.Type infraType = infrastructure.getType();

    if (infraType == Infrastructure.Type.VM || infraType == Infrastructure.Type.HOSTED_VM) {
      return VmInitializeUtils.getOS(infrastructure);
    } else if (infraType == Infrastructure.Type.DOCKER) {
      return DockerInitializeStepUtils.getOS(infrastructure);
    } else {
      throw new CIStageExecutionException(format("InfraInfo is not supported for %s", infraType.toString()));
    }
  }

  //  public static String getPoolId(InfraInfo infraInfo) {
  //    if (infraInfo.getType() == InfraInfo.Type.VM) {
  //      return ((VmInfraInfo) infraInfo).getPoolId();
  //    } else {
  //      return "";
  //    }
  //  }

  //  public static InfraInfo validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
  //    Infrastructure.Type type = infrastructure.getType();
  //    InfraInfo infraInfo;
  //    if (type == Infrastructure.Type.VM) {
  //      infraInfo = VmInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
  //    } else if (type == Infrastructure.Type.DOCKER) {
  //      infraInfo = DockerInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
  //    } else {
  //      infraInfo = DliteVmInitializeTaskParamsBuilder.validateInfrastructureAndGetInfraInfo(infrastructure);
  //    }
  //
  //    return infraInfo;
  //  }

  public static CIInitializeTaskParams.Type validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
    Infrastructure.Type type = infrastructure.getType();
    CIInitializeTaskParams.Type infraInfo;
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
