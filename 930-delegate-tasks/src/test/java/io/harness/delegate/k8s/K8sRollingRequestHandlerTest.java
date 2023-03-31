/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sTestHelper.deployment;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.InvalidRequestException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sRollingRequestHandlerTest extends CategoryTest {
  @Mock KubernetesContainerService kubernetesContainerService;
  @Mock K8sTaskHelperBase taskHelperBase;
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  @InjectMocks @Spy K8sRollingBaseHandler baseHandler;
  @InjectMocks @Spy K8sRollingRequestHandler rollingRequestHandler;

  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock LogCallback logCallback;

  @Captor ArgumentCaptor<List<KubernetesResourceId>> captor;
  final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  @Mock private IK8sReleaseHistory releaseHistory;
  @Mock private IK8sRelease release;
  final String workingDirectory = "./repo/k8s";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    doReturn(logCallback)
        .when(taskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), anyString(), anyBoolean(), eq(commandUnitsProgress));

    doReturn(KubernetesConfig.builder().namespace("default").build())
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(any(K8sInfraDelegateConfig.class), anyString(), any(LogCallback.class));

    doReturn(releaseHandler).when(taskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(release).when(releaseHandler).createRelease(anyString(), anyInt());
    doReturn(release).when(release).setReleaseData(anyList(), anyBoolean());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTask() throws Exception {
    K8sRollingDeployRequest rollingDeployRequest =
        K8sRollingDeployRequest.builder()
            .releaseName("releaseName")
            .k8sInfraDelegateConfig(mock(K8sInfraDelegateConfig.class))
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().kustomizeDirPath("dir").build())
            .useDeclarativeRollback(true)
            .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    doReturn(singletonList(deployment()))
        .when(taskHelperBase)
        .readManifestAndOverrideLocalSecrets(anyListOf(FileData.class), eq(logCallback), anyBoolean());
    doReturn(true)
        .when(taskHelperBase)
        .doStatusCheckForAllCustomResources(
            any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), eq(logCallback), eq(true), anyLong());

    K8sDeployResponse response = rollingRequestHandler.executeTask(
        rollingDeployRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isNotNull();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecuteTaskApplyFailureShouldSaveWorkloads() throws Exception {
    on(rollingRequestHandler).set("release", null);
    K8sRollingDeployRequest rollingDeployRequest =
        K8sRollingDeployRequest.builder()
            .releaseName("releaseName")
            .k8sInfraDelegateConfig(mock(K8sInfraDelegateConfig.class))
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().kustomizeDirPath("dir").build())
            .useDeclarativeRollback(false)
            .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    K8SLegacyReleaseHistory releaseHistory = mock(K8SLegacyReleaseHistory.class);
    ReleaseHistory releaseHistoryContent = mock(ReleaseHistory.class);
    K8sLegacyRelease currentRelease = mock(K8sLegacyRelease.class);
    doReturn(currentRelease).when(releaseHandler).createRelease(anyString(), anyInt());
    doReturn(currentRelease).when(currentRelease).setReleaseData(anyList(), anyBoolean());
    doReturn(releaseHistoryContent).when(releaseHistory).getReleaseHistory();
    doReturn(null).when(releaseHistoryContent).addReleaseToReleaseHistory(any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());

    RuntimeException thrownException = new RuntimeException("Failed to apply");
    doThrow(thrownException)
        .when(taskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), any(LogCallback.class),
            eq(true), eq(true), anyString());
    doReturn(Collections.singletonList(deployment()))
        .when(taskHelperBase)
        .readManifestAndOverrideLocalSecrets(anyListOf(FileData.class), eq(logCallback), anyBoolean(), anyBoolean());

    assertThatThrownBy(()
                           -> rollingRequestHandler.executeTask(
                               rollingDeployRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    Mockito.verify(taskHelperBase, times(1))
        .getLatestRevision(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalGetPodsFailed() throws Exception {
    K8sRollingDeployRequest rollingDeployRequest =
        K8sRollingDeployRequest.builder()
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().kustomizeDirPath("dir").build())
            .k8sInfraDelegateConfig(mock(K8sInfraDelegateConfig.class))
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    InvalidRequestException thrownException = new InvalidRequestException("Failed to get pods");
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(taskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));

    doReturn(singletonList(deployment()))
        .when(taskHelperBase)
        .readManifestAndOverrideLocalSecrets(anyListOf(FileData.class), eq(logCallback), anyBoolean(), anyBoolean());

    doReturn(emptyList())
        .when(baseHandler)
        .getExistingPods(anyLong(), anyListOf(KubernetesResource.class), any(KubernetesConfig.class), anyString(),
            any(LogCallback.class));
    doReturn(true)
        .when(taskHelperBase)
        .doStatusCheckForAllCustomResources(
            any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), eq(logCallback), eq(true), anyLong());
    doThrow(thrownException)
        .when(baseHandler)
        .getPods(anyLong(), anyListOf(KubernetesResource.class), any(KubernetesConfig.class), anyString());

    assertThatThrownBy(()
                           -> rollingRequestHandler.executeTaskInternal(
                               rollingDeployRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isEqualTo(thrownException);
    verify(taskHelperBase)
        .saveRelease(anyBoolean(), anyBoolean(), any(KubernetesConfig.class), any(IK8sRelease.class),
            any(IK8sReleaseHistory.class), anyString());
    verify(baseHandler, times(1))
        .addLabelsInDeploymentSelectorForCanary(anyBoolean(), anyBoolean(), anyList(), anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithPruning() throws Exception {
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    K8sRollingDeployRequest deployRequestWithPruningEnabled =
        K8sRollingDeployRequest.builder()
            .releaseName("releaseName")
            .pruningEnabled(true)
            .manifestDelegateConfig(K8sManifestDelegateConfig.builder().build())
            .k8sInfraDelegateConfig(mock(K8sInfraDelegateConfig.class))
            .useDeclarativeRollback(true)
            .build();
    doReturn(null).when(baseHandler).getLastSuccessfulRelease(anyBoolean(), any(), anyInt(), any(), any());
    List<KubernetesResourceId> prunedResourceIds = singletonList(KubernetesResourceId.builder().build());
    doReturn(prunedResourceIds).when(rollingRequestHandler).prune(any(), any(), any());

    K8sDeployResponse deployResponse = rollingRequestHandler.executeTaskInternal(
        deployRequestWithPruningEnabled, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(((K8sRollingDeployResponse) deployResponse.getK8sNGTaskResponse()).getPrunedResourceIds())
        .isEqualTo(prunedResourceIds);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPruningWithNoResourceToPrune() throws Exception {
    on(rollingRequestHandler).set("release", K8sLegacyRelease.builder().resourcesWithSpec(emptyList()).build());
    on(rollingRequestHandler).set("kubernetesConfig", KubernetesConfig.builder().namespace("ns").build());
    doNothing().when(taskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    assertThat(rollingRequestHandler.prune(null, null, logCallback)).isEmpty();

    K8sLegacyRelease releaseWithEmptySpecs = K8sLegacyRelease.builder().resourcesWithSpec(emptyList()).build();
    assertThat(rollingRequestHandler.prune(null, releaseWithEmptySpecs, logCallback)).isEmpty();

    doReturn(emptyList()).when(taskHelperBase).getResourcesToBePrunedInOrder(any(), any());
    K8sLegacyRelease releaseWithDummySpec =
        K8sLegacyRelease.builder().resourcesWithSpec(singletonList(KubernetesResource.builder().build())).build();
    assertThat(rollingRequestHandler.prune(null, releaseWithDummySpec, logCallback)).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPruning() throws Exception {
    on(rollingRequestHandler).set("release", K8sLegacyRelease.builder().resourcesWithSpec(emptyList()).build());
    on(rollingRequestHandler).set("kubernetesConfig", KubernetesConfig.builder().namespace("ns").build());
    K8sLegacyRelease releaseWithDummySpec =
        K8sLegacyRelease.builder().resourcesWithSpec(singletonList(KubernetesResource.builder().build())).build();
    List<KubernetesResourceId> toBePruned = singletonList(
        KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build());
    doReturn(toBePruned)
        .when(taskHelperBase)
        .executeDeleteHandlingPartialExecution(any(Kubectl.class), any(K8sDelegateTaskParams.class),
            anyListOf(KubernetesResourceId.class), any(LogCallback.class), anyBoolean());
    doNothing().when(taskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    doReturn(toBePruned)
        .when(taskHelperBase)
        .getResourcesToBePrunedInOrder(anyListOf(KubernetesResource.class), anyListOf(KubernetesResource.class));
    rollingRequestHandler.prune(null, releaseWithDummySpec, logCallback);
    verify(taskHelperBase).executeDeleteHandlingPartialExecution(any(), any(), captor.capture(), any(), anyBoolean());

    assertThat(captor.getValue().get(0)).isEqualTo(toBePruned.get(0));
  }
}