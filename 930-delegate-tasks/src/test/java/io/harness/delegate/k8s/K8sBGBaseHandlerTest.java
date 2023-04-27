/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sTestHelper.service;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.Green;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sBGReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseConstants;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleaseSecretHelper;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import java.util.ArrayList;
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
  @Mock K8sReleaseHandler releaseHandler;

  @InjectMocks K8sBGBaseHandler k8sBGBaseHandler;

  @Mock LogCallback logCallback;
  @Mock Kubectl kubectl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
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
    String primaryColor = "green";
    String stageColor = "blue";
    KubernetesResourceId stage = KubernetesResourceId.builder().name("deployment-blue").kind("Deployment").build();
    KubernetesResource stageResource =
        KubernetesResource.builder().resourceId(stage).spec(getDeploymentSpec("deployment-blue")).build();
    KubernetesResourceId versioned =
        KubernetesResourceId.builder().name("config-1").kind("ConfigMap").versioned(true).build();
    KubernetesResource versionedResource =
        KubernetesResource.builder().resourceId(versioned).spec(getVersionedConfigmapSpec("config-1")).build();
    KubernetesResourceId primary = KubernetesResourceId.builder().name("deployment-green").kind("Deployment").build();
    KubernetesResource primaryResource =
        KubernetesResource.builder().resourceId(primary).spec(getDeploymentSpec("deployment-green")).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<K8sRelease> releaseList = new ArrayList<>();
    releaseList.add(createRelease(List.of(stageResource, versionedResource), 0, Succeeded, stageColor));
    releaseList.add(createRelease(List.of(stageResource), 1, Failed, stageColor));
    releaseList.add(createRelease(List.of(primaryResource, versionedResource), 2, Succeeded, primaryColor));
    releaseList.add(createRelease(List.of(stageResource, versionedResource), 3, Succeeded, stageColor));

    K8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().releaseHistory(releaseList).build();

    ArgumentCaptor<K8sBGReleaseHistoryCleanupDTO> cleanupCaptor =
        ArgumentCaptor.forClass(K8sBGReleaseHistoryCleanupDTO.class);
    ArgumentCaptor<List<KubernetesResourceId>> deletedResourcesCaptor = ArgumentCaptor.forClass(List.class);
    doNothing().when(releaseHandler).cleanReleaseHistoryBG(cleanupCaptor.capture());
    doNothing()
        .when(k8sTaskHelperBase)
        .delete(eq(kubectl), eq(delegateTaskParams), deletedResourcesCaptor.capture(), eq(logCallback), eq(false));
    PrePruningInfo prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(delegateTaskParams, releaseHistory,
        logCallback, primaryColor, stageColor, 3, kubectl, KubernetesConfig.builder().build(), "", true);

    assertThat(prePruningInfo.getReleaseHistoryBeforeStageCleanUp().size()).isEqualTo(4);
    assertThat(prePruningInfo.getDeletedResourcesInStage()).hasSize(1);
    assertThat(prePruningInfo.getDeletedResourcesInStage().get(0).getName()).isEqualTo("config-1");

    assertThat(cleanupCaptor.getValue().getReleasesToClean().size()).isEqualTo(2);
    assertThat(deletedResourcesCaptor.getValue().get(0).getName()).isEqualTo(versioned.getName());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCleanupForBlueGreenSameColor() throws Exception {
    IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);

    PrePruningInfo prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(K8sDelegateTaskParams.builder().build(),
        releaseHistory, logCallback, "blue", "blue", 1, kubectl, null, null, true);

    // Do nothing if colors are the same
    verifyNoMoreInteractions(releaseHistory);
    assertThat(prePruningInfo.getReleaseHistoryBeforeStageCleanUp()).isEqualTo(releaseHistory);
    assertThat(prePruningInfo.getDeletedResourcesInStage()).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForBgLegacy() throws Exception {
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

    ReleaseHistory releaseHist = ReleaseHistory.createNew();

    releaseHist.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(stage0, versioned0, persistentResource, oldResource)));
    releaseHist.setReleaseStatus(Succeeded);
    releaseHist.getLatestRelease().setManagedWorkload(stage0);
    releaseHist.setReleaseNumber(0);

    releaseHist.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(stage1, versioned1, persistentResource, oldResource)));
    releaseHist.setReleaseStatus(Failed);
    releaseHist.getLatestRelease().setManagedWorkload(stage1);
    releaseHist.setReleaseNumber(1);

    releaseHist.createNewReleaseWithResourceMap(
        getResourcesWithSpecForRelease(asList(primary, versioned2, persistentResource)));
    releaseHist.setReleaseStatus(Succeeded);
    releaseHist.getLatestRelease().setManagedWorkload(primary);
    releaseHist.setReleaseNumber(2);

    releaseHist.createNewReleaseWithResourceMap(getResourcesWithSpecForRelease(asList(currentStage, versioned3)));
    releaseHist.setReleaseStatus(Succeeded);
    releaseHist.getLatestRelease().setManagedWorkload(currentStage);
    releaseHist.setReleaseNumber(3);

    K8SLegacyReleaseHistory legacyReleaseHistory =
        K8SLegacyReleaseHistory.builder().releaseHistory(releaseHist).build();

    PrePruningInfo prePruningInfo = PrePruningInfo.builder()
                                        .releaseHistoryBeforeStageCleanUp(legacyReleaseHistory)
                                        .deletedResourcesInStage(asList(versioned0, versioned1))
                                        .build();

    when(k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
             any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean()))
        .thenAnswer(i -> i.getArguments()[2]);

    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig(primaryColor, stageColor, releaseHist.getRelease(3), prePruningInfo);
    testBGPruning(delegateTaskParams, k8sBlueGreenHandlerConfig);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForBg() throws Exception {
    String primaryColor = "green";
    String stageColor = "blue";

    KubernetesResourceId stage0 = KubernetesResourceId.builder().name(format("deployment-0-%s", stageColor)).build();
    KubernetesResource stageResource0 = KubernetesResource.builder()
                                            .resourceId(stage0)
                                            .spec(getDeploymentSpec(format("deployment-0-%s", stageColor)))
                                            .build();

    KubernetesResourceId stage1 = KubernetesResourceId.builder().name(format("deployment-1-%s", stageColor)).build();
    KubernetesResource stageResource1 = KubernetesResource.builder()
                                            .resourceId(stage1)
                                            .spec(getDeploymentSpec(format("deployment-1-%s", stageColor)))
                                            .build();

    KubernetesResourceId currentStage =
        KubernetesResourceId.builder().name(format("deployment-current-%s", stageColor)).build();
    KubernetesResource currentStageResource = KubernetesResource.builder()
                                                  .resourceId(currentStage)
                                                  .spec(getDeploymentSpec(format("deployment-current-%s", stageColor)))
                                                  .build();

    KubernetesResourceId versioned0 = KubernetesResourceId.builder().kind("ConfigMap").name("config-0").build();
    KubernetesResource versionedResource0 =
        KubernetesResource.builder().resourceId(versioned0).spec(getConfigmapSpec("config-0")).build();
    KubernetesResourceId versioned1 = KubernetesResourceId.builder().kind("ConfigMap").name("config-1").build();
    KubernetesResource versionedResource1 =
        KubernetesResource.builder().resourceId(versioned1).spec(getConfigmapSpec("config-1")).build();
    KubernetesResourceId versioned2 = KubernetesResourceId.builder().kind("ConfigMap").name("config-2").build();
    KubernetesResource versionedResource2 =
        KubernetesResource.builder().resourceId(versioned2).spec(getConfigmapSpec("config-2")).build();
    KubernetesResourceId versioned3 = KubernetesResourceId.builder().kind("ConfigMap").name("config-3").build();
    KubernetesResource versionedResource3 =
        KubernetesResource.builder().resourceId(versioned3).spec(getConfigmapSpec("config-3")).build();
    KubernetesResourceId persistentResourceId = KubernetesResourceId.builder().kind("ConfigMap").name("config").build();
    KubernetesResource persistentResource =
        KubernetesResource.builder().resourceId(persistentResourceId).spec(getConfigmapSpec("config")).build();
    KubernetesResourceId oldResourceId = KubernetesResourceId.builder().kind("ConfigMap").name("old-config").build();
    KubernetesResource oldResource =
        KubernetesResource.builder().resourceId(oldResourceId).spec(getConfigmapSpec("old-config")).build();
    KubernetesResourceId primaryId = KubernetesResourceId.builder().name(format("deployment-%s", primaryColor)).build();
    KubernetesResource primary = KubernetesResource.builder()
                                     .resourceId(primaryId)
                                     .spec(getDeploymentSpec(format("deployment-%s", primaryColor)))
                                     .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    K8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().build();

    List<K8sRelease> releaseList = new ArrayList<>();
    releaseList.add(createRelease(
        List.of(stageResource0, versionedResource0, persistentResource, oldResource), 0, Succeeded, stageColor));
    releaseList.add(createRelease(
        List.of(stageResource1, versionedResource1, persistentResource, oldResource), 1, Failed, stageColor));
    releaseList.add(
        createRelease(List.of(primary, versionedResource2, persistentResource), 2, Succeeded, primaryColor));
    K8sRelease currentRelease =
        createRelease(List.of(currentStageResource, versionedResource3), 3, Succeeded, stageColor);

    releaseHistory.setReleaseHistory(releaseList);

    PrePruningInfo prePruningInfo = PrePruningInfo.builder()
                                        .releaseHistoryBeforeStageCleanUp(releaseHistory)
                                        .deletedResourcesInStage(asList(versioned0, versioned1))
                                        .build();

    when(k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
             any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean()))
        .thenAnswer(i -> i.getArguments()[2]);

    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig(primaryColor, stageColor, currentRelease, prePruningInfo);
    testBGPruning(delegateTaskParams, k8sBlueGreenHandlerConfig);
  }

  private void testBGPruning(
      K8sDelegateTaskParams delegateTaskParams, K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig) throws Exception {
    List<KubernetesResourceId> resourcesPruned =
        k8sBGBaseHandler.pruneForBg(delegateTaskParams, logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(),
            k8sBlueGreenHandlerConfig.getStageColor(), k8sBlueGreenHandlerConfig.getPrePruningInfo(),
            k8sBlueGreenHandlerConfig.getCurrentRelease(), k8sBlueGreenHandlerConfig.getClient());
    assertThat(resourcesPruned).hasSize(3);
    assertThat(resourcesPruned.stream().map(KubernetesResourceId::getName).collect(toList()))
        .containsExactlyInAnyOrder(format("deployment-0-%s", k8sBlueGreenHandlerConfig.getStageColor()),
            format("deployment-1-%s", k8sBlueGreenHandlerConfig.getStageColor()), "old-config");
    verify(k8sTaskHelperBase, times(2))
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
    verify(logCallback).saveExecutionLog(anyString(), any(LogLevel.class), eq(SUCCESS));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForBgSameColor() throws Exception {
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    PrePruningInfo prePruningInfo = PrePruningInfo.builder().releaseHistoryBeforeStageCleanUp(null).build();

    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig("blue", "blue", K8sRelease.builder().build(), prePruningInfo);
    List<KubernetesResourceId> resourcesPruned = k8sBGBaseHandler.pruneForBg(K8sDelegateTaskParams.builder().build(),
        logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(),
        k8sBlueGreenHandlerConfig.getPrePruningInfo(), k8sBlueGreenHandlerConfig.getCurrentRelease(),
        k8sBlueGreenHandlerConfig.getClient());

    // Do nothing if colors are the same
    verifyNoMoreInteractions(releaseHistory);
    assertThat(resourcesPruned).isEmpty();
    verify(logCallback).saveExecutionLog(anyString(), any(LogLevel.class), eq(SUCCESS));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneForNoPreviousReleases() throws Exception {
    PrePruningInfo prePruningInfo1 = PrePruningInfo.builder().build();
    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig =
        getK8sBlueGreenHandlerConfig("blue", "green", K8sRelease.builder().build(), prePruningInfo1);
    List<KubernetesResourceId> resourcesPruned = k8sBGBaseHandler.pruneForBg(K8sDelegateTaskParams.builder().build(),
        logCallback, k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(),
        k8sBlueGreenHandlerConfig.getPrePruningInfo(), k8sBlueGreenHandlerConfig.getCurrentRelease(),
        k8sBlueGreenHandlerConfig.getClient());

    // Do nothing if colors are the same
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    assertThat(resourcesPruned).isEmpty();
    verify(logCallback, times(1)).saveExecutionLog(captor.capture(), any(LogLevel.class), eq(SUCCESS));
    assertThat(captor.getValue()).contains("No older releases are available in release history");
  }

  private List<KubernetesResource> getResourcesWithSpecForRelease(List<KubernetesResourceId> resourceIds) {
    return resourceIds.stream()
        .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
        .collect(toList());
  }

  private K8sRelease createRelease(
      List<KubernetesResource> resources, int number, IK8sRelease.Status status, String color) {
    V1Secret releaseSecret = new V1SecretBuilder().build();
    K8sReleaseSecretHelper.putLabelsItem(
        releaseSecret, K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY, String.valueOf(number));
    K8sReleaseSecretHelper.putLabelsItem(releaseSecret, K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY, status.name());
    K8sReleaseSecretHelper.putLabelsItem(releaseSecret, K8sReleaseConstants.RELEASE_SECRET_RELEASE_COLOR_KEY, color);

    K8sRelease release = K8sRelease.builder().releaseSecret(releaseSecret).build();
    release.setReleaseData(resources, false);

    return release;
  }

  @NotNull
  private K8sBlueGreenHandlerConfig getK8sBlueGreenHandlerConfig(
      String primaryColor, String stageColor, IK8sRelease currentRelease, PrePruningInfo prePruningInfo) {
    K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig = new K8sBlueGreenHandlerConfig();
    k8sBlueGreenHandlerConfig.setPrePruningInfo(prePruningInfo);
    k8sBlueGreenHandlerConfig.setPrimaryColor(primaryColor);
    k8sBlueGreenHandlerConfig.setPrimaryColor(primaryColor);
    k8sBlueGreenHandlerConfig.setStageColor(stageColor);
    k8sBlueGreenHandlerConfig.setCurrentRelease(currentRelease);
    k8sBlueGreenHandlerConfig.setUseDeclarativeRollback(true);
    k8sBlueGreenHandlerConfig.setClient(kubectl);
    return k8sBlueGreenHandlerConfig;
  }

  private String getDeploymentSpec(String name) {
    return "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: " + name + "\n"
        + "  labels: []\n"
        + "spec:\n"
        + "  selector:\n"
        + "    app: test-app\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      labels:\n"
        + "         app: test-app\n"
        + "---\n";
  }

  private String getVersionedConfigmapSpec(String name) {
    return "apiVersion: v1\n"
        + "kind: ConfigMap\n"
        + "versioned: true\n"
        + "metadata:\n"
        + "  name: " + name + "\n"
        + "---";
  }

  private String getConfigmapSpec(String name) {
    return "apiVersion: v1\n"
        + "kind: ConfigMap\n"
        + "metadata:\n"
        + "  name: " + name + "\n"
        + "---";
  }
}
