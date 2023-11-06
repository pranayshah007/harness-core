/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.beans.FeatureName.QUEUE_CI_EXECUTIONS_CONCURRENCY;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class BuildJobEnvInfoBuilder {
  @Inject private CIFeatureFlagService ffService;
  private static final int VM_INIT_TIMEOUT_MILLIS = 900 * 1000;
  private static final int K8_WIN_INIT_TIMEOUT_MILLIS = 900 * 1000;

  public int getTimeout(Infrastructure infrastructure, String accountId) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    boolean queueEnabled = infrastructure.getType() == Infrastructure.Type.HOSTED_VM
        && ffService.isEnabled(QUEUE_CI_EXECUTIONS_CONCURRENCY, accountId);

    if (infrastructure.getType() == Type.KUBERNETES_DIRECT) {
      return getK8Timeout((K8sDirectInfraYaml) infrastructure, queueEnabled);
    } else if (infrastructure.getType() == Type.VM) {
      if (queueEnabled) {
        return InitializeStepInfo.DEFAULT_TIMEOUT_WITH_QUEUE;
      }
      return VM_INIT_TIMEOUT_MILLIS;
    } else {
      if (queueEnabled) {
        return InitializeStepInfo.DEFAULT_TIMEOUT_WITH_QUEUE;
      }
      return InitializeStepInfo.DEFAULT_TIMEOUT;
    }
  }

  private int getK8Timeout(K8sDirectInfraYaml k8sDirectInfraYaml, boolean queueEnabled) {
    if (k8sDirectInfraYaml.getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();
    int timeoutInMillis = InitializeStepInfo.DEFAULT_TIMEOUT;

    OSType os = resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
    if (os == OSType.Windows && !queueEnabled) {
      timeoutInMillis = K8_WIN_INIT_TIMEOUT_MILLIS;
    }

    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      timeoutInMillis = (int) Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
    }

    // if  this queue feature flag is enabled then the final timeout must be more as every pipeline in the queue should
    // run without timeout error
    if (queueEnabled) {
      timeoutInMillis = InitializeStepInfo.DEFAULT_TIMEOUT_WITH_QUEUE;
    }
    return timeoutInMillis;
  }
}
