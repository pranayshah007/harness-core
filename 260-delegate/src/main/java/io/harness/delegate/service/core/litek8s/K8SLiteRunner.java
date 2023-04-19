/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.connector.SecretSpecBuilder.DOCKER_REGISTRY_SECRET_TYPE;
import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import io.harness.delegate.core.beans.TaskDescriptor;
import io.harness.delegate.core.beans.TaskSecret;
import io.harness.delegate.service.core.runner.TaskRunner;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.util.Yaml;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8SLiteRunner implements TaskRunner {
  private static final String DOCKER_CONFIG_KEY = ".dockercfg";

  private final CoreV1Api coreApi;
  private final PodBuilder podBuilder;
  private final RunnerDecryptionService decryptionService;

  //  private final K8EventHandler k8EventHandler;

  @Inject
  public K8SLiteRunner(final ApiClient apiClient, final PodBuilder podBuilder,
      /*, final K8EventHandler k8EventHandler*/ final RunnerDecryptionService decryptionService) {
    this.coreApi = new CoreV1Api(apiClient);
    this.podBuilder = podBuilder;
    //    this.k8EventHandler = k8EventHandler;
    this.decryptionService = decryptionService;
  }

  @Override
  public void executeTask(final String taskGroupId, final List<TaskDescriptor> tasks) {
    log.info("Setting up pod spec");

    try {
      // Step 1 - decrypt image pull secrets and create secret resources.
      // pullSecrets need to be decrypted by component which is configured during startup (e.g. runner or core),
      // otherwise we will have chicken & egg problem. E.g. delegate creates pod/container to decrypt secret, but image
      // for it needs secret itself.
      // I think other task secrets are known in advance for entire stage for both CI & CD (I think no real secret
      // expressions or dynamic secrets), this means we can do them during init here or execute step later
      //
      final var imageSecrets =
          tasks.stream()
              .filter(task -> task.hasRuntime() && task.getRuntime().hasInfraSecret())
              .flatMap(task -> createImagePullSecrets(taskGroupId, task.getId(), task.getRuntime().getInfraSecret()))
              .collect(Collectors.toList());
      // Step 1a - Should we decrypt other step secrets here and create resources?

      // Step 1b - Support certs (i.e. secret files that get mounted as secret volume).
      // Right now these are copied from delegate using special syntax and env vars (complicated)

      // Step 2 - create any other resources like volumes, config maps etc...

      // Step 3 - create pod - we don't need to busy wait - maybe LE should send task response as first thing when
      // created?
      final V1Pod pod = podBuilder
                            .createSpec(taskGroupId, tasks)
                            //              .withImagePullSecrets()
                            //              .withVolumes(extraVolumes)
                            .build();
      // TODO: Add
      log.info("Creating Task Pod with YAML:\n{}", Yaml.dump(pod));
      coreApi.createNamespacedPod("harness-delegate-ng", pod, null, null, DELEGATE_FIELD_MANAGER, "Warn");

      // Step 3 - Watch pod logs - normally stop when init finished, but if LE sends response then that's not possible
      // (e.g. delegate replicaset), but we can stop on watch status
      //    Watch<CoreV1Event> watch =
      //            k8EventHandler.startAsyncPodEventWatch(kubernetesConfig, namespace, podName,
      //            logStreamingTaskClient);

      // Step 4 - send response to SaaS
    } catch (ApiException e) {
      log.error("APIException: {}, {}, {}, {}, {}", e.getCode(), e.getResponseBody(), e.getMessage(),
          e.getResponseHeaders(), e.getCause());
      log.error("Failed to create the task {}", taskGroupId, e);
    }
  }

  private Stream<V1Secret> createImagePullSecrets(
      final String taskGroupId, final String taskId, final TaskSecret infraSecret) {
    // TODO: Deal with multiple secrets so they don't overwrite each other, though we should never have more than one
    final String secretName = String.format("%s-image-%s", taskGroupId, taskId);
    final var decryptedSecrets = decryptionService.decrypt(infraSecret);
    return decryptedSecrets.values().stream().map(secret -> createImagePullSecret(secretName, secret));
  }

  /**
   * TODO: CI & CD image pull secrets seem to work vastly different.
   * For CD it seems most of the processing is in PMS and delegate just does decryption.
   * For CI most of the processing is on client side.
   * CD Style seems simpler for the client and might support more use cases. Investigate more and  do that.
   * Below is skeleton that wont work right now (data needs to be .configjson, not just secret). Bonus if we could do
   * .dockerconfigjson
   *
   * @see
   *     io.harness.pms.expressions.utils.ImagePullSecretUtils#getImagePullSecret(io.harness.cdng.artifact.outcome.ArtifactOutcome,
   *     io.harness.pms.contracts.ambiance.Ambiance)
   */
  private V1Secret createImagePullSecret(final String secretName, final char[] secret) {
    var registrySecret =
        new V1SecretBuilder()
            .withMetadata(new V1ObjectMetaBuilder()
                              .withNamespace(K8SResourceHelper.getRunnerNamespace())
                              .withName(secretName)
                              .build())
            .withData(ImmutableMap.of(DOCKER_CONFIG_KEY, String.valueOf(secret).getBytes(Charsets.UTF_8)))
            .withType(DOCKER_REGISTRY_SECRET_TYPE)
            .build();
    return registrySecret;
    //    return coreApi.createSecrcreateNamespacedSecret(K8SResourceHelper.getRunnerNamespace(), registrySecret, null,
    //    null, DELEGATE_FIELD_MANAGER, "Warn");
  }
}
