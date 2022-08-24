/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.ManifestType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.ng.core.BaseNGAccess;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ChaosServiceImpl implements ChaosService {
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  private final String K8S_APPLY_COMMAND_NAME = "K8s Apply";

  @Override
  public String applyK8sManifest(ChaosK8sRequest chaosK8sRequest) {
    String chaosUid = generateUuid();
    LinkedHashMap<String, String> logAbstractions = buildLogAbstractions(chaosK8sRequest, chaosUid);
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(chaosK8sRequest.getAccountId())
            .taskParameters(getTaskParams(
                chaosK8sRequest.getAccountId(), chaosK8sRequest.getK8sConnectorId(), chaosK8sRequest.getK8sManifest()))
            .eligibleToExecuteDelegateIds(Arrays.asList(chaosK8sRequest.getDelegateId()))
            .taskType(TaskType.K8S_COMMAND_TASK_NG.name())
            .executionTimeout(Duration.ofMinutes(15))
            .taskSetupAbstraction("ng", "true")
            .logStreamingAbstractions(logAbstractions)
            .build();
    String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new ChaosNotifyCallback(chaosUid), taskId);
    return taskId;
  }

  @NonNull
  private LinkedHashMap<String, String> buildLogAbstractions(ChaosK8sRequest chaosK8sRequest, String chaosUid) {
    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put("accountId", chaosK8sRequest.getAccountId());
    logAbstractions.put("chaosUid", chaosUid);
    return logAbstractions;
  }

  private TaskParameters getTaskParams(String accountIdentifier, String connectorIdentifier, String manifestContent) {
    BaseNGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountIdentifier).build();
    ConnectorInfoDTO responseDTO = k8sEntityHelper.getConnectorInfoDTO(connectorIdentifier, ngAccess);

    LocalFileStoreDelegateConfig storeDelegateConfig =
        LocalFileStoreDelegateConfig.builder()
            .filePaths(Collections.singletonList("/template/deployment.yaml"))
            .manifestType(ManifestType.K8Manifest)
            .manifestIdentifier("Chaos Inline Manifest")
            .manifestFiles(Collections.singletonList(ManifestFiles.builder()
                                                         .fileContent(manifestContent)
                                                         .fileName("deployment.yaml")
                                                         .filePath("/template/deployment.yaml")
                                                         .build()))
            .build();

    return K8sApplyRequest.builder()
        .skipDryRun(true)
        .releaseName("chaos-" + generateUuid())
        .commandName(K8S_APPLY_COMMAND_NAME)
        .taskType(K8sTaskType.APPLY)
        .timeoutIntervalInMin(15)
        .k8sInfraDelegateConfig(
            DirectK8sInfraDelegateConfig.builder()
                .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) responseDTO.getConnectorConfig())
                .encryptionDataDetails(k8sEntityHelper.getEncryptionDataDetails(responseDTO, ngAccess))
                .build())
        .manifestDelegateConfig(K8sManifestDelegateConfig.builder().storeDelegateConfig(storeDelegateConfig).build())
        .accountId(accountIdentifier)
        .deprecateFabric8Enabled(true)
        .filePaths(Collections.singletonList("template/deployment.yaml"))
        .skipSteadyStateCheck(false)
        .shouldOpenFetchFilesLogStream(false)
        .useNewKubectlVersion(false)
        .useLatestKustomizeVersion(false)
        .useK8sApiForSteadyStateCheck(true)
        .build();
  }
}
