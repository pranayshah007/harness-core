/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class BuildJobEnvInfoBuilder {
  @Inject private InitializeStepInfoBuilder initializeStepInfoBuilder;
  @Inject private VmInitializeStepUtils vmInitializeStepUtils;

  public BuildJobEnvInfo getCIBuildJobEnvInfo(StageElementConfig stageElementConfig, Infrastructure infrastructure,
      CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps, Ambiance ambiance) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure is not set");
    }

    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT
        || infrastructure.getType() == Type.USE_FROM_STAGE || infrastructure.getType() == Type.KUBERNETES_HOSTED) {
      return initializeStepInfoBuilder.getInitializeStepInfoBuilder(
          stageElementConfig, infrastructure, ciExecutionArgs, steps, ambiance);
    } else if (infrastructure.getType() == Type.VM) {
      return vmInitializeStepUtils.getInitializeStepInfoBuilder(
          stageElementConfig, infrastructure, ciExecutionArgs, steps, ambiance);
    } else {
      throw new IllegalArgumentException("Input infrastructure type is not of type kubernetes or VM");
    }
  }

  public int getTimeout(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() == Type.KUBERNETES_DIRECT) {
      return getK8Timeout((K8sDirectInfraYaml) infrastructure);
    }

    return InitializeStepInfo.DEFAULT_TIMEOUT;
  }

  private int getK8Timeout(K8sDirectInfraYaml k8sDirectInfraYaml) {
    if (k8sDirectInfraYaml.getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();

    int timeoutInMillis = InitializeStepInfo.DEFAULT_TIMEOUT;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      timeoutInMillis = (int) Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
    }
    return timeoutInMillis;
  }
}
