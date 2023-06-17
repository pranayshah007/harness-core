/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.network.SafeHttpCall.execute;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.service.impl.ContainerMetadataType.K8S;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.ContainerInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.ContainerServicePerpetualTaskParams;
import io.harness.perpetualtask.instancesync.K8sContainerInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class ContainerInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private K8sTaskHelper k8sTaskHelper;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private ContainerService containerService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private AwsEcsHelperServiceDelegate awsEcsHelperServiceDelegate;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    if (log.isDebugEnabled()) {
      log.debug("Running the InstanceSync perpetual task executor for task id: {}", taskId);
    }
    ContainerInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), ContainerInstanceSyncPerpetualTaskParams.class);

    return K8S.name().equals(taskParams.getContainerType())
        ? executeK8sContainerSyncTask(taskId, taskParams.getK8SContainerPerpetualTaskParams())
        : executeContainerServiceSyncTask(taskId, taskParams.getContainerServicePerpetualTaskParams());
  }

  private PerpetualTaskResponse executeContainerServiceSyncTask(
      PerpetualTaskId taskId, ContainerServicePerpetualTaskParams containerServicePerpetualTaskParams) {
    final SettingAttribute settingAttribute = (SettingAttribute) kryoSerializer.asObject(
        containerServicePerpetualTaskParams.getSettingAttribute().toByteArray());

    ContainerSyncResponse responseData =
        getContainerSyncResponse(containerServicePerpetualTaskParams, settingAttribute);
    publishInstanceSyncResult(
        taskId, settingAttribute.getAccountId(), containerServicePerpetualTaskParams.getNamespace(), responseData);

    boolean isFailureResponse = FAILURE == responseData.getCommandExecutionStatus();
    return PerpetualTaskResponse.builder()
        .responseCode(Response.SC_OK)
        .responseMessage(isFailureResponse ? responseData.getErrorMessage() : "success")
        .build();
  }

  private ContainerSyncResponse getContainerSyncResponse(
      ContainerServicePerpetualTaskParams containerServicePerpetualTaskParams, SettingAttribute settingAttribute) {
    @SuppressWarnings("unchecked")
    final List<EncryptedDataDetail> encryptedDataDetails = (List<EncryptedDataDetail>) kryoSerializer.asObject(
        containerServicePerpetualTaskParams.getEncryptionDetails().toByteArray());

    ContainerServiceParams request =
        ContainerServiceParams.builder()
            .settingAttribute(settingAttribute)
            .containerServiceName(containerServicePerpetualTaskParams.getContainerSvcName())
            .encryptionDetails(encryptedDataDetails)
            .clusterName(containerServicePerpetualTaskParams.getClusterName())
            .namespace(containerServicePerpetualTaskParams.getNamespace())
            .region(containerServicePerpetualTaskParams.getRegion())
            .subscriptionId(containerServicePerpetualTaskParams.getSubscriptionId())
            .resourceGroup(containerServicePerpetualTaskParams.getResourceGroup())
            .masterUrl(containerServicePerpetualTaskParams.getMasterUrl())
            .releaseName(containerServicePerpetualTaskParams.getReleaseName())
            .build();

    try {
      boolean isEcs = settingAttribute.getValue() instanceof AwsConfig;
      boolean ecsServiceExists = false;
      if (isEcs) {
        ecsServiceExists =
            awsEcsHelperServiceDelegate.serviceExists(request.getSettingAttribute(), encryptedDataDetails,
                containerServicePerpetualTaskParams.getRegion(), containerServicePerpetualTaskParams.getClusterName(),
                containerServicePerpetualTaskParams.getContainerSvcName());
      }

      List<ContainerInfo> containerInfos = containerService.getContainerInfos(request, true);
      return ContainerSyncResponse.builder()
          .containerInfoList(containerInfos)
          .commandExecutionStatus((containerInfos != null) ? SUCCESS : FAILURE)
          .controllerName(request.getContainerServiceName())
          .isEcs(isEcs)
          .ecsServiceExists(ecsServiceExists)
          .releaseName(containerServicePerpetualTaskParams.getReleaseName())
          .namespace(containerServicePerpetualTaskParams.getNamespace())
          .build();
    } catch (Exception exception) {
      log.warn(String.format("Failed to fetch containers info for namespace: [%s] and svc:[%s] due to [%s] ",
          containerServicePerpetualTaskParams.getNamespace(), containerServicePerpetualTaskParams.getContainerSvcName(),
          ExceptionUtils.getMessage(exception)));

      return ContainerSyncResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(exception.getMessage())
          .build();
    }
  }

  private PerpetualTaskResponse executeK8sContainerSyncTask(
      PerpetualTaskId taskId, K8sContainerInstanceSyncPerpetualTaskParams k8sContainerInstanceSyncPerpetualTaskParams) {
    final K8sClusterConfig k8sClusterConfig = (K8sClusterConfig) kryoSerializer.asObject(
        k8sContainerInstanceSyncPerpetualTaskParams.getK8SClusterConfig().toByteArray());
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, true);

    K8sTaskExecutionResponse responseData =
        getK8sTaskResponse(k8sContainerInstanceSyncPerpetualTaskParams, kubernetesConfig);

    K8sInstanceSyncResponse k8sTaskResponse = (K8sInstanceSyncResponse) responseData.getK8sTaskResponse();
    if (Objects.nonNull(k8sTaskResponse)) {
      k8sTaskResponse.setClusterName(k8sClusterConfig.getClusterName());
    }

    publishInstanceSyncResult(taskId, k8sContainerInstanceSyncPerpetualTaskParams.getAccountId(),
        k8sContainerInstanceSyncPerpetualTaskParams.getNamespace(), responseData);

    boolean isFailureResponse = FAILURE == responseData.getCommandExecutionStatus();
    return PerpetualTaskResponse.builder()
        .responseCode(Response.SC_OK)
        .responseMessage(isFailureResponse ? responseData.getErrorMessage() : "success")
        .build();
  }

  private K8sTaskExecutionResponse getK8sTaskResponse(
      K8sContainerInstanceSyncPerpetualTaskParams k8sContainerInstanceSyncPerpetualTaskParams,
      KubernetesConfig kubernetesConfig) {
    try {
      long timeoutMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_STEADY_STATE_TIMEOUT);
      String namespace = k8sContainerInstanceSyncPerpetualTaskParams.getNamespace();
      String releaseName = k8sContainerInstanceSyncPerpetualTaskParams.getReleaseName();
      List<K8sPod> k8sPodList =
          k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutMillis);

      return K8sTaskExecutionResponse.builder()
          .k8sTaskResponse(K8sInstanceSyncResponse.builder()
                               .k8sPodInfoList(k8sPodList)
                               .releaseName(releaseName)
                               .namespace(namespace)
                               .build())
          .commandExecutionStatus((k8sPodList != null) ? SUCCESS : FAILURE)
          .build();

    } catch (Exception exception) {
      log.warn(String.format("Failed to fetch k8s pod list for namespace: [%s] and releaseName:[%s] due to [%s]",
          k8sContainerInstanceSyncPerpetualTaskParams.getNamespace(),
          k8sContainerInstanceSyncPerpetualTaskParams.getReleaseName(), ExceptionUtils.getMessage(exception)));
      return K8sTaskExecutionResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(exception.getMessage())
          .build();
    }
  }

  private void publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, String namespace, DelegateResponseData responseData) {
    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResult(taskId.getId(), accountId, responseData));
    } catch (Exception e) {
      log.warn(String.format(
          "Failed to publish container instance sync result. namespace [%s] and PerpetualTaskId [%s] due to [%s]",
          namespace, taskId.getId(), ExceptionUtils.getMessage(e)));
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
