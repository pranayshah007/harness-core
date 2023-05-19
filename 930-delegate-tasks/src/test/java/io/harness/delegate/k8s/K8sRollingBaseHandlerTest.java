/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sRollingBaseHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private K8sRollingBaseHandler k8sRollingBaseHandler;

  @Mock private LogCallback logCallback;
  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getPodsMultipleWorkloadsInSameNamespace() throws Exception {
    doReturn(Arrays.asList(buildPod(1), buildPod(2)))
        .when(k8sTaskHelperBase)
        .getPodDetails(kubernetesConfig, "default", "release-name", 1);

    List<KubernetesResource> resources = ImmutableList.of(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("deploy-1").namespace("default").kind("Deployment").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("deploy-2").namespace("default").kind("StatefulSet").build())
            .build());

    final List<K8sPod> pods = k8sRollingBaseHandler.getPods(1, resources, kubernetesConfig, "release-name");

    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList())).containsExactly("1", "2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getPodsMultipleWorkloadsInMutipleNamespace() throws Exception {
    doReturn(Arrays.asList(buildPod(1), buildPod(2)))
        .when(k8sTaskHelperBase)
        .getPodDetails(kubernetesConfig, "default", "release-name", 1);

    doReturn(Arrays.asList(buildPod(3), buildPod(4), buildPod(5)))
        .when(k8sTaskHelperBase)
        .getPodDetails(kubernetesConfig, "harness", "release-name", 1);

    List<KubernetesResource> resources = ImmutableList.of(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("deploy-1").namespace("default").kind("Deployment").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("deploy-2").namespace("default").kind("StatefulSet").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("deploy-3").namespace("harness").kind("StatefulSet").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("deploy-4").namespace("harness").kind("Deployment").build())
            .build());

    final List<K8sPod> pods = k8sRollingBaseHandler.getPods(1, resources, kubernetesConfig, "release-name");

    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactly("1", "2", "3", "4", "5");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getPodsIfNoWorkloadGiven() throws Exception {
    assertThat(k8sRollingBaseHandler.getPods(1, null, kubernetesConfig, "release-name")).isEmpty();
    assertThat(k8sRollingBaseHandler.getPods(1, Collections.emptyList(), kubernetesConfig, "release-name")).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateDeploymentConfigRevision() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    K8sLegacyRelease.KubernetesResourceIdRevision resourceIdMock =
        Mockito.mock(K8sLegacyRelease.KubernetesResourceIdRevision.class);
    K8sLegacyRelease release = K8sLegacyRelease.builder().managedWorkloads(asList(resourceIdMock)).build();

    when(k8sTaskHelperBase.getLatestRevision(any(), any(), any())).thenReturn("2");
    when(resourceIdMock.getWorkload())
        .thenReturn(KubernetesResourceId.builder().kind(Kind.DeploymentConfig.name()).build());

    k8sRollingBaseHandler.updateManagedWorkloadsRevision(delegateTaskParams, release, null);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(resourceIdMock).setRevision(captor.capture());
    assertThat(captor.getValue()).isEqualTo("2");

    when(resourceIdMock.getWorkload()).thenReturn(KubernetesResourceId.builder().kind(Kind.Deployment.name()).build());
    verify(resourceIdMock, times(1)).setRevision(anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateMultipleWorkloadsRevision() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    K8sLegacyRelease.KubernetesResourceIdRevision deploymentId =
        Mockito.mock(K8sLegacyRelease.KubernetesResourceIdRevision.class);
    K8sLegacyRelease.KubernetesResourceIdRevision statefulSetId =
        Mockito.mock(K8sLegacyRelease.KubernetesResourceIdRevision.class);
    K8sLegacyRelease.KubernetesResourceIdRevision daemonSetId =
        Mockito.mock(K8sLegacyRelease.KubernetesResourceIdRevision.class);
    K8sLegacyRelease release =
        K8sLegacyRelease.builder().managedWorkloads(asList(deploymentId, statefulSetId, daemonSetId)).build();

    doReturn("3")
        .when(k8sTaskHelperBase)
        .getLatestRevision(nullable(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class));

    doReturn(KubernetesResourceId.builder().kind(Kind.Deployment.name()).build()).when(deploymentId).getWorkload();
    doReturn(KubernetesResourceId.builder().kind(Kind.StatefulSet.name()).build()).when(statefulSetId).getWorkload();
    doReturn(KubernetesResourceId.builder().kind(Kind.DaemonSet.name()).build()).when(daemonSetId).getWorkload();

    k8sRollingBaseHandler.updateManagedWorkloadsRevision(delegateTaskParams, release, null);
    verify(deploymentId, times(1)).setRevision("3");
    verify(statefulSetId, times(1)).setRevision("3");
    verify(daemonSetId, times(1)).setRevision("3");
  }

  private K8sPod buildPod(int name) {
    return K8sPod.builder().name(String.valueOf(name)).build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPods() throws Exception {
    KubernetesResourceId sample = KubernetesResourceId.builder().namespace("default").build();
    List<KubernetesResource> managedWorkload = Arrays.asList(KubernetesResource.builder().resourceId(sample).build(),
        KubernetesResource.builder().resourceId(sample).build());
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    k8sRollingBaseHandler.getPods(3000L, managedWorkload, kubernetesConfig, "releaseName");

    verify(k8sTaskHelperBase, times(1)).getPodDetails(kubernetesConfig, "default", "releaseName", 3000L);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetExistingPods() throws Exception {
    KubernetesResourceId sample = KubernetesResourceId.builder().namespace("default").build();
    List<KubernetesResource> managedWorkload = Arrays.asList(KubernetesResource.builder().resourceId(sample).build(),
        KubernetesResource.builder().resourceId(sample).build());
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    k8sRollingBaseHandler.getExistingPods(3000L, managedWorkload, kubernetesConfig, "releaseName", logCallback);

    verify(k8sTaskHelperBase, times(1)).getPodDetails(kubernetesConfig, "default", "releaseName", 3000L);
    verify(logCallback, times(1)).saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetExistingPodsFailed() throws Exception {
    KubernetesResourceId sample = KubernetesResourceId.builder().namespace("default").build();
    List<KubernetesResource> managedWorkload = Arrays.asList(KubernetesResource.builder().resourceId(sample).build(),
        KubernetesResource.builder().resourceId(sample).build());
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    InvalidRequestException thrownException = new InvalidRequestException("Failed to get pods");

    doThrow(thrownException).when(k8sTaskHelperBase).getPodDetails(kubernetesConfig, "default", "releaseName", 3000L);

    assertThatThrownBy(()
                           -> k8sRollingBaseHandler.getExistingPods(
                               3000L, managedWorkload, kubernetesConfig, "releaseName", logCallback))
        .isEqualTo(thrownException);

    verify(logCallback, times(1)).saveExecutionLog(thrownException.getMessage(), ERROR, FAILURE);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentSelector() {
    KubernetesResource resource = mock(KubernetesResource.class);
    prepareMockedKubernetesResource(resource);
    List<KubernetesResource> resources = singletonList(resource);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resources);

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(false, resources, false, context);
    verify(resource, never()).addLabelsInResourceSelector(anyMap(), any());

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(false, resources, true, context);
    verify(resource, times(1)).addLabelsInResourceSelector(anyMap(), any());

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(true, resources, false, context);
    verify(resource, times(2)).addLabelsInResourceSelector(anyMap(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentSelectorWrapper() {
    KubernetesResource resource1 = mock(KubernetesResource.class);
    KubernetesResource resource2 = mock(KubernetesResource.class);
    List<KubernetesResource> resources = asList(resource1, resource2);
    prepareMockedKubernetesResourceList(resources);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resources);

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(
        true, true, asList(resource1, resource2), singletonList(resource1), context);
    verify(resource1, times(1)).addLabelsInResourceSelector(anyMap(), any());
    verify(resource2, never()).addLabelsInResourceSelector(anyMap(), any());

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(
        true, false, asList(resource1, resource2), singletonList(resource1), context);
    verify(resource1, times(2)).addLabelsInResourceSelector(anyMap(), any());
    verify(resource2, times(1)).addLabelsInResourceSelector(anyMap(), any());

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(
        false, true, asList(resource1, resource2), singletonList(resource1), context);
    verify(resource1, times(3)).addLabelsInResourceSelector(anyMap(), any());
    verify(resource2, times(1)).addLabelsInResourceSelector(anyMap(), any());

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(
        false, false, asList(resource1, resource2), singletonList(resource1), context);
    verify(resource1, times(3)).addLabelsInResourceSelector(anyMap(), any());
    verify(resource2, times(1)).addLabelsInResourceSelector(anyMap(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentSelectorWrapperFfOf() {
    KubernetesResource resource1 = mock(KubernetesResource.class);
    KubernetesResource resource2 = mock(KubernetesResource.class);
    List<KubernetesResource> resources = asList(resource1, resource2);
    prepareMockedKubernetesResourceList(resources);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resources);

    k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(
        true, false, asList(resource1, resource2), singletonList(resource1), context);
    verify(resource1, times(1)).addLabelsInResourceSelector(anyMap(), any());
    verify(resource2, times(1)).addLabelsInResourceSelector(anyMap(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchLastSuccessfulReleaseFromOldReleaseHistory() throws Exception {
    K8sReleaseHandler releaseHandler = mock(K8sReleaseHandler.class);
    IK8sReleaseHistory oldReleaseHistory = mock(IK8sReleaseHistory.class);
    IK8sReleaseHistory currentReleaseHistory = mock(IK8sReleaseHistory.class);
    IK8sRelease lastSuccessfulRelease = mock(IK8sRelease.class);

    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(null).when(currentReleaseHistory).getLastSuccessfulRelease(anyInt());
    doReturn(oldReleaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(lastSuccessfulRelease).when(oldReleaseHistory).getLastSuccessfulRelease(anyInt());

    assertThat(k8sRollingBaseHandler.getLastSuccessfulRelease(true, currentReleaseHistory, 1, null, null))
        .isEqualTo(lastSuccessfulRelease);
    verify(k8sTaskHelperBase).getReleaseHandler(false);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchLastSuccessfulReleaseFromCurrentRelease() throws Exception {
    IK8sReleaseHistory currentReleaseHistory = mock(IK8sReleaseHistory.class);
    IK8sRelease lastSuccessfulRelease = mock(IK8sRelease.class);

    doReturn(lastSuccessfulRelease).when(currentReleaseHistory).getLastSuccessfulRelease(anyInt());

    assertThat(k8sRollingBaseHandler.getLastSuccessfulRelease(true, currentReleaseHistory, 1, null, null))
        .isEqualTo(lastSuccessfulRelease);
    verify(k8sTaskHelperBase, never()).getReleaseHandler(false);
  }

  private void prepareMockedKubernetesResource(KubernetesResource resource) {
    KubernetesResourceId resourceId = mock(KubernetesResourceId.class);
    when(resource.getResourceId()).thenReturn(resourceId);
    when(resourceId.getKind()).thenReturn(Kind.Deployment.name());
  }

  private void prepareMockedKubernetesResourceList(List<KubernetesResource> resourceList) {
    for (KubernetesResource resource : resourceList) {
      KubernetesResourceId mockedResource = mock(KubernetesResourceId.class);
      when(resource.getResourceId()).thenReturn(mockedResource);
      when(mockedResource.getKind()).thenReturn(Kind.Deployment.name());
    }
  }
}
