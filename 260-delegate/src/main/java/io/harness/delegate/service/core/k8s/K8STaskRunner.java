/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.serializer.YamlUtils;

import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Status;
import lombok.NonNull;
import software.wings.beans.bash.ShellScriptParameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8STaskRunner {
  private static final String HARNESS_DELEGATE_NG = "harness-delegate-ng";
  private static final String DELEGATE_FIELD_MANAGER = "delegate-field-manager";

  private static TaskData createDummyTaskData() {
    final Map<String, String> vars = ImmutableMap.of("key1", "va1", "key2", "val2");
    final var shellScriptParameters =
        new ShellScriptParameters("actId", "some,vars", "another,secret", "my super \n scrupt", 1000, "accId", "appId",
            "/root/not", Collections.emptyMap(), Collections.emptyMap(), vars);
    final var objects = new Object[] {shellScriptParameters};
    return TaskData.builder().parameters(objects).build();
  }

  public static void main(final String[] args) throws IOException {
    final var k8STaskRunner = new K8STaskRunner();
    final var taskId = UUID.randomUUID().toString();
    try {
      final var taskPackage = DelegateTaskPackage.builder()
                                  .delegateTaskId(taskId)
                                  .data(createDummyTaskData())
                                  .build();
      k8STaskRunner.launchTask(taskPackage);
      k8STaskRunner.cleanupTaskData(taskId);
    } catch (final IOException | ApiException | URISyntaxException e) {
      log.error("Exception creating task", e);
    }
  }

  private final BatchV1Api batchApi;
  private final CoreV1Api coreApi;

  public K8STaskRunner() throws IOException {
    //        final var client = ClientBuilder.cluster().build();
    final var client = Config.defaultClient(); // FixMe: This is API client from running in local
    Configuration.setDefaultApiClient(client);

    batchApi = new BatchV1Api(client);
    coreApi = new CoreV1Api(client);
  }

  public void launchTask(final DelegateTaskPackage taskPackage) throws IOException, ApiException, URISyntaxException {
    // TODO: Check how to refresh service account token
    final V1Secret secret = createTaskSecrets(taskPackage.getDelegateTaskId(), taskPackage.getSecrets());
    final V1ConfigMap configMap = createTaskConfig(taskPackage.getDelegateTaskId(), taskPackage);

    final var configMapVolume = K8SVolumeUtils.fromConfigMap(configMap);
    final var secretVolume = K8SVolumeUtils.fromSecret(secret);
    createTaskJob(taskPackage.getDelegateTaskId(), configMapVolume, secretVolume);

    log.info("done!!!");
  }

  public void cleanupTaskData(final String taskId) throws ApiException {
    coreApi.deleteNamespacedSecret(getSecretName(taskId), HARNESS_DELEGATE_NG, null, null, 30, true, null, null);
    coreApi.deleteNamespacedConfigMap(getConfigName(taskId), HARNESS_DELEGATE_NG, null, null, 30, true, null, null);
    log.info("task data cleaned up for {}", taskId);
  }

  private void createTaskJob(final String taskId, final V1Volume configMapVolume, final V1Volume secretVolume)
      throws ApiException {
    final var job = Yaml.loadAs(jobYaml, K8SJob.class)
                        .name(taskId)
                        .namespace(HARNESS_DELEGATE_NG)
                        .addVolume(configMapVolume, "/etc/config")
                        .addVolume(secretVolume, "/etc/secret");
    log.info("Job: {}", Yaml.dump(job));

    batchApi.createNamespacedJob(HARNESS_DELEGATE_NG, job, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }

  private V1Secret createTaskSecrets(final String taskId, final Set<String> secrets) throws ApiException {
    final var secret = new K8SSecret(getSecretName(taskId), HARNESS_DELEGATE_NG).putStringDataItem("some-key", "and-value");
    log.info("YM {}", Yaml.dump(secret));
    coreApi.createNamespacedSecret(HARNESS_DELEGATE_NG, secret, null, null, DELEGATE_FIELD_MANAGER, "Warn");
    return secret;
  }

  private V1ConfigMap createTaskConfig(final String taskId, final DelegateTaskPackage taskData)
          throws IOException, ApiException {
    final var configYaml = new YamlUtils().dump(taskData);
    final var configMap =
        new K8SConfigMap(getConfigName(taskId), HARNESS_DELEGATE_NG).putDataItem("config.yaml", configYaml);

    log.info("YM {}", Yaml.dump(configMap));
    log.info("CY: {}", configYaml);
    final var delegateTaskPackage = new YamlUtils().read(configYaml, DelegateTaskPackage.class);
    log.info("TP: {}", delegateTaskPackage);
    coreApi.createNamespacedConfigMap(
        HARNESS_DELEGATE_NG, configMap, null, null, DELEGATE_FIELD_MANAGER, "Warn");
    return configMap;
  }

  @NonNull
  private String getSecretName(final String taskId) {
    return taskId + "-secret";
  }

  @NonNull
  private String getConfigName(final String taskId) {
    return taskId + "-config";
  }

  private static final String jobYaml = "apiVersion: batch/v1\n"
      + "kind: Job\n"
      + "metadata:\n"
      + "spec:\n"
      + "  backoffLimit: 2\n"
      + "  activeDeadlineSeconds: 1000\n"
      + "  ttlSecondsAfterFinished: 2000\n"
      + "  template:\n"
      + "    spec:\n"
      + "      restartPolicy: Never\n"
      + "      containers:\n"
      + "        - image: alpine\n"
      + "          name: first-task\n"
      + "          imagePullPolicy: Always\n"
      + "          resources:\n"
      + "            limits:\n"
      + "              cpu: \"0.5\"\n"
      + "              memory: \"512Mi\"\n"
      + "            requests:\n"
      + "              cpu: \"0.5\"\n"
      + "              memory: \"512Mi\"\n"
      + "          command: [\"/bin/sh\"]\n"
      + "          args: [\"-c\", \"env; while true; do echo hello sleep 10; echo slept; done\"]";
}
