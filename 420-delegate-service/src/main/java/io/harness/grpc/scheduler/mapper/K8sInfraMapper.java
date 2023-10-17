/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.K8sInfraSpec;
import io.harness.delegate.LogConfig;
import io.harness.delegate.core.beans.K8SInfra;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sInfraMapper {
  public static K8SInfra map(final K8sInfraSpec infra, final Map<String, String> executionTaskIds,
      final LogConfig loggingConfig, final String loggingToken) {
    final var k8SInfraBuilder = K8SInfra.newBuilder();
    for (final var task : infra.getStepsList()) {
      final var stepTaskId = executionTaskIds.get(task.getStepId());
      final var k8SStep = K8SStepMapper.map(task, stepTaskId);

      k8SInfraBuilder.addSteps(k8SStep);
    }

    for (final var resource : infra.getResourcesList()) {
      k8SInfraBuilder.addResources(ResourceMapper.INSTANCE.map(resource));
    }

    final var securityContext = SecurityContextMapper.map(infra.getSecurityContext());
    final var compute = ComputeMapper.INSTANCE.map(infra.getComputeResource());

    return k8SInfraBuilder.setCompute(compute)
        .setSecurityContext(securityContext)
        .setWorkingDir("/opt/harness")
        .setLogPrefix(loggingConfig.getLogKey())
        .setLogToken(loggingToken)
        .build();
  }
}
