/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sTestHelper.service;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.Green;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.beans.K8sBlueGreenHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sBGBaseHandlerTest extends CategoryTest {
  @Mock K8sTaskHelperBase k8sTaskHelperBase;
  @Mock KubernetesContainerService kubernetesContainerService;

  @InjectMocks K8sBGBaseHandler k8sBGBaseHandler;

  @Mock LogCallback logCallback;
  @Mock Kubectl kubectl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLogColor() {
    assertThat(k8sBGBaseHandler.getLogColor(HarnessLabelValues.colorBlue)).isEqualTo(Blue);

    assertThat(k8sBGBaseHandler.getLogColor(HarnessLabelValues.colorGreen)).isEqualTo(Green);

    assertThat(k8sBGBaseHandler.getLogColor("unhandled")).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetInverseColor() {
    assertThat(k8sBGBaseHandler.getInverseColor(HarnessLabelValues.colorBlue)).isEqualTo(HarnessLabelValues.colorGreen);

    assertThat(k8sBGBaseHandler.getInverseColor(HarnessLabelValues.colorGreen)).isEqualTo(HarnessLabelValues.colorBlue);

    assertThat(k8sBGBaseHandler.getInverseColor("unhandled")).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWrapUp() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    k8sBGBaseHandler.wrapUp(delegateTaskParams, logCallback, kubectl);

    verify(k8sTaskHelperBase).describe(kubectl, delegateTaskParams, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryColor() throws Exception {
    V1Service serviceInCluster =
        new V1ServiceBuilder()
            .withSpec(new V1ServiceSpecBuilder()
                          .addToSelector(ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue))
                          .build())
            .build();
    testGetPrimaryColor(serviceInCluster, HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryColorMissingSpec() throws Exception {
    V1Service serviceInCluster = new V1ServiceBuilder().withSpec(null).build();

    testGetPrimaryColor(serviceInCluster, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryColorMissingSelector() throws Exception {
    V1Service serviceInCluster =
        new V1ServiceBuilder().withSpec(new V1ServiceSpecBuilder().withSelector(null).build()).build();

    testGetPrimaryColor(serviceInCluster, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryColorMissingService() throws Exception {
    testGetPrimaryColor(null, HarnessLabelValues.colorDefault);
  }

  private void testGetPrimaryColor(V1Service serviceInCluster, String expectedColor) throws Exception {
    KubernetesResource service = service();
    KubernetesConfig config = KubernetesConfig.builder().build();

    doReturn(serviceInCluster).when(kubernetesContainerService).getService(config, "my-service");

    String result = k8sBGBaseHandler.getPrimaryColor(service, config, logCallback);
    assertThat(result).isEqualTo(expectedColor);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetAllPods() throws Exception {
    final K8sPod stagePod = K8sPod.builder().name("stage-pod").build();
    final K8sPod primaryPod = K8sPod.builder().name("primary-pod").build();
    final List<K8sPod> stagePods = singletonList(stagePod);
    final List<K8sPod> primaryPods = singletonList(primaryPod);
    final KubernetesConfig config = KubernetesConfig.builder().build();
    final KubernetesResource managedWorkload =
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().namespace("default").build()).build();

    doReturn(stagePods).when(k8sTaskHelperBase).getPodDetailsWithColor(config, "default", "release", "stage", 1000);
    doReturn(primaryPods).when(k8sTaskHelperBase).getPodDetailsWithColor(config, "default", "release", "primary", 1000);

    List<K8sPod> pods = k8sBGBaseHandler.getAllPods(1000, config, managedWorkload, "primary", "stage", "release");

    assertThat(pods).containsExactlyInAnyOrder(stagePod, primaryPod);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCleanupForBlueGreen() throws Exception {
    KubernetesResourceId stage = KubernetesResourceId.builder().name("deployment-blue").build();
    KubernetesResourceId versioned = KubernetesResourceId.builder().name("config-1").versioned(true).build();
    KubernetesResourceId primary = KubernetesResourceId.builder().name("deployment-green").build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.createNewRelease(asList(stage, versioned));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(stage);
    releaseHistory.setReleaseNumber(0);

    releaseHistory.createNewRelease(singletonList(stage));
    releaseHistory.setReleaseStatus(Release.Status.Failed);
    releaseHistory.getLatestRelease().setManagedWorkload(stage);
    releaseHistory.setReleaseNumber(1);

    releaseHistory.createNewRelease(asList(primary, versioned));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(primary);
    releaseHistory.setReleaseNumber(2);

    releaseHistory.createNewRelease(asList(stage, versioned));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(stage);
    releaseHistory.setReleaseNumber(3);

    PrePruningInfo prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(
        delegateTaskParams, releaseHistory, logCallback, "green", "blue", releaseHistory.getLatestRelease(), kubectl);

    // PrePruningInfo should contain releaseHistory prior to deletion of stage releases history
    assertThat(prePruningInfo.getReleaseHistoryBeforeStageCleanUp().getReleases()).hasSize(4);
    assertThat(prePruningInfo.getDeletedResourcesInStage()).hasSize(1);
    assertThat(prePruningInfo.getDeletedResourcesInStage().get(0).getName()).isEqualTo("config-1");

    // Should remove all stage release expect the current one and keep primary
    assertThat(releaseHistory.getReleases()).hasSize(2);
    assertThat(releaseHistory.getRelease(2)).isNotNull();
    assertThat(releaseHistory.getRelease(3)).isNotNull();

    // Should delete resource single time since the latest and primary releases shouldn't be cleaned
    verify(k8sTaskHelperBase).delete(kubectl, delegateTaskParams, asList(versioned), logCallback, true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCleanupForBlueGreenSameColor() throws Exception {
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);

    PrePruningInfo prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(K8sDelegateTaskParams.builder().build(),
        releaseHistory, logCallback, "blue", "blue", Release.builder().build(), kubectl);

    // Do nothing if colors are the same
    verifyNoMoreInteractions(releaseHistory);
    assertThat(prePruningInfo.getReleaseHistoryBeforeStageCleanUp()).isEqualTo(releaseHistory);
    assertThat(prePruningInfo.getDeletedResourcesInStage()).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForBg() throws Exception {
    String primaryColor = "green";
    String stageColor = "blue";

    KubernetesResourceId stage0 = KubernetesResourceId.builder().name(format("deployment-0-%s", stageColor)).build();
    KubernetesResourceId stage1 = KubernetesResourceId.builder().name(format("deployment-1-%s", stageColor)).build();
    KubernetesResourceId currentStage =
        KubernetesResourceId.builder().name(format("deployment-current-%s", stageColor)).build();
    KubernetesResourceId versioned0 = KubernetesResourceId.builder().name("config-0").versioned(true).build();
    KubernetesResourceId versioned1 = KubernetesResourceId.builder().name("config-1").versioned(true).build();
    KubernetesResourceId versioned2 = KubernetesResourceId.builder().name("config-2").versioned(true).build();
    KubernetesResourceId versioned3 = KubernetesResourceId.builder().name("config-3").versioned(true).build();
    KubernetesResourceId persistentResource = KubernetesResourceId.builder().name("config").versioned(false).build();
    KubernetesResourceId oldResource = KubernetesResourceId.builder().name("old-config").versioned(false).build();
    KubernetesResourceId primary = KubernetesResourceId.builder().name(format("deployment-%s", primaryColor)).build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();

    releaseHistory.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(stage0, versioned0, persistentResource, oldResource)));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(stage0);
    releaseHistory.setReleaseNumber(0);

    releaseHistory.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(stage1, versioned1, persistentResource, oldResource)));
    releaseHistory.setReleaseStatus(Release.Status.Failed);
    releaseHistory.getLatestRelease().setManagedWorkload(stage1);
    releaseHistory.setReleaseNumber(1);

    releaseHistory.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(primary, versioned2, persistentResource)));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(primary);
    releaseHistory.setReleaseNumber(2);

    releaseHistory.createNewReleaseWithResourceMap(getResourcesWithSpecForRelease(asList(currentStage, versioned3)));
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    releaseHistory.getLatestRelease().setManagedWorkload(currentStage);
    releaseHistory.setReleaseNumber(3);

    PrePruningInfo prePruningInfo = PrePruningInfo.builder()
                                        .releaseHistoryBeforeStageCleanUp(releaseHistory)
                                        .deletedResourcesInStage(asList(versioned0, versioned1))
                                        .build();

    when(k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
             any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean()))
        .thenAnswer(i -> i.getArguments()[2]);

    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig(primaryColor, stageColor, releaseHistory.getRelease(3), prePruningInfo);

    List<KubernetesResourceId> resourcesPruned =
        k8sBGBaseHandler.pruneForBg(delegateTaskParams, logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(),
            k8sBlueGreenHandlerConfig.getStageColor(), k8sBlueGreenHandlerConfig.getPrePruningInfo(),
            k8sBlueGreenHandlerConfig.getCurrentRelease(), k8sBlueGreenHandlerConfig.getClient());
    assertThat(resourcesPruned).hasSize(3);
    assertThat(resourcesPruned.stream().map(KubernetesResourceId::getName).collect(toList()))
        .containsExactlyInAnyOrder(
            format("deployment-0-%s", stageColor), format("deployment-1-%s", stageColor), "old-config");
    verify(k8sTaskHelperBase, times(2))
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForBgSameColor() throws Exception {
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    PrePruningInfo prePruningInfo = PrePruningInfo.builder().releaseHistoryBeforeStageCleanUp(releaseHistory).build();

    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig("blue", "blue", Release.builder().build(), prePruningInfo);
    List<KubernetesResourceId> resourcesPruned = k8sBGBaseHandler.pruneForBg(K8sDelegateTaskParams.builder().build(),
        logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(),
        k8sBlueGreenHandlerConfig.getPrePruningInfo(), k8sBlueGreenHandlerConfig.getCurrentRelease(),
        k8sBlueGreenHandlerConfig.getClient());

    // Do nothing if colors are the same
    verifyNoMoreInteractions(releaseHistory);
    assertThat(resourcesPruned).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForNoPreviousReleases() throws Exception {
    PrePruningInfo prePruningInfo1 = PrePruningInfo.builder().build();
    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig("blue", "green", Release.builder().build(), prePruningInfo1);
    List<KubernetesResourceId> resourcesPruned = k8sBGBaseHandler.pruneForBg(K8sDelegateTaskParams.builder().build(),
        logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(),
        k8sBlueGreenHandlerConfig.getPrePruningInfo(), k8sBlueGreenHandlerConfig.getCurrentRelease(),
        k8sBlueGreenHandlerConfig.getClient());

    // Do nothing if colors are the same
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    assertThat(resourcesPruned).isEmpty();
    verify(logCallback, times(1)).saveExecutionLog(captor.capture());
    assertThat(captor.getValue()).contains("No older releases are available in release history");
  }

  private List<KubernetesResource> getResourcesWithSpecForRelease(List<KubernetesResourceId> resourceIds) {
    return resourceIds.stream()
        .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
        .collect(toList());
  }

  @NotNull
  private K8sBlueGreenHandlerConfig getK8sBlueGreenHandlerConfig(
      String primaryColor, String stageColor, Release currentRelease, PrePruningInfo prePruningInfo) {
    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig = new K8sBlueGreenHandlerConfig();
    k8sBlueGreenHandlerConfig.setPrePruningInfo(prePruningInfo);
    k8sBlueGreenHandlerConfig.setPrimaryColor(primaryColor);
    k8sBlueGreenHandlerConfig.setPrimaryColor(primaryColor);
    k8sBlueGreenHandlerConfig.setStageColor(stageColor);
    k8sBlueGreenHandlerConfig.setCurrentRelease(currentRelease);
    k8sBlueGreenHandlerConfig.setClient(kubectl);
    return k8sBlueGreenHandlerConfig;
  }
}
