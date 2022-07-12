/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.task.TaskParameters;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;

@Singleton
public class ChaosServiceImpl implements ChaosService {
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Override
  public void applyK8sManifest(ChaosK8sRequest chaosK8sRequest) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(chaosK8sRequest.getAccountId())
            .serializationFormat(SerializationFormat.JSON)
            .taskParameters(getTaskParams(chaosK8sRequest))
            .eligibleToExecuteDelegateIds(Arrays.asList(chaosK8sRequest.getDelegateId()))
            .taskType(TaskType.K8S_COMMAND_TASK.name())
            .build();
    String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new ChaosNotifyCallback(), taskId);
  }

  private TaskParameters getTaskParams(ChaosK8sRequest request) {
    // todo: build task params.
    return K8sApplyTaskParameters.builder().build();
  }
}
