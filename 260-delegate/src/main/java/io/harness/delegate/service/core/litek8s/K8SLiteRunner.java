/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.delegate.core.beans.ExecutionEnvironment;
import io.harness.delegate.core.beans.ExecutionInfrastructure;
import io.harness.delegate.core.beans.K8S;
import io.harness.delegate.core.beans.TaskDescriptor;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.runner.TaskRunner;
import io.harness.delegate.service.core.util.AnyUtils;
import io.harness.delegate.service.core.util.ApiExceptionLogger;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.common.collect.Streams;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Yaml;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class K8SLiteRunner implements TaskRunner {
  private static final int CONTAINER_START_PORT = 20002;

  private final CoreV1Api coreApi;
  private final ContainerBuilder containerBuilder;
  private final SecretsBuilder secretsBuilder;
  //  private final K8EventHandler k8EventHandler;

  @Override
  public void init(final String taskGroupId, final List<TaskDescriptor> tasks, final ExecutionInfrastructure infra) {
    log.info("Setting up pod spec");

    try {
      // Step 0 - unpack infra definition. Each runner knows the infra spec it expects
      final var k8sInfra = AnyUtils.unpack(infra.getProtoSpec(), K8S.class);

      // Step 1 - decrypt image pull secrets and create secret resources.
      // pullSecrets need to be decrypted by component which is configured during startup (e.g. runner or core),
      // otherwise we will have chicken & egg problem. E.g. delegate creates pod/container to decrypt secret, but image
      // for it needs secret itself.
      // I think other task secrets are known in advance for entire stage for both CI & CD (I think no real secret
      // expressions or dynamic secrets), this means we can do them during init here or execute step later
      final var imageSecrets =
          Streams
              .mapWithIndex(k8sInfra.getInfraSecretsList().stream(),
                  (secret, index) -> secretsBuilder.createImagePullSecrets(taskGroupId, secret, index))
              .collect(toList());

      // Step 1a - Should we decrypt other step secrets here and create resources?
      final var taskSecrets =
          tasks.stream().collect(groupingBy(TaskDescriptor::getId, flatMapping(this::createTaskSecrets, toList())));

      // Step 1b - TODO: Support certs (i.e. secret files that get mounted as secret volume).
      // Right now these are copied from delegate using special syntax and env vars (complicated)

      // Step 2 - create any other resources like volumes, config maps etc...
      final var protoVolumes = VolumeBuilder.unpackVolumes(k8sInfra.getResourcesList());
      final var volumes = VolumeBuilder.createVolumes(protoVolumes);
      final var volumeMounts = VolumeBuilder.createVolumeMounts(protoVolumes);

      // Step 3 - create pod - we don't need to busy wait - maybe LE should send task response as first thing when
      // created?
      final var portMap = new PortMap(CONTAINER_START_PORT);
      final V1Pod pod = PodBuilder.createSpec(taskGroupId)
                            .withImagePullSecrets(imageSecrets)
                            .withTasks(createContainers(tasks, taskSecrets, volumeMounts, portMap))
                            .buildPod(containerBuilder, k8sInfra.getResource(), volumes, portMap);

      log.info("Creating Task Pod with YAML:\n{}", Yaml.dump(pod));
      coreApi.createNamespacedPod(
          K8SResourceHelper.getRunnerNamespace(), pod, null, null, DELEGATE_FIELD_MANAGER, "Warn");

      // Step 3 - Watch pod logs - normally stop when init finished, but if LE sends response then that's not possible
      // (e.g. delegate replicaset), but we can stop on watch status
      //    Watch<CoreV1Event> watch =
      //            k8EventHandler.startAsyncPodEventWatch(kubernetesConfig, namespace, podName,
      //            logStreamingTaskClient);

      // Step 4 - send response to SaaS
    } catch (ApiException e) {
      log.error(ApiExceptionLogger.format(e));
      log.error("Failed to create the task {}", taskGroupId, e);
    } catch (Exception e) {
      log.error("Failed to create the task {}", taskGroupId, e);
      throw e;
    }
  }

  @Override
  public void execute(final String taskGroupId, final List<TaskDescriptor> tasks) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void cleanup(final String taskGroupId) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NonNull
  private Stream<V1Secret> createTaskSecrets(final TaskDescriptor task) {
    return task.getInputSecretsList().stream().map(secret -> secretsBuilder.createSecret(task.getId(), secret));
  }

  private List<V1Container> createContainers(final List<TaskDescriptor> taskDescriptors,
      final Map<String, List<V1Secret>> taskSecrets, final List<V1VolumeMount> volumeMounts, final PortMap portMap) {
    return taskDescriptors.stream()
        .map(descriptor
            -> createContainer(descriptor.getId(), descriptor.getRuntime(), taskSecrets.get(descriptor.getId()),
                volumeMounts, portMap.getPort(descriptor.getId())))
        .collect(Collectors.toList());
  }

  private V1Container createContainer(final String taskId, final ExecutionEnvironment runtime,
      final List<V1Secret> secrets, final List<V1VolumeMount> volumeMounts, final int port) {
    return containerBuilder.createContainer(taskId, runtime, port)
        .addAllToVolumeMounts(volumeMounts)
        .addAllToEnvFrom(createSecretRef(secrets))
        .build();
  }

  @NonNull
  private List<V1EnvFromSource> createSecretRef(final List<V1Secret> secrets) {
    return secrets.stream().map(K8SEnvVar::fromSecret).collect(toList());
  }
}
