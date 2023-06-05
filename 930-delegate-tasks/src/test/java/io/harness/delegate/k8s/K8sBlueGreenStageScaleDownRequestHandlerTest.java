/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.NGInstanceUnitType.COUNT;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import io.harness.delegate.task.k8s.K8sBlueGreenStageScaleDownRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sScaleRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class K8sBlueGreenStageScaleDownRequestHandlerTest extends CategoryTest {
  @Spy @InjectMocks private K8sBGBaseHandler k8sBGBaseHandler;
  @InjectMocks private K8sBlueGreenStageScaleDownRequestHandler k8sBlueGreenStageScaleDownRequestHandler;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock private IK8sReleaseHistory releaseHistory;
  @Mock private IK8sRelease release;
  private final String namespace = "default";
  private final String releaseName = "test-release";
  private final String workingDirectory = "manifest";
  private final String kubectlPath = "clientPath";
  private final String kubeconfigPath = "configPath";
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();
  private final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;
  private final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                               .workingDirectory(workingDirectory)
                                                               .kubectlPath(kubectlPath)
                                                               .kubeconfigPath(kubeconfigPath)
                                                               .build();
  private final K8sBlueGreenStageScaleDownRequest k8sBlueGreenStageScaleDownRequest =
      K8sBlueGreenStageScaleDownRequest.builder()
          .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
          .releaseName(releaseName)
          .timeoutIntervalInMin(timeoutIntervalInMin)
          .useDeclarativeRollback(false)
          .build();
  private final KubernetesResourceId deploymentBlue = KubernetesResourceId.builder()
                                                          .kind("Deployment")
                                                          .name("release-todolist-blue")
                                                          .namespace(namespace)
                                                          .versioned(false)
                                                          .build();
  private final KubernetesResourceId deploymentGreen = KubernetesResourceId.builder()
                                                           .kind("Deployment")
                                                           .name("release-todolist-green")
                                                           .namespace(namespace)
                                                           .versioned(false)
                                                           .build();
  private final KubernetesResourceId statefulSetGreen = KubernetesResourceId.builder()
                                                            .kind("StatefulSet")
                                                            .name("release-ss-todolist-green")
                                                            .namespace(namespace)
                                                            .versioned(false)
                                                            .build();
  private final KubernetesResourceId statefulSetBlue = KubernetesResourceId.builder()
                                                           .kind("StatefulSet")
                                                           .name("release-ss-todolist-blue")
                                                           .namespace(namespace)
                                                           .versioned(false)
                                                           .build();
  private final KubernetesResourceId hpaBlue = KubernetesResourceId.builder()
                                                   .kind("HorizontalPodAutoscaler")
                                                   .name("hpa-blue")
                                                   .namespace(namespace)
                                                   .versioned(false)
                                                   .build();
  private final KubernetesResourceId pdbBlue = KubernetesResourceId.builder()
                                                   .kind("PodDisruptionBudget")
                                                   .name("pdb-blue")
                                                   .namespace(namespace)
                                                   .versioned(false)
                                                   .build();
  private final KubernetesResourceId hpaGreen = KubernetesResourceId.builder()
                                                    .kind("HorizontalPodAutoscaler")
                                                    .name("hpa-green")
                                                    .namespace(namespace)
                                                    .versioned(false)
                                                    .build();
  private final KubernetesResourceId pdbGreen = KubernetesResourceId.builder()
                                                    .kind("PodDisruptionBudget")
                                                    .name("pdb-green")
                                                    .namespace(namespace)
                                                    .versioned(false)
                                                    .build();
  private final KubernetesResourceId customHpa = KubernetesResourceId.builder()
                                                     .kind("HorizontalPodAutoscaler")
                                                     .name("custom-hpa")
                                                     .namespace(namespace)
                                                     .versioned(false)
                                                     .build();
  private final KubernetesResourceId customPdb = KubernetesResourceId.builder()
                                                     .kind("PodDisruptionBudget")
                                                     .name("custom-pdb")
                                                     .namespace(namespace)
                                                     .versioned(false)
                                                     .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(iLogStreamingTaskClient), anyString(), anyBoolean(), any());
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    Reflect.on(k8sBlueGreenStageScaleDownRequestHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testThrowInvalidArgumentExceptionWithInvalidK8sDeployRequest() throws Exception {
    K8sScaleRequest scaleRequest = K8sScaleRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .instanceUnitType(COUNT)
                                       .workload("Deployment/test-deployment")
                                       .instances(2)
                                       .releaseName(releaseName)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .build();

    assertThatThrownBy(()
                           -> k8sBlueGreenStageScaleDownRequestHandler.executeTask(
                               scaleRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testInitWithNullRelease() throws Exception {
    doReturn(null).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();

    K8sDeployResponse response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testInitWithMockK8sRelease() {
    doReturn(release).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();

    assertThatThrownBy(()
                           -> k8sBlueGreenStageScaleDownRequestHandler.executeTask(k8sBlueGreenStageScaleDownRequest,
                               delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testInitWithK8sLegacyRelease() throws Exception {
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .managedWorkload(deploymentBlue)
                                            .resources(Collections.singletonList(deploymentBlue))
                                            .status(IK8sRelease.Status.Succeeded)
                                            .build();

    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(logCallback));
    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true)))
        .thenReturn(true);

    K8sDeployResponse response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .executeDeleteHandlingPartialExecution(any(Kubectl.class), eq(delegateTaskParams),
            eq(Collections.singletonList(deploymentGreen)), eq(logCallback), eq(true));
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    k8sLegacyRelease = K8sLegacyRelease.builder()
                           .managedWorkload(deploymentGreen)
                           .resources(Arrays.asList(deploymentGreen, statefulSetGreen))
                           .status(IK8sRelease.Status.Succeeded)
                           .build();

    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentBlue), eq(logCallback));
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deploymentBlue), eq(0), eq(logCallback), eq(true)))
        .thenReturn(true);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(statefulSetBlue), eq(logCallback));
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(statefulSetBlue), eq(0), eq(logCallback), eq(true)))
        .thenReturn(true);

    response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentBlue), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(statefulSetBlue), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .executeDeleteHandlingPartialExecution(any(Kubectl.class), eq(delegateTaskParams),
            eq(Arrays.asList(deploymentBlue, statefulSetBlue)), eq(logCallback), eq(true));
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());

    // No release found with BG deployment
    KubernetesResourceId deployment = KubernetesResourceId.builder()
                                          .kind("Deployment")
                                          .name("release-todolist")
                                          .namespace(namespace)
                                          .versioned(false)
                                          .build();

    k8sLegacyRelease = K8sLegacyRelease.builder()
                           .managedWorkload(deployment)
                           .resources(Collections.singletonList(deployment))
                           .status(IK8sRelease.Status.Succeeded)
                           .build();

    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    verify(k8sTaskHelperBase, times(0))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deployment), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .executeDeleteHandlingPartialExecution(any(Kubectl.class), eq(delegateTaskParams),
            eq(Collections.singletonList(deployment)), eq(logCallback), eq(true));
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testDeleteHPAAndPDB() throws Exception {
    List<KubernetesResourceId> allResources = Arrays.asList(deploymentBlue, hpaBlue, pdbBlue);
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .managedWorkload(deploymentBlue)
                                            .resources(allResources)
                                            .status(IK8sRelease.Status.Succeeded)
                                            .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    List<KubernetesResourceId> deleteResources = Arrays.asList(hpaGreen, pdbGreen);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(logCallback));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(hpaGreen), eq(logCallback));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(pdbGreen), eq(logCallback));
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true)))
        .thenReturn(true);
    when(k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
             any(Kubectl.class), eq(delegateTaskParams), eq(deleteResources), eq(logCallback), eq(false)))
        .thenReturn(deleteResources);

    K8sDeployResponse response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), eq(delegateTaskParams), eq(deleteResources), eq(logCallback), eq(false));
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testNotToDeleteCustomHPAAndPDB() throws Exception {
    List<KubernetesResourceId> allResources =
        Arrays.asList(deploymentBlue, deploymentBlue, hpaBlue, pdbBlue, customHpa, customPdb);
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .managedWorkload(deploymentBlue)
                                            .resources(allResources)
                                            .status(IK8sRelease.Status.Succeeded)
                                            .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    List<KubernetesResourceId> deleteResources = Arrays.asList(hpaGreen, pdbGreen);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(logCallback));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(hpaGreen), eq(logCallback));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(pdbGreen), eq(logCallback));
    when(k8sTaskHelperBase.scale(
             any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true)))
        .thenReturn(true);
    when(k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
             any(Kubectl.class), eq(delegateTaskParams), eq(deleteResources), eq(logCallback), eq(false)))
        .thenReturn(deleteResources);
    K8sDeployResponse response = k8sBlueGreenStageScaleDownRequestHandler.executeTask(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentGreen), eq(0), eq(logCallback), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), eq(delegateTaskParams), eq(deleteResources), eq(logCallback), eq(false));
    assertThat(response).isEqualTo(
        K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testSwappingBlueGreenInResourceName() throws Exception {
    KubernetesResourceId deploymentBlueBlue = KubernetesResourceId.builder()
                                                  .kind("Deployment")
                                                  .name("release-todolist-blue-blue")
                                                  .namespace(namespace)
                                                  .versioned(false)
                                                  .build();
    KubernetesResourceId deploymentBlueGreen = KubernetesResourceId.builder()
                                                   .kind("Deployment")
                                                   .name("release-todolist-blue-green")
                                                   .namespace(namespace)
                                                   .versioned(false)
                                                   .build();
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .managedWorkload(deploymentBlueBlue)
                                            .resources(Collections.singletonList(deploymentBlueBlue))
                                            .status(IK8sRelease.Status.Succeeded)
                                            .build();
    doReturn(k8sLegacyRelease).when(releaseHistory).getLatestSuccessfulBlueGreenRelease();
    doReturn(true)
        .when(k8sTaskHelperBase)
        .checkIfResourceExists(any(Kubectl.class), eq(delegateTaskParams), eq(deploymentBlueGreen), eq(logCallback));
    k8sBlueGreenStageScaleDownRequestHandler.init(
        k8sBlueGreenStageScaleDownRequest, delegateTaskParams, kubernetesConfig, logCallback);
    verify(k8sTaskHelperBase, times(1)).getResourcesIdsInTableFormat(Collections.singletonList(deploymentBlueGreen));
  }
}
