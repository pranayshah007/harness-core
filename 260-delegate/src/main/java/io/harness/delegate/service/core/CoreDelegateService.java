/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.service.common.SimpleDelegateAgent;
import io.harness.delegate.service.core.k8s.K8STaskRunner;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CoreDelegateService extends SimpleDelegateAgent {
  final DelegateDecryptionService decryptionService;

  @Override
  protected void abortTask(final DelegateTaskAbortEvent taskEvent) {}

  @Override
  protected void executeTask(final @NonNull DelegateTaskPackage taskPackage) {
    try {
      final var task = new K8STaskRunner();
      task.launchTask(taskPackage);
    } catch (IOException | ApiException | URISyntaxException e) {
      log.error("Failed to create the task {}", taskPackage.getDelegateTaskId());
    }
  }

  @Override
  protected ImmutableList<String> getCurrentlyExecutingTaskIds() {
    return ImmutableList.of("");
  }

  @Override
  protected ImmutableList<TaskType> getSupportedTasks() {
    return ImmutableList.of(TaskType.K8S_COMMAND_TASK);
  }

  @Override
  protected void onPreResponseSent(final DelegateTaskResponse response) {
    final DelegateMetaInfo responseMetadata =
        DelegateMetaInfo.builder().hostName(HOST_NAME).id(DelegateAgentCommonVariables.getDelegateId()).build();
    if (response.getResponse() instanceof DelegateTaskNotifyResponseData) {
      ((DelegateTaskNotifyResponseData) response.getResponse()).setDelegateMetaInfo(responseMetadata);
    }
  }

  private void updateWithDecryptSecrets(final DelegateTaskPackage taskPackage) {
    final var encryptionConfig = transformEncryptedRecords(taskPackage);

    final var decryptedRecords = decryptionService.decrypt(encryptionConfig);
    final var decryptedSecrets = decryptedRecords.values().stream().map(String::valueOf).collect(Collectors.toSet());
    taskPackage.getSecrets().addAll(decryptedSecrets);
  }

  private Map<EncryptionConfig, List<EncryptedRecord>> transformEncryptedRecords(
      final DelegateTaskPackage taskPackage) {
    return taskPackage.getSecretDetails().values().stream().collect(Collectors.groupingBy(secretDetail
        -> taskPackage.getEncryptionConfigs().get(secretDetail.getConfigUuid()),
        mapping(SecretDetail::getEncryptedRecord, toList())));
  }
}
