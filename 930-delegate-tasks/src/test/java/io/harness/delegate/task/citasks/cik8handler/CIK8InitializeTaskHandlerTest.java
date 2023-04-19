/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

import static io.harness.delegate.beans.ci.k8s.PodStatus.Status.ERROR;
import static io.harness.delegate.beans.ci.k8s.PodStatus.Status.RUNNING;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildImageSecretErrorTaskParams;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildPodCreateErrorTaskParams;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildTaskParams;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildTaskParamsWithPVC;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildTaskParamsWithPodSvc;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.buildTaskParamsWithWhitespace;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.getCustomVarSecret;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.getPublishArtifactSecrets;
import static io.harness.delegate.task.citasks.cik8handler.CIK8BuildTaskHandlerTestHelper.getSecretVariableDetails;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.delegate.task.citasks.cik8handler.k8java.CIK8JavaClientHandler;
import io.harness.delegate.task.citasks.cik8handler.k8java.pod.PodSpecBuilder;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CIK8InitializeTaskHandlerTest extends CategoryTest {
  @Mock private PodSpecBuilder podSpecBuilder;
  @Mock private K8sConnectorHelper k8sConnectorHelper;
  @Mock private SecretSpecBuilder secretSpecBuilder;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private K8EventHandler k8EventHandler;
  @Mock private SecretVolumesHelper secretVolumesHelper;
  @Mock private CIK8JavaClientHandler cik8JavaClientHandler;
  @Mock private CoreV1Api coreV1Api;

  @Mock private ApiClientFactory apiClientFactory;
  @Mock private ApiClient apiClient;

  @InjectMocks private CIK8InitializeTaskHandler cik8BuildTaskHandler;

  private static final String namespace = "default";
  private static final String serviceAccountName = null;
  private static String secretName = "foo";
  private static final int timeout = 100;
  private static V1Secret imgSecret =
      new V1SecretBuilder()
          .withMetadata(new V1ObjectMetaBuilder().withName(secretName).withNamespace(namespace).build())
          .withData(ImmutableMap.of(".dockercfg", "test".getBytes()))
          .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithImageSecretError() throws IOException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildImageSecretErrorTaskParams();
    ImageDetailsWithConnector imageDetailsWithConnector =
        cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList().get(0).getImageDetailsWithConnector();

    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(cik8JavaClientHandler.createRegistrySecret(eq(coreV1Api), eq(namespace), any(), eq(imageDetailsWithConnector)))
        .thenAnswer(invocation -> { throw new ApiException(); });

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithPodCreateError() throws IOException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    V1PodBuilder podBuilder = new V1PodBuilder();

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildPodCreateErrorTaskParams();

    ImageDetailsWithConnector imageDetailsWithConnector =
        cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList().get(0).getImageDetailsWithConnector();

    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(cik8JavaClientHandler.createRegistrySecret(coreV1Api, namespace, secretName, imageDetailsWithConnector))
        .thenReturn(imgSecret);
    when(podSpecBuilder.createSpec((PodParams) cik8InitializeTaskParams.getCik8PodParams())).thenReturn(podBuilder);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, podBuilder.build(), namespace))
        .thenAnswer(invocation -> { throw new ApiException(); });

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithPodReadyError() throws TimeoutException, InterruptedException, IOException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    Watch<CoreV1Event> watch = mock(Watch.class);
    V1PodBuilder podBuilder = new V1PodBuilder();

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildTaskParams();
    ConnectorDetails gitConnectorDetails = ConnectorDetails.builder().build();
    Map<String, SecretParams> gitSecretData = new HashMap<>();
    ImageDetailsWithConnector imageDetailsWithConnector =
        cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList().get(0).getImageDetailsWithConnector();

    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(cik8JavaClientHandler.createRegistrySecret(coreV1Api, namespace, secretName, imageDetailsWithConnector))
        .thenReturn(imgSecret);
    when(secretSpecBuilder.decryptGitSecretVariables(gitConnectorDetails)).thenReturn(gitSecretData);
    when(podSpecBuilder.createSpec((PodParams) cik8InitializeTaskParams.getCik8PodParams())).thenReturn(podBuilder);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, podBuilder.build(), namespace))
        .thenReturn(podBuilder.build());
    when(k8EventHandler.startAsyncPodEventWatch(eq(kubernetesConfig), eq(namespace),
             eq(cik8InitializeTaskParams.getCik8PodParams().getName()), eq(logStreamingTaskClient)))
        .thenReturn(watch);
    when(cik8JavaClientHandler.waitUntilPodIsReady(
             coreV1Api, cik8InitializeTaskParams.getCik8PodParams().getName(), namespace, timeout))
        .thenReturn(PodStatus.builder().status(ERROR).build());
    doNothing().when(k8EventHandler).stopEventWatch(watch);

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithPVC() throws InterruptedException, IOException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    V1PodBuilder podBuilder = new V1PodBuilder();
    Watch<CoreV1Event> watch = mock(Watch.class);

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildTaskParamsWithPVC();
    ImageDetailsWithConnector imageDetailsWithConnector =
        cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList().get(0).getImageDetailsWithConnector();

    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(cik8JavaClientHandler.createRegistrySecret(coreV1Api, namespace, secretName, imageDetailsWithConnector))
        .thenReturn(imgSecret);
    when(podSpecBuilder.createSpec((PodParams) cik8InitializeTaskParams.getCik8PodParams())).thenReturn(podBuilder);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, podBuilder.build(), namespace))
        .thenReturn(podBuilder.build());
    when(k8EventHandler.startAsyncPodEventWatch(eq(kubernetesConfig), eq(namespace),
             eq(cik8InitializeTaskParams.getCik8PodParams().getName()), eq(logStreamingTaskClient)))
        .thenReturn(watch);
    when(cik8JavaClientHandler.waitUntilPodIsReady(
             coreV1Api, cik8InitializeTaskParams.getCik8PodParams().getName(), namespace, timeout))
        .thenReturn(PodStatus.builder().status(ERROR).build());
    doNothing().when(k8EventHandler).stopEventWatch(watch);

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithSuccess() throws IOException, TimeoutException, InterruptedException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    V1PodBuilder podBuilder = new V1PodBuilder();
    Watch<CoreV1Event> watch = mock(Watch.class);

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildTaskParams();
    Map<String, ConnectorDetails> publishArtifactEncryptedValues = cik8InitializeTaskParams.getCik8PodParams()
                                                                       .getContainerParamsList()
                                                                       .get(0)
                                                                       .getContainerSecrets()
                                                                       .getConnectorDetailsMap();

    when(apiClientFactory.getClient(any(KubernetesConfig.class))).thenReturn(apiClient);
    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(cik8JavaClientHandler.createRegistrySecret(any(), any(), any(), any())).thenReturn(imgSecret);
    when(secretVolumesHelper.checkSecretVolumesConfigured()).thenReturn(false);
    when(secretSpecBuilder.decryptCustomSecretVariables(getSecretVariableDetails(), Collections.emptyMap()))
        .thenReturn(getCustomVarSecret());
    when(secretSpecBuilder.decryptConnectorSecretVariables(publishArtifactEncryptedValues))
        .thenReturn(getPublishArtifactSecrets());
    when(podSpecBuilder.createSpec((PodParams) cik8InitializeTaskParams.getCik8PodParams())).thenReturn(podBuilder);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(any(), any(), any())).thenReturn(podBuilder.build());
    when(k8EventHandler.startAsyncPodEventWatch(eq(kubernetesConfig), eq(namespace),
             eq(cik8InitializeTaskParams.getCik8PodParams().getName()), eq(logStreamingTaskClient)))
        .thenReturn(watch);
    when(cik8JavaClientHandler.waitUntilPodIsReady(any(), eq("pod"), eq(namespace), eq(timeout)))
        .thenReturn(PodStatus.builder().ciContainerStatusList(new ArrayList<>()).status(RUNNING).build());

    doNothing().when(k8EventHandler).stopEventWatch(watch);

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");

    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalWithServicePodSuccess() throws InterruptedException, ApiException, IOException {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    V1PodBuilder podBuilder = new V1PodBuilder();
    Watch<CoreV1Event> watch = mock(Watch.class);

    CIK8InitializeTaskParams cik8InitializeTaskParams = buildTaskParamsWithPodSvc();
    Map<String, SecretParams> gitSecretData = new HashMap<>();
    Map<String, ConnectorDetails> publishArtifactConnectors = cik8InitializeTaskParams.getCik8PodParams()
                                                                  .getContainerParamsList()
                                                                  .get(0)
                                                                  .getContainerSecrets()
                                                                  .getConnectorDetailsMap();

    ImageDetailsWithConnector imageDetailsWithConnector =
        cik8InitializeTaskParams.getCik8PodParams().getContainerParamsList().get(0).getImageDetailsWithConnector();

    when(k8sConnectorHelper.getKubernetesConfig(any(ConnectorDetails.class))).thenReturn(kubernetesConfig);
    when(secretSpecBuilder.decryptGitSecretVariables(cik8InitializeTaskParams.getCik8PodParams().getGitConnector()))
        .thenReturn(gitSecretData);
    when(cik8JavaClientHandler.createRegistrySecret(coreV1Api, namespace, secretName, imageDetailsWithConnector))
        .thenReturn(imgSecret);
    when(secretSpecBuilder.decryptCustomSecretVariables(getSecretVariableDetails(), Collections.emptyMap()))
        .thenReturn(getCustomVarSecret());
    when(secretSpecBuilder.decryptConnectorSecretVariables(publishArtifactConnectors))
        .thenReturn(getPublishArtifactSecrets());

    CIK8ServicePodParams servicePodParams = cik8InitializeTaskParams.getServicePodParams().get(0);
    when(podSpecBuilder.createSpec((PodParams) servicePodParams.getCik8PodParams())).thenReturn(podBuilder);
    when(secretVolumesHelper.checkSecretVolumesConfigured()).thenReturn(false);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, podBuilder.build(), namespace))
        .thenReturn(podBuilder.build());
    doNothing().when(cik8JavaClientHandler).createService(eq(coreV1Api), eq(namespace), any(), any(), any());
    when(podSpecBuilder.createSpec((PodParams) cik8InitializeTaskParams.getCik8PodParams())).thenReturn(podBuilder);
    when(cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, podBuilder.build(), namespace))
        .thenReturn(podBuilder.build());
    when(k8EventHandler.startAsyncPodEventWatch(eq(kubernetesConfig), eq(namespace),
             eq(cik8InitializeTaskParams.getCik8PodParams().getName()), eq(logStreamingTaskClient)))
        .thenReturn(watch);
    when(cik8JavaClientHandler.waitUntilPodIsReady(
             any(), eq(cik8InitializeTaskParams.getCik8PodParams().getName()), eq(namespace), eq(timeout)))
        .thenReturn(PodStatus.builder().status(RUNNING).build());
    doNothing().when(k8EventHandler).stopEventWatch(watch);

    K8sTaskExecutionResponse response =
        cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getType() {
    assertEquals(CIK8InitializeTaskHandler.Type.GCP_K8, cik8BuildTaskHandler.getType());
  }

  @Captor ArgumentCaptor<PodParams> podParamsArgumentCaptor;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void executeTaskWithWhitespace() throws UnsupportedEncodingException, TimeoutException, InterruptedException {
    CIK8InitializeTaskParams cik8InitializeTaskParams = buildTaskParamsWithWhitespace();
    cik8BuildTaskHandler.executeTaskInternal(cik8InitializeTaskParams, logStreamingTaskClient, "");
    Mockito.verify(podSpecBuilder).createSpec(podParamsArgumentCaptor.capture());
    PodParams captorValue = podParamsArgumentCaptor.getValue();
    assertEquals(namespace, captorValue.getNamespace());
    assertEquals(serviceAccountName, captorValue.getServiceAccountName());
  }
}
