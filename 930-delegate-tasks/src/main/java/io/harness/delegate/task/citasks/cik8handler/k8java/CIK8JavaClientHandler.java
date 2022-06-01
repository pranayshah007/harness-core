/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.k8java;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder.DOCKER_REGISTRY_SECRET_TYPE;
import static io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder.OPAQUE_SECRET_TYPE;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.task.citasks.cik8handler.ImageSecretBuilder;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.PodNotFoundException;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.threading.Sleeper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIK8JavaClientHandler {
  @Inject private ImageSecretBuilder imageSecretBuilder;

  private static final String DOCKER_CONFIG_KEY = ".dockercfg";
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  @Inject private SecretSpecBuilder secretSpecBuilder;
  @Inject private Sleeper sleeper;
  @Inject private ApiClientFactory apiClientFactory;

  private final int DELETION_MAX_ATTEMPTS = 15;

  public V1Pod createOrReplacePodWithRetries(CoreV1Api coreV1Api, V1Pod pod, String namespace) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying failed to create pod; attempt: {}", "Failing pod creation after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> createOrReplacePod(coreV1Api, pod, namespace));
  }

  public V1Secret createRegistrySecret(
      CoreV1Api coreV1Api, String namespace, String secretName, ImageDetailsWithConnector imageDetails) {
    String credentialData = imageSecretBuilder.getJSONEncodedImageCredentials(imageDetails);
    if (credentialData == null) {
      return null;
    }

    V1Secret secret = new V1SecretBuilder()
                          .withMetadata(new V1ObjectMetaBuilder().withNamespace(namespace).withName(secretName).build())
                          .withData(ImmutableMap.of(DOCKER_CONFIG_KEY, credentialData.getBytes(Charsets.UTF_8)))
                          .withType(DOCKER_REGISTRY_SECRET_TYPE)
                          .build();

    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying failed to create registry secret; attempt: {}",
        "Failing registry secret creation after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> createOrReplaceSecret(coreV1Api, secret, namespace));
  }

  public V1Secret createEnvSecret(CoreV1Api coreV1Api, String namespace, String secretName, Map<String, byte[]> data) {
    V1Secret secret = new V1SecretBuilder()
                          .withMetadata(new V1ObjectMetaBuilder().withNamespace(namespace).withName(secretName).build())
                          .withData(data)
                          .withType(OPAQUE_SECRET_TYPE)
                          .build();

    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying failed to create registry secret; attempt: {}",
        "Failing registry secret creation after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> createOrReplaceSecret(coreV1Api, secret, namespace));
  }

  public V1Secret createOrReplaceSecret(CoreV1Api coreV1Api, V1Secret definition, String namespace) {
    String name = definition.getMetadata().getName();
    V1Secret secret = getSecret(coreV1Api, name, namespace);
    return secret == null ? createSecret(coreV1Api, definition, namespace)
                          : replaceSecret(coreV1Api, definition, namespace);
  }

  public V1Secret getSecret(CoreV1Api coreV1Api, String secretName, String namespace) {
    if (isBlank(secretName)) {
      return null;
    }

    try {
      return coreV1Api.readNamespacedSecret(secretName, namespace, null, null, null);
    } catch (ApiException exception) {
      if (isResourceNotFoundException(exception.getCode())) {
        return null;
      }
      String defaultMessage = format("Failed to get %s/%s. Code: %s, Message: %s \n Cause: %s", namespace, secretName,
          exception.getCode(), exception.getResponseBody(), exception.getMessage());
      log.error(defaultMessage);
      String message = parseApiExceptionMessage(exception.getResponseBody(), defaultMessage);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  private boolean isResourceNotFoundException(int code) {
    return code == 404;
  }

  @VisibleForTesting
  V1Secret createSecret(CoreV1Api coreV1Api, V1Secret secret, String namespace) {
    log.info("Creating secret [{}]", secret.getMetadata().getName());

    try {
      return coreV1Api.createNamespacedSecret(namespace, secret, null, null, null);
    } catch (ApiException exception) {
      String secretDef = secret.getMetadata() != null && isNotEmpty(secret.getMetadata().getName())
          ? format("%s/%s", namespace, secret.getMetadata().getName())
          : "Secret";
      String defaultMessage = format("Failed to create secret %s. Code: %s, message: %s", secretDef,
          exception.getCode(), exception.getResponseBody());
      log.error(defaultMessage);
      String message = parseApiExceptionMessage(exception.getResponseBody(), defaultMessage);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @VisibleForTesting
  V1Secret replaceSecret(CoreV1Api coreV1Api, V1Secret secret, String namespace) {
    String name = secret.getMetadata().getName();
    log.info("Replacing secret [{}]", name);

    try {
      return coreV1Api.replaceNamespacedSecret(name, namespace, secret, null, null, null);
    } catch (ApiException exception) {
      String secretDef = secret.getMetadata() != null && isNotEmpty(secret.getMetadata().getName())
          ? format("%s/%s", namespace, secret.getMetadata().getName())
          : "Secret";
      String defaultMessage = format("Failed to replace secret %s. Code: %s, message: %s", secretDef,
          exception.getCode(), exception.getResponseBody());
      log.error(defaultMessage);
      String message = parseApiExceptionMessage(exception.getResponseBody(), defaultMessage);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  public V1Pod createOrReplacePod(CoreV1Api coreV1Api, V1Pod pod, String namespace) throws ApiException {
    V1Pod podGet = null;
    try {
      podGet = getPod(coreV1Api, pod.getMetadata().getName(), namespace);
    } catch (ApiException ex) {
      if (ex.getCode() != 404) {
        log.info("CreateOrReplace Pod: Pod get failed with err: %s", ex);
      }
    }

    if (podGet != null) {
      try {
        GenericKubernetesApi<V1Pod, V1PodList> podClient =
            new GenericKubernetesApi(V1Pod.class, V1PodList.class, "", "v1", "pods", coreV1Api.getApiClient());
        deletePod(podClient, pod.getMetadata().getName(), namespace);
      } catch (Exception ex) {
        log.info("CreateOrReplace Pod: Pod delete failed with err: %s", ex);
      }
    }

    return createPod(coreV1Api, pod, namespace);
  }

  private V1Pod createPod(CoreV1Api coreV1Api, V1Pod pod, String namespace) throws ApiException {
    try {
      return coreV1Api.createNamespacedPod(namespace, pod, null, null, null);
    } catch (ApiException ex) {
      log.warn("Failed to created pod due to: {}", ex.getResponseBody());
      throw ex;
    }
  }

  public V1Status deletePodWithRetries(CoreV1Api coreV1Api, String podName, String namespace) throws ApiException {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicyForDeletion(format("[Retrying failed to delete pod: [%s]; attempt: {}", podName),
            format("Failed to delete pod after retrying {} times", podName));

    GenericKubernetesApi<V1Pod, V1PodList> podClient =
        new GenericKubernetesApi(V1Pod.class, V1PodList.class, "", "v1", "pods", coreV1Api.getApiClient());
    return Failsafe.with(retryPolicy).get(() -> deletePod(podClient, podName, namespace));
  }

  public V1Status deletePod(GenericKubernetesApi<V1Pod, V1PodList> podClient, String podName, String namespace) {
    V1Status v1Status = new V1Status();
    KubernetesApiResponse kubernetesApiResponse = podClient.delete(namespace, podName);
    if (kubernetesApiResponse.isSuccess()) {
      v1Status.setStatus("Success");
      return v1Status;
    }

    if (kubernetesApiResponse.getHttpStatusCode() == 404) {
      log.warn("Pod {} not found ", podName);
      throw new PodNotFoundException("Failed to delete pod " + podName);
    } else {
      throw new RuntimeException("Failed to delete pod " + podName);
    }
  }

  public V1Pod getPod(CoreV1Api coreV1Api, String podName, String namespace) throws ApiException {
    return coreV1Api.readNamespacedPod(podName, namespace, null, null, null);
  }

  public void createService(CoreV1Api coreV1Api, String namespace, String serviceName, Map<String, String> selectorMap,
      List<Integer> ports) throws ApiException {
    List<V1ServicePort> svcPorts = new ArrayList<>();
    for (int idx = 0; idx < ports.size(); idx++) {
      String name = String.format("%d-%d", ports.get(idx), idx);
      V1ServicePort servicePort = new V1ServicePortBuilder().withName(name).withPort(ports.get(idx)).build();
      svcPorts.add(servicePort);
    }

    V1Service svc = new V1ServiceBuilder()
                        .withNewMetadata()
                        .withName(serviceName)
                        .endMetadata()
                        .withNewSpec()
                        .withSelector(selectorMap)
                        .withPorts(svcPorts)
                        .endSpec()
                        .build();
    coreV1Api.createNamespacedService(namespace, svc, null, null, null);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  // Waits for the pod to exit PENDING state and returns true if pod is in RUNNING state, else false.
  public PodStatus waitUntilPodIsReady(
      CoreV1Api coreV1Api, String podName, String namespace, int podMaxWaitUntilReadySecs) throws InterruptedException {
    int errorCounter = 0;
    V1Pod pod = null;
    Instant startTime = Instant.now();
    Instant currTime = startTime;

    while (Duration.between(startTime, currTime).getSeconds() < podMaxWaitUntilReadySecs) {
      // Either pod is in pending phase where it is waiting for scheduling / creation of containers
      // or pod is waiting for containers to move to running state.
      if (pod != null && !isPodInPendingPhase(pod) && !isPodInWaitingState(pod) && isIpAssigned(pod)) {
        return PodStatus.builder()
            .status(PodStatus.Status.RUNNING)
            .ip(pod.getStatus().getPodIP())
            .ciContainerStatusList(getContainersStatus(pod))
            .build();
      }

      sleeper.sleep(CIConstants.POD_WAIT_UNTIL_READY_SLEEP_SECS * 1000L);
      try {
        pod = getPod(coreV1Api, podName, namespace);
      } catch (Exception ex) {
        errorCounter = errorCounter + 1;
        log.error("Pod get call failed, errorCounter: {}", errorCounter, ex);
        if (errorCounter >= 10) {
          throw new PodNotFoundException(format("Pod %s is not present in namespace %s", podName, namespace), ex);
        }
        continue;
      }
      currTime = Instant.now();
    }

    String errMsg;
    // If pod's container status list is non-empty, reason for pod not to be in running state is in waiting container's
    // status message. Else reason is present in pod conditions.
    if (isNotEmpty(pod.getStatus().getContainerStatuses())) {
      List<String> containerErrs =
          pod.getStatus()
              .getContainerStatuses()
              .stream()
              .filter(containerStatus -> containerStatus.getState().getWaiting() != null)
              .filter(containerStatus -> containerStatus.getState().getWaiting().getMessage() != null)
              .map(containerStatus -> containerStatus.getState().getWaiting().getMessage())
              .collect(Collectors.toList());
      errMsg = String.join(", ", containerErrs);
    } else {
      List<String> podConditions = pod.getStatus()
                                       .getConditions()
                                       .stream()
                                       .filter(Objects::nonNull)
                                       .map(V1PodCondition::getMessage)
                                       .collect(Collectors.toList());
      errMsg = String.join(", ", podConditions);
    }

    if (isEmpty(errMsg) && Duration.between(startTime, currTime).getSeconds() >= podMaxWaitUntilReadySecs) {
      errMsg = format("Timeout exception: Pod containers failed to reach running state within %s seconds",
          podMaxWaitUntilReadySecs);
    }
    return PodStatus.builder()
        .status(PodStatus.Status.ERROR)
        .errorMessage(errMsg)
        .ciContainerStatusList(getContainersStatus(pod))
        .ip(pod.getStatus().getPodIP())
        .build();
  }

  private boolean isPodInWaitingState(V1Pod pod) {
    for (V1ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
      if (containerStatus.getState().getWaiting() != null) {
        return true;
      }
    }
    return false;
  }

  private boolean isIpAssigned(V1Pod pod) {
    if (pod.getStatus().getPodIP() != null) {
      return true;
    }
    return false;
  }

  private boolean isPodInPendingPhase(V1Pod pod) {
    String podPhase = pod.getStatus().getPhase();
    return podPhase.equals(CIConstants.POD_PENDING_PHASE);
  }

  private List<CIContainerStatus> getContainersStatus(V1Pod pod) {
    List<CIContainerStatus> containerStatusList = new ArrayList<>();
    List<V1ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
    if (isNotEmpty(containerStatuses)) {
      for (V1ContainerStatus containerStatus : containerStatuses) {
        String name = containerStatus.getName();
        String image = containerStatus.getImage();
        if (containerStatus.getState().getRunning() != null) {
          containerStatusList.add(CIContainerStatus.builder()
                                      .name(name)
                                      .image(image)
                                      .status(CIContainerStatus.Status.SUCCESS)
                                      .startTime(containerStatus.getState().getRunning().getStartedAt().toString())
                                      .build());
        } else if (containerStatus.getState().getTerminated() != null) {
          V1ContainerStateTerminated containerStateTerminated = containerStatus.getState().getTerminated();
          containerStatusList.add(CIContainerStatus.builder()
                                      .name(name)
                                      .image(image)
                                      .status(CIContainerStatus.Status.ERROR)
                                      .startTime(containerStateTerminated.getStartedAt().toString())
                                      .endTime(containerStateTerminated.getFinishedAt().toString())
                                      .errorMsg(getContainerErrMsg(
                                          containerStateTerminated.getReason(), containerStateTerminated.getMessage()))
                                      .build());
        } else if (containerStatus.getState().getWaiting() != null) {
          V1ContainerStateWaiting containerStateWaiting = containerStatus.getState().getWaiting();
          containerStatusList.add(
              CIContainerStatus.builder()
                  .name(name)
                  .image(image)
                  .status(CIContainerStatus.Status.ERROR)
                  .errorMsg(getContainerErrMsg(containerStateWaiting.getReason(), containerStateWaiting.getMessage()))
                  .build());
        }
      }
    }
    return containerStatusList;
  }

  private String getContainerErrMsg(String reason, String message) {
    if (isEmpty(message)) {
      return reason;
    }

    if (isEmpty(reason)) {
      return message;
    } else {
      return format("%s: %s", reason, message);
    }
  }

  public Boolean deleteService(CoreV1Api coreV1Api, String namespace, String serviceName) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicyForDeletion(format("[Retrying failed to delete service: [%s]; attempt: {}", serviceName),
            format("Failed to delete service after retrying {} times", serviceName));

    return Failsafe.with(retryPolicy).get(() -> {
      try {
        return coreV1Api.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null)
            .getStatus()
            .equals("Success");
      } catch (ApiException ex) {
        ignoreResourceNotFound(ex, serviceName);
        return false;
      }
    });
  }

  public Boolean deleteSecret(CoreV1Api coreV1Api, String namespace, String secretName) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicyForDeletion(format("[Retrying failed to delete secret: [%s]; attempt: {}", secretName),
            format("Failed to delete secret after retrying {} times", secretName));

    return Failsafe.with(retryPolicy).get(() -> {
      try {
        return coreV1Api.deleteNamespacedSecret(secretName, namespace, null, null, null, null, null, null)
            .getStatus()
            .equals("Success");
      } catch (ApiException ex) {
        ignoreResourceNotFound(ex, secretName);
        return false;
      }
    });
  }

  private void ignoreResourceNotFound(ApiException ex, String resourceName) throws ApiException {
    if (ex.getCode() == 404) {
      log.warn("K8 resource {}  not found ", resourceName);
    } else {
      throw ex;
    }
  }

  private RetryPolicy<Object> getRetryPolicyForDeletion(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withMaxAttempts(DELETION_MAX_ATTEMPTS)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  // Example ApiException response body:
  // {"kind":"Status","apiVersion":"v1","metadata":{},"status":"Failure","message":"namespaces \"test\" not
  // found","reason":"NotFound","details":{"name":"test","kind":"namespaces"},"code":404}
  public String parseApiExceptionMessage(String responseBody, String defaultMessage) {
    try {
      JSONObject response = new JSONObject(responseBody);
      return response.getString("message");
    } catch (Exception e) {
      return defaultMessage;
    }
  }
}
