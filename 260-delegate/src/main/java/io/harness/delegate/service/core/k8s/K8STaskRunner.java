/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.configuration.DelegateConfiguration;
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
import java.io.InputStreamReader;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8STaskRunner {
  private static final String HARNESS_DELEGATE_NG = "harness-delegate-ng";
  private static final String DELEGATE_FIELD_MANAGER = "delegate-field-manager";
  private static final String K8S_TASK_TEMPLATE =
      "/io/harness/delegate/service/core/k8s/resources/k8s_task_template.yaml";

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
   * @param taskPackage DelegateTaskPackage for the job
   * @throws IOException thrown in case of an issue serializing yaml objects
   * @throws ApiException thrown in case of an issue invoking K8S API
   */
  public void launchTask(final DelegateTaskPackage taskPackage) throws IOException, ApiException {
    // TODO: Check how to refresh service account token
    final V1Secret secret = createTaskSecrets(taskPackage.getDelegateTaskId(), taskPackage.getSecrets());
    final V1ConfigMap taskPackageConfMap = createTaskConfig(taskPackage.getDelegateTaskId(), taskPackage);
    final V1ConfigMap delegateConfigConfMap = createDelegateConfig(taskPackage.getDelegateTaskId());

    final var taskPackageVol = K8SVolumeUtils.fromConfigMap(taskPackageConfMap, "task-package");
    final var delegateConfigVol = K8SVolumeUtils.fromConfigMap(delegateConfigConfMap, "delegate-configuration");
    final var secretVolume = K8SVolumeUtils.fromSecret(secret);
    createTaskJob(taskPackage.getDelegateTaskId(), taskPackageVol, delegateConfigVol, secretVolume);

    log.info("Task job created for id {}!!!", taskPackage.getDelegateTaskId());
  }

  /**
   * Cleans up K8S resources after the task is launched.
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

  private void createTaskJob(final String taskId, final V1Volume taskPackageVolume, final V1Volume delegateConfigVolume,
      final V1Volume secretVolume) throws ApiException {
    final var jobTemplate = getClass().getResourceAsStream(K8S_TASK_TEMPLATE);

    if (jobTemplate == null) {
      throw new IllegalStateException("K8S Task template not found at " + K8S_TASK_TEMPLATE);
    }

    final var job = Yaml.loadAs(new InputStreamReader(jobTemplate), K8SJob.class)
                        .name(getJobName(taskId))
                        .namespace(HARNESS_DELEGATE_NG)
                        .addVolume(taskPackageVolume, "/etc/config")
                        .addVolume(delegateConfigVolume, "/etc/delegate-config")
                        .addVolume(secretVolume, "/etc/secret")
                        .addEnvVar("ACCOUNT_ID", delegateConfiguration.getAccountId())
                        .addEnvVar("TASK_ID", taskId)
                        .addEnvVar("DELEGATE_NAME", "");

    log.debug("Creating Task Job with YAML:\n{}", Yaml.dump(job));

    batchApi.createNamespacedJob(HARNESS_DELEGATE_NG, job, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }

  private V1Secret createTaskSecrets(final String taskId, final Set<String> secrets) throws ApiException {
    final var secret =
        new K8SSecret(getSecretName(taskId), HARNESS_DELEGATE_NG); // TODO: Put secret data in the secret object

    coreApi.createNamespacedSecret(HARNESS_DELEGATE_NG, secret, null, null, DELEGATE_FIELD_MANAGER, "Warn");
    return secret;
  }

  private V1ConfigMap createTaskConfig(final String taskId, final DelegateTaskPackage taskData)
      throws IOException, ApiException {
    final var configYaml = new YamlUtils().dump(taskData);
    final var configMap =
        new K8SConfigMap(getConfigName(taskId), HARNESS_DELEGATE_NG).putDataItem("config.yaml", configYaml);

    coreApi.createNamespacedConfigMap(HARNESS_DELEGATE_NG, configMap, null, null, DELEGATE_FIELD_MANAGER, "Warn");
    return configMap;
  }

  private V1ConfigMap createDelegateConfig(final String taskId) throws IOException, ApiException {
    final var configYaml = new YamlUtils().dump(delegateConfiguration);
    final var configMap = new K8SConfigMap(getConfigName(taskId + "-delegate-config"), HARNESS_DELEGATE_NG)
                              .putDataItem("config.yaml", configYaml);

    coreApi.createNamespacedConfigMap(HARNESS_DELEGATE_NG, configMap, null, null, DELEGATE_FIELD_MANAGER, "Warn");
    return configMap;
  }

  @NonNull
  private String getJobName(final String taskId) {
    return normalizeResourceName("task-" + taskId + "-job");
  }

  @NonNull
  private String getSecretName(final String taskId) {
    return normalizeResourceName("task-" + taskId + "-secret");
  }

  @NonNull
  private String getConfigName(final String taskId) {
    return normalizeResourceName("task-" + taskId + "-config");
  }

  // K8S resource name needs to contain only lowercase alphanumerics . and _, but must start and end with alphanumerics
  // Regex used by K8S for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*'
  private String normalizeResourceName(final String resourceName) {
    return resourceName.trim().toLowerCase().replaceAll("_", ".");
  }
}
