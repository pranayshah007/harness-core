/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveArchType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;

import static java.lang.String.format;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class DockerInitializeTaskParamsBuilder {
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private SecretVolumesHelper secretVolumesHelper;
  @Inject private VmInitializeUtils vmInitializeUtils;

  public CIVmInitializeTaskParams getDockerInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    return vmInitializeTaskParamsBuilder.getVmInitializeParams(
        initializeStepInfo, ambiance, "", Collections.emptyList());
  }

  public String getHostedPoolId(ParameterField<Platform> platform) {
    OSType os = OSType.Linux;
    ArchType arch = ArchType.Amd64;
    if (platform != null && platform.getValue() != null) {
      os = resolveOSType(platform.getValue().getOs());
      arch = resolveArchType(platform.getValue().getArch());
    }

    return format("%s-%s", os.toString().toLowerCase(), arch.toString().toLowerCase());
  }

  //  public static InfraInfo validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
  //    validateInfrastructure(infrastructure);
  //    return DockerInfraInfo.builder().build();
  //  }

  public static CIVmInitializeTaskParams.Type validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
    validateInfrastructure(infrastructure);
    return CIVmInitializeTaskParams.Type.DOCKER;
  }

  public static void validateInfrastructure(Infrastructure infrastructure) {
    if (((DockerInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Docker input infrastructure can not be empty");
    }
  }
}
