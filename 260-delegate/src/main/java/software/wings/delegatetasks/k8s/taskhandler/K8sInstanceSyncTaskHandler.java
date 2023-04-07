/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;

import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sInstanceSyncTaskHandler extends K8sTaskHandler {
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sInstanceSyncTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sInstanceSyncTaskParameters"));
    }

    K8sInstanceSyncTaskParameters k8sInstanceSyncTaskParameters = (K8sInstanceSyncTaskParameters) k8sTaskParameters;

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sInstanceSyncTaskParameters.getK8sClusterConfig(), k8sDelegateTaskParams.getWorkingDirectory(), false);

    long steadyStateTimeoutInMillis =
        getTimeoutMillisFromMinutes(k8sInstanceSyncTaskParameters.getTimeoutIntervalInMin());

    String namespace = k8sInstanceSyncTaskParameters.getNamespace();
    String releaseName = k8sInstanceSyncTaskParameters.getReleaseName();
    List<K8sPod> k8sPodList =
        k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, steadyStateTimeoutInMillis);

    K8sInstanceSyncResponse k8sInstanceSyncResponse = K8sInstanceSyncResponse.builder()
                                                          .k8sPodInfoList(k8sPodList)
                                                          .releaseName(releaseName)
                                                          .namespace(namespace)
                                                          .build();

    return k8sTaskHelper.getK8sTaskExecutionResponse(k8sInstanceSyncResponse, (k8sPodList != null) ? SUCCESS : FAILURE);
  }
}
