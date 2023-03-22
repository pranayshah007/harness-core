/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

/**
 * Delegate task handler to execute a command or list of commands on a pod's container in a kubernetes cluster.
 */

import io.harness.delegate.beans.ci.ExecuteCommandTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.task.citasks.ExecuteCommandTaskHandler;
import io.harness.logging.CommandExecutionStatus;

import com.esotericsoftware.kryo.serializers.FieldSerializer.NotNull;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class K8ExecuteCommandTaskHandler implements ExecuteCommandTaskHandler {
  @NotNull private ExecuteCommandTaskHandler.Type type = ExecuteCommandTaskHandler.Type.K8;
  @Inject private K8sConnectorHelper k8sConnectorHelper;

  @Override
  public ExecuteCommandTaskHandler.Type getType() {
    return type;
  }

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(ExecuteCommandTaskParams executeCommandTaskParams) {
    return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
  }
}
