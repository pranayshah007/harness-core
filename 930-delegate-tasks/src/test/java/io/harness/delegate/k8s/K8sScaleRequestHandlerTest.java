/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.NGInstanceUnitType.COUNT;
import static io.harness.beans.NGInstanceUnitType.PERCENTAGE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sScaleRequest;
import io.harness.delegate.task.k8s.K8sScaleResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sScaleRequestHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @InjectMocks private K8sScaleRequestHandler k8sScaleRequestHandler;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  private final String namespace = "default";
  private final String releaseName = "test-release";
  private final String workingDirectory = "manifest";
  private final String kubectlPath = "clientPath";
  private final String kubeconfigPath = "configPath";
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();
  private final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(iLogStreamingTaskClient), anyString(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByCountSuccessWithSkipSteadyCheck() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(true)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    List<K8sPod> pods =
        Arrays.asList(K8sPod.builder().name("old-pod").build(), K8sPod.builder().name("new-pod").newPod(true).build());

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();
    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true)))
        .thenReturn(true);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutIntervalInMillis))
        .thenReturn(pods);
    when(k8sTaskHelperBase.tagNewPods(anyList(), anyList())).thenReturn(pods);

    K8sDeployResponse response = k8sScaleRequestHandler.executeTaskInternal(
        scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sScaleResponse.class);
    K8sScaleResponse scaleResponse = (K8sScaleResponse) response.getK8sNGTaskResponse();
    assertThat(scaleResponse.getK8sPodList()).isNotEmpty();
    assertThat(scaleResponse.getK8sPodList()).containsAll(pods);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(1)).tagNewPods(anyList(), anyList());
    verify(k8sTaskHelperBase, times(2))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByCountSuccess() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    List<K8sPod> pods =
        Arrays.asList(K8sPod.builder().name("old-pod").build(), K8sPod.builder().name("new-pod").newPod(true).build());

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));

    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true)))
        .thenReturn(true);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutIntervalInMillis))
        .thenReturn(pods);
    when(k8sTaskHelperBase.tagNewPods(anyList(), anyList())).thenReturn(pods);
    when(k8sTaskHelperBase.doStatusCheck(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback), eq(true)))
        .thenReturn(true);

    K8sDeployResponse response = k8sScaleRequestHandler.executeTaskInternal(
        scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sScaleResponse.class);
    K8sScaleResponse scaleResponse = (K8sScaleResponse) response.getK8sNGTaskResponse();
    assertThat(scaleResponse.getK8sPodList()).isNotEmpty();
    assertThat(scaleResponse.getK8sPodList()).containsAll(pods);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(2))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(1)).tagNewPods(anyList(), anyList());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotScaleByCountIfNoWorkloadSpecified() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    K8sDeployResponse response = k8sScaleRequestHandler.executeTaskInternal(
        scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sScaleResponse.class);
    K8sScaleResponse scaleResponse = (K8sScaleResponse) response.getK8sNGTaskResponse();
    assertThat(scaleResponse.getK8sPodList()).isNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByCountFailScale() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    List<K8sPod> pods =
        Arrays.asList(K8sPod.builder().name("old-pod").build(), K8sPod.builder().name("new-pod").newPod(true).build());

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();
    RuntimeException thrownException = new RuntimeException("error");
    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true)))
        .thenThrow(thrownException);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutIntervalInMillis))
        .thenReturn(pods);

    assertThatThrownBy(()
                           -> k8sScaleRequestHandler.executeTaskInternal(
                               scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(0)).tagNewPods(anyList(), anyList());
    verify(k8sTaskHelperBase, times(0))
        .doStatusCheck(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByCountFailInitGetCurrentReplicas() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();

    RuntimeException thrownException = new RuntimeException("Failed to list pods");
    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenThrow(thrownException);
    assertThatThrownBy(()
                           -> k8sScaleRequestHandler.executeTaskInternal(
                               scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(0))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(0)).tagNewPods(anyList(), anyList());
    verify(k8sTaskHelperBase, times(0))
        .doStatusCheck(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByCountFailInit() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();

    assertThatThrownBy(()
                           -> k8sScaleRequestHandler.executeTaskInternal(
                               scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .hasMessageContaining(
            format(KubernetesExceptionHints.INVALID_RESOURCE_KIND_NAME_FORMAT, "test-deployment", "test-deployment"));

    verify(k8sTaskHelperBase, times(0))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(0))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(0)).tagNewPods(anyList(), anyList());
    verify(k8sTaskHelperBase, times(0))
        .doStatusCheck(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailIfNoInstanceUnitSpecified() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();

    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);

    assertThatThrownBy(()
                           -> k8sScaleRequestHandler.executeTaskInternal(
                               scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .hasMessageContaining(
            "Missing instance unit type. Select one of [COUNT, PERCENTAGE] to set the scale target replica count type");

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(0))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(0)).tagNewPods(anyList(), anyList());
    verify(k8sTaskHelperBase, times(0))
        .doStatusCheck(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByPercentageSuccess() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(PERCENTAGE)
                                       .workload("Deployment/test-deployment")
                                       .instances(200)
                                       .maxInstances(Optional.empty())
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    List<K8sPod> pods =
        Arrays.asList(K8sPod.builder().name("old-pod").build(), K8sPod.builder().name("new-pod").newPod(true).build());
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();
    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutIntervalInMillis))
        .thenReturn(pods);
    when(k8sTaskHelperBase.tagNewPods(anyList(), anyList())).thenReturn(pods);

    K8sDeployResponse response = k8sScaleRequestHandler.executeTaskInternal(
        scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sScaleResponse.class);
    K8sScaleResponse scaleResponse = (K8sScaleResponse) response.getK8sNGTaskResponse();
    assertThat(scaleResponse.getK8sPodList()).isNotEmpty();
    assertThat(scaleResponse.getK8sPodList()).containsAll(pods);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(2), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(2))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(1)).tagNewPods(anyList(), anyList());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldScaleByPercentageMaxInstancesSuccess() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(PERCENTAGE)
                                       .workload("Deployment/test-deployment")
                                       .instances(200)
                                       .maxInstances(Optional.of(5))
                                       .releaseName(releaseName)
                                       .skipSteadyStateCheck(false)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    List<K8sPod> pods =
        Arrays.asList(K8sPod.builder().name("old-pod").build(), K8sPod.builder().name("new-pod").newPod(true).build());

    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("test-deployment")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));

    when(k8sTaskHelperBase.getCurrentReplicas(
             any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(1);
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(10), eq(logCallback), eq(true)))
        .thenReturn(true);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutIntervalInMillis))
        .thenReturn(pods);
    when(k8sTaskHelperBase.tagNewPods(anyList(), anyList())).thenReturn(pods);
    when(k8sTaskHelperBase.doStatusCheck(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback)))
        .thenReturn(true);

    K8sDeployResponse response = k8sScaleRequestHandler.executeTaskInternal(
        scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sScaleResponse.class);
    K8sScaleResponse scaleResponse = (K8sScaleResponse) response.getK8sNGTaskResponse();
    assertThat(scaleResponse.getK8sPodList()).isNotEmpty();
    assertThat(scaleResponse.getK8sPodList()).containsAll(pods);

    verify(k8sTaskHelperBase, times(1))
        .getCurrentReplicas(any(Kubectl.class), eq(deployment), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(10), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(2))
        .getPodDetails(eq(kubernetesConfig), eq(namespace), eq(releaseName), eq(timeoutIntervalInMillis));
    verify(k8sTaskHelperBase, times(1)).tagNewPods(anyList(), anyList());
  }
}
