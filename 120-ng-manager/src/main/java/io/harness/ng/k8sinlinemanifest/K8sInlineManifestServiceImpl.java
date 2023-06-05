/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.k8sinlinemanifest;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
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
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class K8sInlineManifestServiceImpl implements K8sInlineManifestService {
  private static final String KUBERNETES_APPLY_COMMAND_NAME = "K8s Apply";
  private static final String ORG_OWNER = "%s";
  private static final String PROJECT_OWNER = "%s/%s";
  private static final long EXECUTION_TIMEOUT = 15;
  private static final int TIMEOUT_INTERVAL_IN_MINUTES = 15;
  private static final int INITIAL_HASH_MAP_CAPACITY = 2;

  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public String applyK8sManifest(K8sManifestRequest k8sManifestRequest, String uid, NotifyCallback notifyCallback) {
    LinkedHashMap<String, String> logAbstractions = buildLogAbstractions(k8sManifestRequest, uid);
    DelegateTaskRequestBuilder requestBuilder =
        DelegateTaskRequest.builder()
            .accountId(k8sManifestRequest.getAccountId())
            .taskParameters(getTaskParams(k8sManifestRequest.getAccountId(), k8sManifestRequest.getOrgId(),
                k8sManifestRequest.getProjectId(), k8sManifestRequest.getK8sConnectorId(),
                k8sManifestRequest.getReleaseIdentifier(), uid, k8sManifestRequest.getK8sManifest()))
            .taskType(TaskType.K8S_COMMAND_TASK_NG.name())
            .executionTimeout(Duration.ofMinutes(EXECUTION_TIMEOUT))
            .taskSetupAbstractions(buildAbstractions(k8sManifestRequest.getOrgId(), k8sManifestRequest.getProjectId()))
            .logStreamingAbstractions(logAbstractions);

    if (isNotEmpty(k8sManifestRequest.getDelegateId())) {
      requestBuilder.eligibleToExecuteDelegateIds(Collections.singletonList(k8sManifestRequest.getDelegateId()));
    }

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(requestBuilder.build(), Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, notifyCallback, taskId);
    return taskId;
  }

  @NonNull
  private static LinkedHashMap<String, String> buildLogAbstractions(K8sManifestRequest k8sManifestRequest, String uid) {
    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put("accountId", k8sManifestRequest.getAccountId());
    logAbstractions.put("uid", uid);
    return logAbstractions;
  }

  private TaskParameters getTaskParams(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String releaseIdentifier, String uid, String manifestContent) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(accountIdentifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build();
    ConnectorInfoDTO responseDTO = k8sEntityHelper.getConnectorInfoDTO(connectorIdentifier, ngAccess);

    LocalFileStoreDelegateConfig storeDelegateConfig =
        LocalFileStoreDelegateConfig.builder()
            .filePaths(Collections.singletonList("/template/deployment.yaml"))
            .manifestType(ManifestType.K8Manifest)
            .manifestIdentifier("K8s Inline Manifest")
            .manifestFiles(Collections.singletonList(ManifestFiles.builder()
                                                         .fileContent(manifestContent)
                                                         .fileName("deployment.yaml")
                                                         .filePath("/template/deployment.yaml")
                                                         .build()))
            .build();

    return K8sApplyRequest.builder()
        .skipDryRun(true)
        .releaseName(releaseIdentifier + "-" + uid)
        .commandName(KUBERNETES_APPLY_COMMAND_NAME)
        .taskType(K8sTaskType.APPLY)
        .timeoutIntervalInMin(TIMEOUT_INTERVAL_IN_MINUTES)
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

  private Map<String, String> buildAbstractions(String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(INITIAL_HASH_MAP_CAPACITY);
    String owner = getOwner(orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  private static String getOwner(String orgIdentifier, String projectIdentifier) {
    String owner = null;
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      owner = String.format(PROJECT_OWNER, orgIdentifier, projectIdentifier);
    } else {
      if (isNotEmpty(orgIdentifier)) {
        owner = String.format(ORG_OWNER, orgIdentifier);
      }
    }
    return owner;
  }
}
