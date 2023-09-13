/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DliteVmInitializeTaskParamsBuilder {
  @Inject VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;

  public DliteVmInitializeTaskParams getDliteVmInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    validateInfrastructure(infrastructure);
    return vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance);
  }

  //  public static InfraInfo validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
  //    validateInfrastructure(infrastructure);
  //    return DliteVmInfraInfo.builder().build();
  //  }

  public static CIVmInitializeTaskParams.Type validateInfrastructureAndGetInfraInfo(Infrastructure infrastructure) {
    validateInfrastructure(infrastructure);
    return CIInitializeTaskParams.Type.DLITE_VM;
  }

  public static void validateInfrastructure(Infrastructure infrastructure) {
    if (((HostedVmInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Dlite vm input infrastructure can not be empty");
    }
  }
}
