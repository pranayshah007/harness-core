/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

import static io.harness.delegate.beans.ci.k8s.PodStatus.Status.RUNNING;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.getDockerSecret;
import static io.harness.delegate.task.citasks.cik8handler.params.CIConstants.POD_RUNNING_PHASE;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ImageSecretBuilder;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.exception.PodNotFoundException;
import io.harness.k8s.model.ImageDetails;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;
import io.harness.threading.Sleeper;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateBuilder;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStateWaitingBuilder;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ContainerStatusBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1StatusBuilder;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIK8JavaClientHandlerTest extends CategoryTest {
  @Mock private SecretSpecBuilder mockSecretSpecBuilder;

  @Mock private CoreV1Api coreV1Api;
  @Mock private GenericKubernetesApi<V1Pod, V1PodList> podClient;
  @Mock private DeleteOptions deleteOptions;
  @Mock private Sleeper sleeper;
  @Mock private ImageSecretBuilder imageSecretBuilder;
  @InjectMocks private io.harness.delegate.task.citasks.cik8handler.k8java.CIK8JavaClientHandler cik8JavaClientHandler;

  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                                  .masterUrl("https://1.1.1.1/")
                                                  .username("admin".toCharArray())
                                                  .password("password".toCharArray())
                                                  .namespace("default")
                                                  .build();

  private static final String podName = "pod";
  private static final String secretName = "secretName";
  private static final String imageName = "imageName";
  private static final String tag = "tag";
  private static final String namespace = "default";
  private static final int podWaitTimeoutSecs = 100;

  private V1Pod getPodWithSate(String runningState) {
    V1PodStatusBuilder podStatus = new V1PodStatusBuilder();
    podStatus.withPhase(runningState);
    podStatus.withHostIP("123.12.11.11");

    podStatus.withContainerStatuses(Arrays.asList(getPendingContainerStatus()));
    return new V1PodBuilder().withStatus(podStatus.build()).build();
  }

  private V1ContainerStatus getPendingContainerStatus() {
    V1ContainerStatusBuilder containerStatus = new V1ContainerStatusBuilder();
    V1ContainerStateWaiting containerStateWaiting = new V1ContainerStateWaitingBuilder().withMessage("pending").build();
    V1ContainerState containerState = new V1ContainerStateBuilder().withWaiting(containerStateWaiting).build();
    containerStatus.withState(containerState);
    return containerStatus.build();
  }

  private V1Pod getPodWithSuccessSate(String runningState) {
    V1PodStatusBuilder podStatus = new V1PodStatusBuilder();
    podStatus.withPhase(runningState);
    podStatus.withHostIP("123.12.11.11");
    podStatus.withPodIP("123.12.11.11");

    podStatus.withContainerStatuses(Arrays.asList(getSuccessContainerStatus()));
    return new V1PodBuilder().withStatus(podStatus.build()).build();
  }

  private V1ContainerStatus getSuccessContainerStatus() {
    V1ContainerStatusBuilder containerStatus = new V1ContainerStatusBuilder();
    V1ContainerState containerState = new V1ContainerStateBuilder().build();
    containerStatus.withState(containerState);
    return containerStatus.build();
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createRegistrySecretWithRegistry() throws ApiException, IOException {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).build();

    when(imageSecretBuilder.getJSONEncodedImageCredentials(any())).thenReturn("fdfd");

    cik8JavaClientHandler.createRegistrySecret(coreV1Api, namespace, secretName, imageDetailsWithConnector);
    verify(coreV1Api, times(1)).createNamespacedSecret(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createPod() throws ApiException {
    V1Pod pod = new V1PodBuilder()
                    .withApiVersion("v1")
                    .withNewStatus()
                    .withPhase("Running")
                    .endStatus()
                    .withNewMetadata()
                    .addToLabels("app", "MyApp")
                    .endMetadata()
                    .build();

    when(coreV1Api.createNamespacedPod(any(), any(), any(), any(), any(), any())).thenReturn(pod);

    assertEquals(pod, cik8JavaClientHandler.createOrReplacePod(coreV1Api, pod, namespace));
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void deletePodWithSuccess() throws ApiException {
    KubernetesApiResponse kubernetesApiResponse =
        new KubernetesApiResponse(new V1StatusBuilder().withStatus("Success").build(), -1);

    when(podClient.delete(anyString(), anyString(), any(DeleteOptions.class))).thenReturn(kubernetesApiResponse);
    assertEquals(cik8JavaClientHandler.deletePod(podClient, podName, namespace).getStatus(), "Success");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void deletePodWithPodNotFound() throws ApiException {
    KubernetesApiResponse kubernetesApiResponse =
        new KubernetesApiResponse(new V1StatusBuilder().withStatus("Failure").build(), 404);

    when(podClient.delete(anyString(), anyString(), any(DeleteOptions.class))).thenReturn(kubernetesApiResponse);
    assertThatThrownBy(() -> cik8JavaClientHandler.deletePod(podClient, podName, namespace).getStatus(), "Failure")
        .isInstanceOf(PodNotFoundException.class);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void deletePodWithFailure() throws ApiException {
    KubernetesApiResponse kubernetesApiResponse =
        new KubernetesApiResponse(new V1StatusBuilder().withStatus("Failure").build(), 401);

    when(podClient.delete(anyString(), anyString(), any(DeleteOptions.class))).thenReturn(kubernetesApiResponse);
    assertThatThrownBy(() -> cik8JavaClientHandler.deletePod(podClient, podName, namespace).getStatus())
        .isInstanceOf(RuntimeException.class);
  }

  @Test(expected = PodNotFoundException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void waitUntilPodIsReadyWithPodNotPresent() throws TimeoutException, InterruptedException, ApiException {
    when(coreV1Api.readNamespacedPod(any(), any(), any())).thenThrow(new PodNotFoundException("not found"));
    cik8JavaClientHandler.waitUntilPodIsReady(coreV1Api, podName, namespace, podWaitTimeoutSecs);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void waitUntilPodIsReadyWithSuccess() throws TimeoutException, InterruptedException, ApiException {
    V1Pod pod = getPodWithSuccessSate(POD_RUNNING_PHASE);

    when(coreV1Api.readNamespacedPod(any(), any(), any())).thenReturn(pod);
    assertEquals(RUNNING,
        cik8JavaClientHandler.waitUntilPodIsReady(coreV1Api, podName, namespace, podWaitTimeoutSecs).getStatus());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void waitUntilPodIsReadyWithSuccessAfterOneRetry() throws InterruptedException, ApiException {
    V1Pod pod1 = getPodWithSuccessSate(POD_RUNNING_PHASE);

    when(coreV1Api.readNamespacedPod(any(), any(), any())).thenReturn(pod1);

    assertEquals(RUNNING,
        cik8JavaClientHandler.waitUntilPodIsReady(coreV1Api, podName, namespace, podWaitTimeoutSecs).getStatus());
  }

  @Test()
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreateSecret() throws ApiException {
    Map<String, SecretParams> dockerSecret = getDockerSecret();
    Map<String, String> decryptedSecret =
        dockerSecret.values().stream().collect(Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));

    Map<String, byte[]> data = new HashMap<>();
    for (Map.Entry<String, String> entry : decryptedSecret.entrySet()) {
      data.put(entry.getKey(), entry.getValue().getBytes());
    }
    V1Secret secret =
        new V1SecretBuilder()
            .withMetadata(new V1ObjectMetaBuilder().withNamespace(namespace).withName("secret-name").build())
            .withType("opaque")
            .withData(data)
            .build();

    when(coreV1Api.createNamespacedSecret(any(), any(), any(), any(), any(), any())).thenReturn(secret);
    V1Secret secret1 = cik8JavaClientHandler.createOrReplaceSecret(coreV1Api, secret, namespace);
    assertThat(secret1).isEqualTo(secret);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createService() throws Exception {
    String serviceName = "svc";
    List<Integer> ports = new ArrayList<>();
    ports.add(8000);
    Map<String, String> selector = new HashMap<>();
    selector.put("foo", "bar");
    cik8JavaClientHandler.createService(coreV1Api, namespace, serviceName, selector, ports);
    verify(coreV1Api).createNamespacedService(any(), any(), any(), any(), any(), any());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void parseApiExceptionMessageSuccess() {
    String body =
        "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"namespaces \\\"test\\\" not found\",\"reason\":\"NotFound\",\"details\":{\"name\":\"test\",\"kind\":\"namespaces\"},\"code\":404}";
    String defaultMsg = "Invalid test";
    String expectedMsg = "namespaces \"test\" not found";

    String response = cik8JavaClientHandler.parseApiExceptionMessage(body, defaultMsg);
    assertThat(response).isEqualTo(expectedMsg);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void parseApiExceptionMessageDefaultMsg() {
    String body = "Unauthorised";
    String defaultMsg = "Invalid test";

    String response = cik8JavaClientHandler.parseApiExceptionMessage(body, defaultMsg);
    assertThat(response).isEqualTo(defaultMsg);
  }
}
