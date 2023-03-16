/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.ExecutionEnvironment;
import io.harness.delegate.core.beans.TaskDescriptor;
import io.harness.delegate.core.beans.TaskSecret;
import io.harness.serializer.YamlUtils;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8STaskRunner {
  private static final String HARNESS_DELEGATE_NG = "harness-delegate-ng";
  private static final String SECRETS_INPUT_MNT_PATH = "/opt/harness/secrets/input/";
  private static final String SECRETS_OUT_MNT_PATH = "/opt/harness/secrets/out/";
  private static final String DELEGATE_CONFIG_MNT_PATH = "/etc/delegate-config";
  private static final String TASK_INPUT_MNT_PATH = "/etc/config";
  private static final String DELEGATE_CONFIG_FILE = "config.yaml";
  private static final String TASK_INPUT_FILE = "task-data.bin";

  private static final Pattern RESOURCE_NAME_NORMALIZER = Pattern.compile("_");

  private final BatchV1Api batchApi;
  private final CoreV1Api coreApi;
  private final DelegateConfiguration delegateConfiguration;

  @Inject
  public K8STaskRunner(final DelegateConfiguration delegateConfiguration, final ApiClient apiClient)
      throws IOException {
    this.batchApi = new BatchV1Api(apiClient);
    this.coreApi = new CoreV1Api(apiClient);
    this.delegateConfiguration = delegateConfiguration;
  }

  /**
   * Launches a K8S Job for the task with DelegateTaskPackage
   *
   * @param task DelegateTaskPackage for the job
   * @throws IOException  thrown in case of an issue serializing yaml objects
   * @throws ApiException thrown in case of an issue invoking K8S API
   */
  public void launchTask(final TaskDescriptor task) throws IOException, ApiException {
    // TODO: Check how to refresh service account toke
    log.info("Creating delegate config for task {}", task.getId());
    final var delegateConfigConfMap = createDelegateConfig(task.getId());
    final var delegateConfigVol = K8SVolumeUtils.fromConfigMap(delegateConfigConfMap, "delegate-configuration");

    log.info("Creating task input for task {}", task.getId());
    final var taskPackageConfMap = createTaskConfig(task.getId(), task);
    final var taskPackageVol = K8SVolumeUtils.fromConfigMap(taskPackageConfMap, "task-package");

    final var jobSpec = createTaskSpec(task.getId(), task.getRuntime(), taskPackageVol, delegateConfigVol);
    if (!task.getInputSecretsList().isEmpty()) {
      log.info("Creating secret input for task {}", task.getId());
      // At this point all secrets should be for K8SRunner, and each PluginSecret of different type (but could be same
      // image). E.g. We can have image that has several secret providers implemented
      final var secretsByPlugin = task.getInputSecretsList().stream().collect(
          Collectors.groupingBy(secret -> secret.getRuntime().getUses(), Collectors.toList()));

      for (final var entry : secretsByPlugin.entrySet()) {
        final var secret = createTaskSecrets(task.getId(), entry.getValue());
        final var secretVol = K8SVolumeUtils.fromSecret(secret, "secret-input");
        jobSpec
            .addInitContainer("secret-decryption", entry.getKey(),
                entry.getValue().get(0).getRuntime().getResource().getMemory(),
                entry.getValue().get(0).getRuntime().getResource().getCpu())
            .addVolume(secretVol, SECRETS_INPUT_MNT_PATH)
            .addVolume(K8SVolumeUtils.emptyDir("secret-output"), SECRETS_OUT_MNT_PATH);
      }
    }

    log.debug("Creating Task Job with YAML:\n{}", Yaml.dump(jobSpec));
    jobSpec.create(batchApi, HARNESS_DELEGATE_NG);

    log.info("Task job created for id {}!!!", task.getId());
  }

  /**
   * Cleans up K8S resources after the task is launched.
   *
   * @param taskId taskId for which cleanup is needed
   * @throws ApiException exception in case there is an issue calling K8S API
   */
  public void cleanupTaskData(final String taskId) throws ApiException {
    coreApi.deleteNamespacedSecret(getSecretName(taskId), HARNESS_DELEGATE_NG, null, null, 30, true, null, null);
    coreApi.deleteNamespacedConfigMap(getConfigName(taskId), HARNESS_DELEGATE_NG, null, null, 30, true, null, null);
    coreApi.deleteNamespacedConfigMap(
        getConfigName(taskId + "-delegate-config"), HARNESS_DELEGATE_NG, null, null, 30, true, null, null);
    log.info("Task data cleaned up for {}", taskId);
  }

  private K8SJob createTaskSpec(final String taskId, final ExecutionEnvironment runtime,
      final V1Volume taskPackageVolume, final V1Volume delegateConfigVolume) {
    return new K8SJob(getJobName(taskId), HARNESS_DELEGATE_NG)
        .addContainer(
            "delegate-task", runtime.getUses(), runtime.getResource().getMemory(), runtime.getResource().getCpu())
        .addVolume(taskPackageVolume, TASK_INPUT_MNT_PATH) // FixMe: Volume should be property of container, not job
        .addVolume(delegateConfigVolume, DELEGATE_CONFIG_MNT_PATH)
        .addEnvVar("ACCOUNT_ID",
            delegateConfiguration.getAccountId()) // FixMe: EnvVar should be property of container, not job
        .addEnvVar("TASK_ID", taskId)
        .addEnvVar("TASK_DATA_PATH", "/etc/config/task-data.bin")
        .addEnvVar("DELEGATE_NAME", "");
  }

  private V1Secret createTaskSecrets(final String taskId, final List<TaskSecret> secrets) throws ApiException {
    final var k8sSecret = new K8SSecret(getSecretName(taskId), HARNESS_DELEGATE_NG);

    for (final var secret : secrets) {
      final var secretFilename = UUID.randomUUID().toString();
      if (secret.getConfig().hasBinaryData()) {
        k8sSecret.putDataItem(secretFilename + ".config", secret.getConfig().getBinaryData().toByteArray())
            .putDataItem(secretFilename + ".bin", secret.getSecrets().getBinaryData().toByteArray());
      } else {
        k8sSecret.putDataItem(secretFilename + ".config", secret.getConfig().getProtoData().toByteArray())
            .putDataItem(secretFilename + ".bin", secret.getSecrets().getProtoData().toByteArray());
      }
    }
    return k8sSecret.create(coreApi, HARNESS_DELEGATE_NG);
  }

  private V1ConfigMap createTaskConfig(final String taskId, final TaskDescriptor descriptor) throws ApiException {
    final var taskData = descriptor.getInput().hasBinaryData() ? descriptor.getInput().getBinaryData().toByteArray()
                                                               : descriptor.getInput().getProtoData().toByteArray();
    final var configMap =
        new K8SConfigMap(getConfigName(taskId), HARNESS_DELEGATE_NG).putBinaryDataItem(TASK_INPUT_FILE, taskData);

    return configMap.create(coreApi, HARNESS_DELEGATE_NG);
  }

  private V1ConfigMap createDelegateConfig(final String taskId) throws IOException, ApiException {
    final var configYaml = new YamlUtils().dump(delegateConfiguration);
    final var configMap = new K8SConfigMap(getConfigName(taskId + "-delegate-config"), HARNESS_DELEGATE_NG)
                              .putDataItem(DELEGATE_CONFIG_FILE, configYaml);

    return configMap.create(coreApi, HARNESS_DELEGATE_NG);
  }

  @NonNull
  private static String getJobName(final String taskId) {
    return normalizeResourceName(taskId + "-job");
  }

  @NonNull
  private static String getSecretName(final String taskId) {
    return normalizeResourceName(taskId + "-secret");
  }

  @NonNull
  private static String getConfigName(final String taskId) {
    return normalizeResourceName(taskId + "-config");
  }

  // K8S resource name needs to contain only lowercase alphanumerics . and -, but must start and end with alphanumerics
  // Regex used by K8S for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*'
  private static String normalizeResourceName(final String resourceName) {
    return "task-" + RESOURCE_NAME_NORMALIZER.matcher(resourceName.trim().toLowerCase(Locale.ROOT)).replaceAll(".");
  }
}
