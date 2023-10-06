/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.service.core.litek8s.ContainerFactory.RESERVED_ADDON_PORT;
import static io.harness.delegate.service.core.litek8s.ContainerFactory.RESERVED_LE_PORT;
import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import static java.lang.String.format;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.k8s.K8SSecret;
import io.harness.delegate.service.core.k8s.K8SService;
import io.harness.delegate.service.core.util.ApiExceptionLogger;
import io.harness.delegate.service.core.util.K8SResourceHelper;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.runners.itfc.Runner;
import io.harness.delegate.service.secret.DecryptedSecrets;
import io.harness.encryption.Scope;
import io.harness.logging.CommandExecutionStatus;
import io.harness.product.ci.engine.proto.ExecuteStep;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.LiteEngineGrpc;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.TokenUtils;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Yaml;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class K8SLiteRunner implements Runner {
  private static final String LOG_SERVICE_TOKEN_VARIABLE = "HARNESS_LOG_SERVICE_TOKEN";
  private static final String LOG_SERVICE_ENDPOINT_VARIABLE = "HARNESS_LOG_SERVICE_ENDPOINT";
  private static final String HARNESS_LOG_PREFIX_VARIABLE = "HARNESS_LOG_PREFIX";
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  private final DelegateConfiguration delegateConfiguration;

  private final CoreV1Api coreApi;
  private final ContainerFactory containerFactory;
  private final SecretsBuilder secretsBuilder;
  private final K8SRunnerConfig config;
  private final InfraCleaner infraCleaner;
  //  private final K8EventHandler k8EventHandler;

  @Override
  public void init(final String infraId, final InputData infra, DecryptedSecrets decrypted, final Context context) {
    log.info("Setting up pod spec");

    try {
      // Step 0 - unpack infra definition. Each runner knows the infra spec it expects
      final var k8sInfra = K8SInfra.parseFrom(infra.getBinaryData());

      // TODO: @Anupam, add image pull secrets support
      // Step 1 - decrypt image pull secrets and create secret resources.
      // pullSecrets need to be decrypted by component which is configured during startup (e.g. runner or core),
      // otherwise we will have chicken & egg problem. E.g. delegate creates pod/container to decrypt secret, but image
      // for it needs secret itself.
      // I think other task secrets are known in advance for entire stage for both CI & CD (I think no real secret
      // expressions or dynamic secrets), this means we can do them during init here or execute step later
      // final var imageSecrets =
      //     Streams
      //         .mapWithIndex(k8sInfra.getInfraSecretsList().stream(),
      //             (secret, index) -> secretsBuilder.createImagePullSecrets(infraId, secret, index))
      //         .collect(toList());

      // Step 1a - Should we decrypt other step secrets here and create resources?
      var k8Secrets = flatMapping(task -> createTaskSecrets(infraId, k8sInfra.getStepsList().get(0), decrypted, context), toList());
      final var taskSecrets = k8sInfra.getStepsList().stream().collect(groupingBy(
          K8SStep::getId, flatMapping(task -> createTaskSecrets(infraId, task, decrypted, context), toList())));

      final var loggingToken = k8sInfra.getLogToken();
      final V1Secret loggingSecret =
          createLoggingSecret(infraId, config.getLogServiceUrl(), loggingToken, k8sInfra.getLogPrefix());

      // Step 1c - TODO: Support certs (i.e. secret files that get mounted as secret volume).
      // Right now these are copied from delegate using special syntax and env vars (complicated)

      // Step 2 - create any other resources like volumes, config maps etc...
      final var protoVolumes = VolumeBuilder.unpackVolumes(k8sInfra.getResourcesList());
      final var volumes = VolumeBuilder.createVolumes(protoVolumes);
      final var volumeMounts = VolumeBuilder.createVolumeMounts(protoVolumes);

      // Step 3 - create service endpoint for LE communication
      final var namespace = config.getNamespace();
      V1Service v1Service =
          K8SService.clusterIp(infraId, namespace, K8SResourceHelper.getPodName(infraId), RESERVED_LE_PORT)
              .create(coreApi);

      // Step 4 - create pod - we don't need to busy wait - maybe LE should send task response as first thing when
      // created?
      final var portMap = new PortMap(RESERVED_ADDON_PORT);
      final V1Pod pod = PodBuilder.createSpec(containerFactory, config, infraId)
                            .withTasks(createContainers(k8sInfra, taskSecrets, loggingSecret, volumeMounts, portMap))
                            .buildPod(k8sInfra, volumes, loggingSecret, portMap);

      log.info("Creating Task Pod with YAML:\n{}", Yaml.dump(pod));
      coreApi.createNamespacedPod(namespace, pod, null, null, DELEGATE_FIELD_MANAGER, "Warn");

      log.info("Done creating the task pod for {}!!", infraId);
      // Step 5 - Watch pod logs - normally stop when init finished, but if LE sends response then that's not possible
      // (e.g. delegate replicaset), but we can stop on watch status
      //    Watch<CoreV1Event> watch =
      //            k8EventHandler.startAsyncPodEventWatch(kubernetesConfig, namespace, podName,
      //            logStreamingTaskClient);

      // Step 6 - send response to SaaS
    } catch (ApiException e) {
      log.error("Failed to create the task {}. {}", infraId, ApiExceptionLogger.format(e), e);
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse protobuf data {}", e);
    } catch (Exception e) {
      log.error("Failed to create the task {}", infraId, e);
      throw e;
    }
  }

  @Override
  public void execute(
      final String taskGroupId, final InputData tasks, DecryptedSecrets decrypted, final Context context) {
    ExecuteStep executeStep = ExecuteStep.newBuilder()
                                  .setTaskParameters(tasks.getBinaryData())
                                  .addAllExecuteCommand(List.of("./start.sh"))
                                  .build();

    UnitStep unitStep = UnitStep.newBuilder()
                            .setId(taskGroupId)
                            .setExecuteTask(executeStep)
                            .setCallbackToken(config.getDelegateToken())
                            .setTaskId(context.get(Context.TASK_ID))
                            .setAccountId(config.getAccountId())
                            .setContainerPort(RESERVED_ADDON_PORT)
                            .build();

    ExecuteStepRequest executeStepRequest = ExecuteStepRequest.newBuilder().setStep(unitStep).build();

    String accountKey = delegateConfiguration.getDelegateToken();
    String managerUrl = delegateConfiguration.getManagerUrl();
    String delegateID = context.get(Context.DELEGATE_ID);
    if (isNotEmpty(managerUrl)) {
      managerUrl = managerUrl.replace("/api/", "");
      executeStepRequest = executeStepRequest.toBuilder().setManagerSvcEndpoint(managerUrl).build();
    }
    if (isNotEmpty(accountKey)) {
      executeStepRequest =
          executeStepRequest.toBuilder().setAccountKey(TokenUtils.getDecodedTokenString(accountKey)).build();
    }
    if (isNotEmpty(delegateID)) {
      executeStepRequest = executeStepRequest.toBuilder().setDelegateId(delegateID).build();
    }

    final var namespace = config.getNamespace();

    String target = K8SService.buildK8sServiceUrl(taskGroupId, namespace, Integer.toString(RESERVED_LE_PORT));
    // String target = format("%s:%d", "127.0.0.1", RESERVED_LE_PORT);
    ManagedChannelBuilder managedChannelBuilder = ManagedChannelBuilder.forTarget(target).usePlaintext();
    ManagedChannel channel = managedChannelBuilder.build();

    final ExecuteStepRequest finalExecuteStepRequest = executeStepRequest;
    try {
      try {
        RetryPolicy<Object> retryPolicy =
            getRetryPolicy(format("[Retrying failed call to send execution call to pod %s: {}", target),
                format("Failed to send execution to pod %s after retrying {} times", RESERVED_LE_PORT));

        Failsafe.with(retryPolicy).get(() -> {
          LiteEngineGrpc.LiteEngineBlockingStub liteEngineBlockingStub = LiteEngineGrpc.newBlockingStub(channel);
          liteEngineBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS).executeStep(finalExecuteStepRequest);
          return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
        });

      } finally {
        // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
        // resources the channel should be shut down when it will no longer be used. If it may be used
        // again leave it running.
        channel.shutdownNow();
      }
    } catch (Exception e) {
      log.error("Failed to execute step on lite engine target {} with err: {}", target, e);
    }
  }

  @Override
  public void cleanup(final String infraId, final Context context) {
    try {
      infraCleaner.deletePod(infraId, config.getNamespace());
      infraCleaner.deleteSecrets(infraId, config.getNamespace());
      infraCleaner.deleteServiceEndpoint(infraId, config.getNamespace());
    } catch (ApiException e) {
      log.error("Failed to cleanup the task {}. {}", infraId, ApiExceptionLogger.format(e), e);
    } catch (Exception e) {
      log.error("Failed to cleanup the task {}", infraId, e);
      throw e;
    }
  }

  private V1Secret createLoggingSecret(final String infraId, final String logServiceUri, final String loggingToken,
      final String loggingPrefix) throws ApiException {
    final var secretName = K8SResourceHelper.getSecretName(infraId + "-logging");
    final var namespace = config.getNamespace();
    return K8SSecret.secret(secretName, namespace, infraId)
        .putStringDataItem(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceUri)
        .putStringDataItem(LOG_SERVICE_TOKEN_VARIABLE, loggingToken)
        .putStringDataItem(HARNESS_LOG_PREFIX_VARIABLE, loggingPrefix)
        .create(coreApi);
  }

  @NonNull
  private Stream<V1Secret> createTaskSecrets(
      final String infraId, final K8SStep task, final DecryptedSecrets decrypted, final Context context) {
    return task.getInputSecretsList().stream().map(secret -> {
      IdentifierRef ref =
          IdentifierRefHelper.getIdentifierRef(Scope.fromString(secret.getScope().name()), secret.getSecretId(),
              context.get(Context.ACCOUNT_ID), context.get(Context.ORG_ID), context.get(Context.PROJECT_ID), null);
      return secretsBuilder.createSecret(infraId, task.getId(), ref, decrypted.getDecryptedValue(ref));
    });
  }

  private List<V1Container> createContainers(final K8SInfra k8SInfra, final Map<String, List<V1Secret>> taskSecrets,
      final V1Secret loggingSecret, final List<V1VolumeMount> volumeMounts, final PortMap portMap) {
    return k8SInfra.getStepsList()
        .stream()
        .map(descriptor
            -> createContainer(descriptor, taskSecrets.get(descriptor.getId()), loggingSecret, volumeMounts,
                portMap.getPort(descriptor.getId())))
        .collect(Collectors.toList());
  }

  private V1Container createContainer(final K8SStep k8SStep, final List<V1Secret> secrets, final V1Secret loggingSecret,
      final List<V1VolumeMount> volumeMounts, final int port) {
    secrets.add(loggingSecret);
    return containerFactory.createContainer(k8SStep.getId(), k8SStep.getRuntime(), port)
        .addAllToVolumeMounts(volumeMounts)
        .addAllToEnvFrom(createSecretRefs(secrets))
        .build();
  }

  @NonNull
  private static List<V1EnvFromSource> createSecretRefs(final List<V1Secret> secrets) {
    return secrets.stream().map(K8SEnvVar::fromSecret).collect(toList());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
