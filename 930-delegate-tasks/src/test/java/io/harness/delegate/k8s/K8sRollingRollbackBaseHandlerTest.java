/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL;
import static io.harness.delegate.k8s.K8sTestHelper.buildProcessResult;
import static io.harness.delegate.k8s.K8sTestHelper.buildRelease;
import static io.harness.delegate.k8s.K8sTestHelper.buildReleaseMultipleManagedWorkloads;
import static io.harness.delegate.k8s.K8sTestHelper.crdNew;
import static io.harness.delegate.k8s.K8sTestHelper.crdOld;
import static io.harness.delegate.k8s.K8sTestHelper.deployment;
import static io.harness.delegate.k8s.K8sTestHelper.deploymentConfig;
import static io.harness.k8s.manifest.ManifestHelper.processYaml;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutUndoCommand;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sLegacyRelease.KubernetesResourceIdRevision;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
public class K8sRollingRollbackBaseHandlerTest extends CategoryTest {
  @Mock private LogCallback logCallback;
  @Mock private IK8sReleaseHistory ik8sReleaseHistory;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;

  @Mock K8sReleaseHandler releaseHandler;
  @Mock private K8SLegacyReleaseHistory legacyReleaseHistory;
  @Mock private K8sReleaseHistory releaseHistory;
  @Mock private K8sRollingBaseHandler rollingBaseHandler;

  @InjectMocks @Spy private K8sRollingRollbackBaseHandler k8sRollingRollbackBaseHandler;

  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
  private K8sDelegateTaskParams k8sDelegateTaskParams =
      K8sDelegateTaskParams.builder().kubectlPath("kubectl").ocPath("oc").kubeconfigPath("config-path").build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRollbackForDC() throws Exception {
    rollbackUtil(deploymentConfig(),
        "oc --kubeconfig=config-path rollout undo DeploymentConfig/test-dc --namespace=default --to-revision=1");
  }
  private K8sPod podWithName(String name) {
    return K8sPod.builder().name(name).build();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetExistingPods() throws Exception {
    K8sRollingRollbackHandlerConfig k8sRollingRollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    k8sRollingRollbackHandlerConfig.setKubernetesConfig(kubernetesConfig);

    k8sRollingRollbackBaseHandler.getExistingPods(3000, k8sRollingRollbackHandlerConfig, "releaseName", logCallback);

    verify(k8sTaskHelperBase, times(1)).getPodDetails(kubernetesConfig, "default", "releaseName", 180000000L);
    verify(logCallback, times(1)).saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRollbackForDeployment() throws Exception {
    rollbackUtil(
        deployment(), "kubectl --kubeconfig=config-path rollout undo Deployment/nginx-deployment --to-revision=1");
  }

  private void rollbackUtil(KubernetesResource kubernetesResource, String expectedOutput) throws Exception {
    int releaseNumber = 2;
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().kubectlPath("kubectl").ocPath("oc").kubeconfigPath("config-path").build();
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    K8sLegacyRelease release = K8sLegacyRelease.builder()
                                   .resources(asList(kubernetesResource.getResourceId()))
                                   .number(2)
                                   .managedWorkloads(asList(KubernetesResourceIdRevision.builder()
                                                                .workload(kubernetesResource.getResourceId())
                                                                .revision("2")
                                                                .build()))
                                   .build();
    K8sLegacyRelease previousEligibleRelease =
        K8sLegacyRelease.builder()
            .resources(asList(kubernetesResource.getResourceId()))
            .number(1)
            .managedWorkloads(asList(KubernetesResourceIdRevision.builder()
                                         .workload(kubernetesResource.getResourceId())
                                         .revision("1")
                                         .build()))
            .build();
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);
    rollbackHandlerConfig.setRelease(release);
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));
    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));

    doReturn(processResult)
        .when(k8sRollingRollbackBaseHandler)
        .executeScript(eq(k8sDelegateTaskParams), eq(expectedOutput), any(LogOutputStream.class),
            any(LogOutputStream.class), any(Map.class));
    doReturn(processResult)
        .when(k8sRollingRollbackBaseHandler)
        .runK8sExecutable(eq(k8sDelegateTaskParams), eq(logCallback), any(RolloutUndoCommand.class));
    doReturn(releaseHistory).when(legacyReleaseHistory).getReleaseHistory();
    doReturn(previousEligibleRelease).when(releaseHistory).getPreviousRollbackEligibleRelease(releaseNumber);
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");

    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);
    mock.close();
    assertThat(rollback).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFirstDeploymentFailsRollBackLegacy() throws Exception {
    String releaseName = "releaseName";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(null);
    rollbackHandlerConfig.setKubernetesConfig(kubernetesConfig);
    rollbackHandlerConfig.setUseDeclarativeRollback(false);

    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(legacyReleaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(true).when(legacyReleaseHistory).isEmpty();

    assertionsForFirstDeploymentFailsRollback(
        rollbackHandlerConfig, releaseName, "No previous release found. Skipping rollback.");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFirstDeploymentFailsRollBack() throws Exception {
    String releaseName = "releaseName";
    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(null);
    rollbackHandlerConfig.setKubernetesConfig(kubernetesConfig);
    rollbackHandlerConfig.setUseDeclarativeRollback(true);

    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(true).when(releaseHistory).isEmpty();
    doReturn(true).when(legacyReleaseHistory).isEmpty();

    assertionsForFirstDeploymentFailsRollback(
        rollbackHandlerConfig, releaseName, "No release data found. Skipping rollback.");
  }

  private void assertionsForFirstDeploymentFailsRollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      String releaseName, String logCallbackMessage) throws Exception {
    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, releaseName, logCallback);
    assertThat(rollbackHandlerConfig.isNoopRollBack()).isTrue();

    boolean result = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, null, logCallback, emptySet(), false, null);
    assertThat(result).isTrue();
    verify(logCallback).saveExecutionLog(logCallbackMessage);

    rollbackHandlerConfig.setPreviousResources(emptyList());
    k8sRollingRollbackBaseHandler.steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, null, logCallback);
    verify(logCallback)
        .saveExecutionLog("Skipping Status Check since there is no previous eligible Managed Workload.", INFO);
    verify(k8sTaskHelperBase, never()).doStatusCheck(any(), any(), any(), any());

    k8sRollingRollbackBaseHandler.postProcess(rollbackHandlerConfig, releaseName);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkloadAndDoStatusCheckLegacy() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = crdNew();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloadsLegacy(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkloadAndDoStatusCheck(rollbackHandlerConfig, previousCustomResource);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkloadAndDoStatusCheck() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = crdNew();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloads(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkloadAndDoStatusCheck(rollbackHandlerConfig, previousCustomResource);
  }

  private void assertionsForRollbackCustomWorkloadAndDoStatusCheck(
      K8sRollingRollbackHandlerConfig rollbackHandlerConfig, KubernetesResource previousCustomResource)
      throws Exception {
    when(k8sTaskHelperBase.doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(),
             any(K8sDelegateTaskParams.class), any(LogCallback.class), anyBoolean(), anyLong(), eq(false)))
        .thenReturn(true);

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    boolean result = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 2, logCallback, emptySet(), false, null);
    assertThat(result).isTrue();

    k8sRollingRollbackBaseHandler.steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, 10, logCallback);
    k8sRollingRollbackBaseHandler.postProcess(rollbackHandlerConfig, "releaseName");

    ArgumentCaptor<List> statusCheckCustomWorkloadsCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), statusCheckCustomWorkloadsCaptor.capture(),
            any(K8sDelegateTaskParams.class), any(LogCallback.class), anyBoolean(), anyLong());
    List<KubernetesResource> customWorkloadsUnderCheck = statusCheckCustomWorkloadsCaptor.getValue();
    assertThat(customWorkloadsUnderCheck).isNotEmpty();
    assertThat(customWorkloadsUnderCheck.get(0)).isEqualTo(previousCustomResource);

    IK8sRelease releaseToSave = rollbackHandlerConfig.getRelease();
    assertThat(releaseToSave).isNotNull();
    assertThat(releaseToSave.getReleaseStatus()).isEqualTo(Failed);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkloadLegacy() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = crdNew();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloadsLegacy(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkload(rollbackHandlerConfig, previousCustomResource, currentCustomResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkload() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = crdNew();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloads(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkload(rollbackHandlerConfig, previousCustomResource, currentCustomResource);
  }

  private void assertionsForRollbackCustomWorkload(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      KubernetesResource previousCustomResource, KubernetesResource currentCustomResource) throws Exception {
    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 2, logCallback, emptySet(), false, null);
    assertThat(rollback).isTrue();

    ArgumentCaptor<List> previousCustomWorkloadsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> currentCustomWorkloadsCaptor = ArgumentCaptor.forClass(List.class);

    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), currentCustomWorkloadsCaptor.capture(),
            any(LogCallback.class), anyBoolean());

    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), previousCustomWorkloadsCaptor.capture(), any(K8sDelegateTaskParams.class),
            any(LogCallback.class), anyBoolean(), eq(false), any());

    List<KubernetesResource> previousCustomWorkloads = previousCustomWorkloadsCaptor.getValue();
    assertThat(previousCustomWorkloads).isNotEmpty();
    assertThat(previousCustomWorkloads.get(0)).isEqualTo(previousCustomResource);

    List<KubernetesResourceId> currentCustomWorkloads = currentCustomWorkloadsCaptor.getValue();
    assertThat(currentCustomWorkloads).isNotEmpty();
    assertThat(currentCustomWorkloads.get(0)).isEqualTo(currentCustomResource.getResourceId());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkloadWithoutDeletingCurrentLegacy() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = deployment();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloadsLegacy(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkloadWithoutDeletingCurrent(rollbackHandlerConfig, previousCustomResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRollbackCustomWorkloadWithoutDeletingCurrent() throws Exception {
    KubernetesResource previousCustomResource = crdOld();
    KubernetesResource currentCustomResource = deployment();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig =
        prepareRollbackCustomWorkloads(previousCustomResource, currentCustomResource);

    assertionsForRollbackCustomWorkloadWithoutDeletingCurrent(rollbackHandlerConfig, previousCustomResource);
  }

  private void assertionsForRollbackCustomWorkloadWithoutDeletingCurrent(
      K8sRollingRollbackHandlerConfig rollbackHandlerConfig, KubernetesResource previousCustomResource)
      throws Exception {
    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 2, logCallback, emptySet(), false, null);
    assertThat(rollback).isTrue();

    ArgumentCaptor<List> previousCustomWorkloadsCaptor = ArgumentCaptor.forClass(List.class);

    verify(k8sTaskHelperBase, times(0))
        .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());

    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), previousCustomWorkloadsCaptor.capture(), any(K8sDelegateTaskParams.class),
            any(LogCallback.class), anyBoolean(), eq(false), any());

    List<KubernetesResource> previousCustomWorkloads = previousCustomWorkloadsCaptor.getValue();
    assertThat(previousCustomWorkloads).isNotEmpty();
    assertThat(previousCustomWorkloads.get(0)).isEqualTo(previousCustomResource);
  }

  private K8sRollingRollbackHandlerConfig prepareRollbackCustomWorkloadsLegacy(
      KubernetesResource previousCustomResource, KubernetesResource currentCustomResource) throws Exception {
    ReleaseHistory releaseHist = mock(ReleaseHistory.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setKubernetesConfig(kubernetesConfig);
    K8sLegacyRelease release = K8sLegacyRelease.builder()
                                   .resources(asList(currentCustomResource.getResourceId()))
                                   .number(2)
                                   .managedWorkloads(getManagedWorkloads(currentCustomResource))
                                   .customWorkloads(getCustomWorkloads(currentCustomResource))
                                   .build();
    K8sLegacyRelease previousEligibleRelease = K8sLegacyRelease.builder()
                                                   .resources(asList(previousCustomResource.getResourceId()))
                                                   .number(1)
                                                   .customWorkloads(asList(previousCustomResource))
                                                   .build();

    when(legacyReleaseHistory.getReleaseHistory()).thenReturn(releaseHist);
    when(releaseHist.getPreviousRollbackEligibleRelease(anyInt())).thenReturn(previousEligibleRelease);
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);
    rollbackHandlerConfig.setRelease(release);
    rollbackHandlerConfig.setUseDeclarativeRollback(false);
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));
    when(k8sTaskHelperBase.applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class),
             any(LogCallback.class), anyBoolean(), eq(false), anyString()))
        .thenReturn(true);
    when(k8sTaskHelperBase.getReleaseHandler(anyBoolean())).thenReturn(releaseHandler);
    when(releaseHandler.getReleaseHistory(any(), any())).thenReturn(legacyReleaseHistory);
    when(legacyReleaseHistory.isEmpty()).thenReturn(false);
    when(legacyReleaseHistory.getLatestRelease()).thenReturn(release);

    return rollbackHandlerConfig;
  }

  private K8sRollingRollbackHandlerConfig prepareRollbackCustomWorkloads(
      KubernetesResource previousCustomResource, KubernetesResource currentCustomResource) throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    K8sRelease previousEligibleRelease = mock(K8sRelease.class);
    K8sRelease currentRelease = mock(K8sRelease.class);
    rollbackHandlerConfig.setKubernetesConfig(kubernetesConfig);
    rollbackHandlerConfig.setReleaseHistory(releaseHistory);
    rollbackHandlerConfig.setRelease(currentRelease);
    rollbackHandlerConfig.setUseDeclarativeRollback(true);
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));

    doReturn(previousEligibleRelease).when(releaseHistory).getLastSuccessfulRelease(anyInt());
    doReturn(List.of(previousCustomResource)).when(previousEligibleRelease).getResourcesWithSpecs();
    doReturn(List.of(currentCustomResource)).when(currentRelease).getResourcesWithSpecs();
    doReturn(1).when(previousEligibleRelease).getReleaseNumber();
    doReturn(Succeeded).when(previousEligibleRelease).getReleaseStatus();
    doReturn(currentRelease).when(currentRelease).updateReleaseStatus(any());
    doReturn(Failed).when(currentRelease).getReleaseStatus();

    when(k8sTaskHelperBase.getReleaseHandler(anyBoolean())).thenReturn(releaseHandler);
    when(releaseHandler.getReleaseHistory(any(), any())).thenReturn(releaseHistory);
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(currentRelease);

    return rollbackHandlerConfig;
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void rollback() throws Exception {
    testRollBackReleaseIsNull();
    testRollBackIfNoManagedWorkload();
    testRollBackToSpecificRelease();
  }

  private void testRollBackReleaseIsNull() throws Exception {
    final boolean success = k8sRollingRollbackBaseHandler.rollback(new K8sRollingRollbackHandlerConfig(),
        K8sDelegateTaskParams.builder().build(), null, logCallback, emptySet(), false, null);

    assertThat(success).isTrue();
  }

  private void testRollBackIfNoManagedWorkload() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setRelease(new K8sLegacyRelease());
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);
    final boolean success = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, K8sDelegateTaskParams.builder().build(), 2, logCallback, emptySet(), false, null);
    assertThat(success).isTrue();
  }

  private void testRollBackToSpecificRelease() throws Exception {
    rollback1ManagedWorkload();
    rollbackMultipleWorkloads();
  }

  private void rollback1ManagedWorkload() throws Exception {
    reset(k8sRollingRollbackBaseHandler);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));
    doReturn(buildProcessResult(0)).when(k8sRollingRollbackBaseHandler).runK8sExecutable(any(), any(), any());

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(buildRelease(Failed, 2));
    releaseHistory.getReleases().add(buildRelease(Succeeded, 1));
    releaseHistory.getReleases().add(buildRelease(Succeeded, 0));
    rollbackHandlerConfig.setRelease(releaseHistory.getLatestRelease());
    rollbackHandlerConfig.setReleaseHistory(K8SLegacyReleaseHistory.builder().releaseHistory(releaseHistory).build());

    final boolean success = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, K8sDelegateTaskParams.builder().build(), 2, logCallback, emptySet(), false, null);

    ArgumentCaptor<RolloutUndoCommand> captor = ArgumentCaptor.forClass(RolloutUndoCommand.class);
    verify(k8sRollingRollbackBaseHandler, times(1)).runK8sExecutable(any(), any(), captor.capture());

    RolloutUndoCommand rolloutUndoCommand = captor.getValue();
    assertThat(rolloutUndoCommand.command())
        .isEqualTo("kubectl --kubeconfig=config-path rollout undo Deployment/nginx-deployment");
    assertThat(success).isTrue();
  }

  private void rollbackMultipleWorkloads() throws Exception {
    reset(k8sRollingRollbackBaseHandler);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));
    doReturn(buildProcessResult(0)).when(k8sRollingRollbackBaseHandler).runK8sExecutable(any(), any(), any());
    doReturn(buildProcessResult(0))
        .when(k8sRollingRollbackBaseHandler)
        .executeScript(any(), anyString(), any(), any(), any());

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(buildReleaseMultipleManagedWorkloads(Failed));
    releaseHistory.getReleases().add(buildReleaseMultipleManagedWorkloads(Succeeded));
    releaseHistory.getReleases().add(buildReleaseMultipleManagedWorkloads(Succeeded));
    rollbackHandlerConfig.setRelease(releaseHistory.getLatestRelease());
    rollbackHandlerConfig.setReleaseHistory(K8SLegacyReleaseHistory.builder().releaseHistory(releaseHistory).build());
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");

    final boolean success = k8sRollingRollbackBaseHandler.legacyRollback(rollbackHandlerConfig,
        K8sDelegateTaskParams.builder().kubeconfigPath("kubeconfig").build(), 2, logCallback, emptySet(), false, null);
    mock.close();

    ArgumentCaptor<RolloutUndoCommand> captor = ArgumentCaptor.forClass(RolloutUndoCommand.class);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sRollingRollbackBaseHandler, times(1)).runK8sExecutable(any(), any(), captor.capture());
    verify(k8sRollingRollbackBaseHandler, times(1))
        .executeScript(any(), stringArgumentCaptor.capture(), any(), any(), any());

    RolloutUndoCommand rolloutUndoCommand = captor.getValue();
    assertThat(rolloutUndoCommand.command())
        .isEqualTo("kubectl --kubeconfig=config-path rollout undo Deployment/nginx-deployment --to-revision=2");
    assertThat(success).isTrue();

    String command = stringArgumentCaptor.getValue();
    assertThat(command).isEqualTo(
        "oc --kubeconfig=kubeconfig rollout undo DeploymentConfig/test-dc --namespace=default --to-revision=2");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSkipRollback() throws Exception {
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);
    KubernetesResource currentCustomResource = crdNew();
    K8sLegacyRelease release = K8sLegacyRelease.builder()
                                   .resources(asList(currentCustomResource.getResourceId()))
                                   .customWorkloads(asList(currentCustomResource))
                                   .status(Succeeded)
                                   .build();
    rollbackHandlerConfig.setRelease(release);

    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 0, logCallback, emptySet(), false, null);
    assertThat(rollback).isTrue();
    ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, times(1)).saveExecutionLog(logCaptor.capture());
    assertThat(logCaptor.getValue()).isEqualTo("No failed release found. Skipping rollback.");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSkipRollbackNoPreviousEligibleRelease() throws Exception {
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    KubernetesResource currentCustomResource = crdNew();
    K8sLegacyRelease release = K8sLegacyRelease.builder()
                                   .resources(asList(currentCustomResource.getResourceId()))
                                   .customWorkloads(asList(currentCustomResource))
                                   .status(Succeeded)
                                   .build();
    rollbackHandlerConfig.setRelease(release);
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);
    when(legacyReleaseHistory.getLastSuccessfulRelease(anyInt())).thenReturn(null);
    doReturn(releaseHistory).when(legacyReleaseHistory).getReleaseHistory();
    doReturn(null).when(releaseHistory).getPreviousRollbackEligibleRelease(anyInt());

    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 1, logCallback, emptySet(), false, null);
    assertThat(rollback).isTrue();
    ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback).saveExecutionLog(logCaptor.capture());
    assertThat(logCaptor.getValue()).isEqualTo("No previous eligible release found. Can't rollback.");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRollbackNoManagedWorkloadInPreviousEligibleRelease() throws Exception {
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    KubernetesResource currentCustomResource = crdNew();
    K8sLegacyRelease release = K8sLegacyRelease.builder()
                                   .resources(asList(currentCustomResource.getResourceId()))
                                   .customWorkloads(asList(currentCustomResource))
                                   .status(Succeeded)
                                   .build();
    rollbackHandlerConfig.setRelease(release);
    rollbackHandlerConfig.setReleaseHistory(legacyReleaseHistory);

    KubernetesResource previousCustomResource = crdOld();
    K8sLegacyRelease previousEligibleRelease = K8sLegacyRelease.builder()
                                                   .resources(asList(previousCustomResource.getResourceId()))
                                                   .number(1)
                                                   .status(Succeeded)
                                                   .build();
    doReturn(releaseHistory).when(legacyReleaseHistory).getReleaseHistory();
    doReturn(previousEligibleRelease).when(releaseHistory).getPreviousRollbackEligibleRelease(anyInt());

    boolean rollback = k8sRollingRollbackBaseHandler.rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, 1, logCallback, emptySet(), false, null);
    assertThat(rollback).isTrue();
    ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, times(2)).saveExecutionLog(logCaptor.capture());
    List<String> allLogs = logCaptor.getAllValues();
    assertThat(allLogs).containsExactly("Previous eligible Release is 1 with status Succeeded",
        "No Managed Workload found in previous eligible release. Skipping rollback.");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRollbackFailed() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setClient(Kubectl.client("kubectl", "config-path"));
    doReturn(buildProcessResult(1)).when(k8sRollingRollbackBaseHandler).runK8sExecutable(any(), any(), any());

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(buildRelease(Failed, 2));
    releaseHistory.getReleases().add(buildRelease(Succeeded, 1));
    releaseHistory.getReleases().add(buildRelease(Succeeded, 0));
    rollbackHandlerConfig.setRelease(releaseHistory.getLatestRelease());
    rollbackHandlerConfig.setReleaseHistory(K8SLegacyReleaseHistory.builder().releaseHistory(releaseHistory).build());

    final boolean success = k8sRollingRollbackBaseHandler.legacyRollback(
        rollbackHandlerConfig, K8sDelegateTaskParams.builder().build(), 2, logCallback, emptySet(), false, null);
    assertThat(success).isFalse();

    ArgumentCaptor<RolloutUndoCommand> captor = ArgumentCaptor.forClass(RolloutUndoCommand.class);
    verify(k8sRollingRollbackBaseHandler, times(1)).runK8sExecutable(any(), any(), captor.capture());
    RolloutUndoCommand rolloutUndoCommand = captor.getValue();
    assertThat(rolloutUndoCommand.command())
        .isEqualTo("kubectl --kubeconfig=config-path rollout undo Deployment/nginx-deployment");

    ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, times(4)).saveExecutionLog(logCaptor.capture());
    List<String> logValues = logCaptor.getAllValues();
    assertThat(logValues).contains("Previous eligible Release is 1 with status Succeeded",
        "\nRolling back to release 1",
        "\nRolling back resource Deployment/nginx-deployment in namespace null to revision null");
  }

  private List<KubernetesResourceIdRevision> getManagedWorkloads(KubernetesResource kubernetesResource) {
    if (kubernetesResource.getResourceId().getKind().equals("Deployment")) {
      return asList(K8sLegacyRelease.KubernetesResourceIdRevision.builder()
                        .workload(kubernetesResource.getResourceId())
                        .revision("2")
                        .build());
    }

    return emptyList();
  }

  private List<KubernetesResource> getCustomWorkloads(KubernetesResource kubernetesResource) {
    if (kubernetesResource.isManagedWorkload() && !kubernetesResource.getResourceId().getKind().equals("Deployment")) {
      return asList(kubernetesResource);
    }

    return emptyList();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void TC1_testRecreatePrunedResources() throws Exception {
    // no pruned resource
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    assertThat(k8sRollingRollbackBaseHandler.recreatePrunedResources(
                   rollbackHandlerConfig, 1, emptyList(), logCallback, k8sDelegateTaskParams, null))
        .isEqualTo(ResourceRecreationStatus.NO_RESOURCE_CREATED);
    verify(ik8sReleaseHistory, never()).getLastSuccessfulRelease(anyInt());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void TC2_testRecreatePrunedResources() throws Exception {
    // no release history
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(null);
    assertThat(k8sRollingRollbackBaseHandler.recreatePrunedResources(rollbackHandlerConfig, 1,
                   ImmutableList.of(KubernetesResourceId.builder().build()), logCallback, k8sDelegateTaskParams, null))
        .isEqualTo(ResourceRecreationStatus.NO_RESOURCE_CREATED);
    verify(ik8sReleaseHistory, never()).getLastSuccessfulRelease(anyInt());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetResourcesRecreated() {
    List<KubernetesResourceId> prunedResourceIds =
        singletonList(KubernetesResourceId.builder().name("dummy_name").build());
    assertThat(k8sRollingRollbackBaseHandler.getResourcesRecreated(prunedResourceIds, RESOURCE_CREATION_SUCCESSFUL))
        .isEqualTo(new HashSet<>(prunedResourceIds));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void TC3_testRecreatePrunedResources() throws Exception {
    // no successful deployment in release history
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    doReturn(null).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());
    assertThat(k8sRollingRollbackBaseHandler.recreatePrunedResources(rollbackHandlerConfig, 1,
                   ImmutableList.of(KubernetesResourceId.builder().build()), logCallback, k8sDelegateTaskParams, null))
        .isEqualTo(ResourceRecreationStatus.NO_RESOURCE_CREATED);
    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void TC4_testRecreatePrunedResources() throws Exception {
    // pruned resources are not present in last successful release
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    KubernetesResource resourcesInPreviousSuccessfulRelease =
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build();
    K8sLegacyRelease previousSuccessfulRelease =
        K8sLegacyRelease.builder().resourcesWithSpec(ImmutableList.of(resourcesInPreviousSuccessfulRelease)).build();
    doReturn(previousSuccessfulRelease).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());

    assertThat(k8sRollingRollbackBaseHandler.recreatePrunedResources(rollbackHandlerConfig, 1,
                   ImmutableList.of(KubernetesResourceId.builder().name("resource1").build()), logCallback,
                   k8sDelegateTaskParams, null))
        .isEqualTo(ResourceRecreationStatus.NO_RESOURCE_CREATED);
    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void TC5_testRecreatePrunedResources() throws Exception {
    // pruning resources
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    rollbackHandlerConfig.setClient(mock(Kubectl.class));
    rollbackHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());

    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(KubernetesResourceId.builder().name("resource0").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource1").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource2").build());

    List<KubernetesResource> resourcesInPreviousSuccessfulRelease = new ArrayList<>();
    resourcesInPreviousSuccessfulRelease.add(
        KubernetesResource.builder().spec("spec0").resourceId(resourceIds.get(0)).build());
    resourcesInPreviousSuccessfulRelease.add(
        KubernetesResource.builder().spec("spec1").resourceId(resourceIds.get(1)).build());

    Map<String, String> k8sCommandFlagExpected = ImmutableMap.of("Apply", "--server-side");
    IK8sRelease previousSuccessfulRelease = mock(IK8sRelease.class);
    doReturn(resourcesInPreviousSuccessfulRelease).when(previousSuccessfulRelease).getResourcesWithSpecs();
    doReturn(previousSuccessfulRelease).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(), anyList(), any(K8sDelegateTaskParams.class), any(LogCallback.class), anyBoolean(), any());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());

    assertThat(k8sRollingRollbackBaseHandler.recreatePrunedResources(
                   rollbackHandlerConfig, 1, resourceIds, logCallback, k8sDelegateTaskParams, k8sCommandFlagExpected))
        .isEqualTo(ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL);
    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), any(LogCallback.class),
            anyBoolean(), eq("--server-side"));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteNewlyCreatedResourcesSuccess() throws Exception {
    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(KubernetesResourceId.builder().name("resource0").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource1").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource2").build());

    List<KubernetesResource> resources =
        resourceIds.stream()
            .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
            .collect(toList());

    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    IK8sRelease currentRelease = mock(IK8sRelease.class);
    doReturn(resources).when(currentRelease).getResourcesWithSpecs();
    doReturn(resourceIds).when(currentRelease).getResourceIds();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    rollbackHandlerConfig.setRelease(currentRelease);
    rollbackHandlerConfig.setClient(mock(Kubectl.class));
    rollbackHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());

    List<KubernetesResourceId> resourcesInPreviousSuccessfulRelease = new ArrayList<>();
    resourcesInPreviousSuccessfulRelease.add(resourceIds.get(0));

    IK8sRelease previousSuccessfulRelease = mock(IK8sRelease.class);
    doReturn(List.of(resources.get(0))).when(previousSuccessfulRelease).getResourcesWithSpecs();
    doReturn(previousSuccessfulRelease).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    when(k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(anyList())).thenAnswer(i -> i.getArguments()[0]);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

    k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(
        rollbackHandlerConfig, 1, logCallback, k8sDelegateTaskParams);

    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, times(1))
        .executeDeleteHandlingPartialExecution(any(Kubectl.class), any(K8sDelegateTaskParams.class), captor.capture(),
            any(LogCallback.class), anyBoolean());
    List<KubernetesResourceId> resourceToBeDeleted = new ArrayList<>(captor.getValue());
    assertThat(resourceToBeDeleted).hasSize(2);
    assertThat(resourceToBeDeleted.stream().map(KubernetesResourceId::getName))
        .containsExactlyInAnyOrder("resource1", "resource2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteNewlyCreatedResourcesFail() throws Exception {
    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(KubernetesResourceId.builder().name("resource0").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource1").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource2").build());

    List<KubernetesResource> resources =
        resourceIds.stream()
            .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
            .collect(toList());

    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    K8sLegacyRelease currentRelease =
        K8sLegacyRelease.builder().resources(resourceIds).resourcesWithSpec(resources).build();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    rollbackHandlerConfig.setRelease(currentRelease);
    rollbackHandlerConfig.setClient(mock(Kubectl.class));
    rollbackHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());

    List<KubernetesResourceId> resourcesInPreviousSuccessfulRelease = new ArrayList<>();
    resourcesInPreviousSuccessfulRelease.add(resourceIds.get(0));
    resourcesInPreviousSuccessfulRelease.add(resourceIds.get(1));

    K8sLegacyRelease previousSuccessfulRelease =
        K8sLegacyRelease.builder().resources(resourcesInPreviousSuccessfulRelease).build();
    doReturn(previousSuccessfulRelease).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    when(k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(anyList())).thenAnswer(i -> i.getArguments()[0]);

    doThrow(new InvalidRequestException("dummy exception"))
        .when(k8sTaskHelperBase)
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());

    assertThatCode(()
                       -> k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(
                           rollbackHandlerConfig, 1, logCallback, k8sDelegateTaskParams))
        .doesNotThrowAnyException();

    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, times(1))
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteWithNoReleaseHistoryPresent() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(null);

    k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(
        rollbackHandlerConfig, 1, logCallback, k8sDelegateTaskParams);

    verify(ik8sReleaseHistory, never()).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, never())
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteWithNoPreviousSuccessfulReleasePresent() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    doReturn(null).when(ik8sReleaseHistory).getLastSuccessfulRelease(anyInt());

    k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(
        rollbackHandlerConfig, 1, logCallback, k8sDelegateTaskParams);

    verify(ik8sReleaseHistory, times(1)).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, never())
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteAbortCase() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setReleaseHistory(ik8sReleaseHistory);
    rollbackHandlerConfig.setRelease(K8sLegacyRelease.builder().status(Succeeded).build());

    k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(
        rollbackHandlerConfig, null, logCallback, k8sDelegateTaskParams);

    verify(ik8sReleaseHistory, never()).getLastSuccessfulRelease(anyInt());
    verify(k8sTaskHelperBase, never())
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testInitWithImperativeRollback() throws Exception {
    // use imperative rollback if FF is off
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(false);
    K8sLegacyRelease latestRelease = mock(K8sLegacyRelease.class);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(legacyReleaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(false).when(legacyReleaseHistory).isEmpty();
    doReturn(latestRelease).when(legacyReleaseHistory).getLatestRelease();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.getRelease()).isEqualTo(latestRelease);
    assertThat(rollbackHandlerConfig.getReleaseHistory()).isEqualTo(legacyReleaseHistory);
    assertThat(rollbackHandlerConfig.isUseDeclarativeRollback()).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testNoOpRollbackGivenEmptyImperativeHistory() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(legacyReleaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(true).when(legacyReleaseHistory).isEmpty();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isUseDeclarativeRollback()).isFalse();
    assertThat(rollbackHandlerConfig.isNoopRollBack()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testNoOpRollbackGivenEmptyImperativeAndDeclarativeHistory() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(true).when(releaseHistory).isEmpty();
    doReturn(true).when(legacyReleaseHistory).isEmpty();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isNoopRollBack()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testNoOpRollbackGivenNoRollbackEligibleReleaseInBothHistories() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);
    rollbackHandlerConfig.setCurrentReleaseNumber(2);

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    K8sRelease latestRelease = mock(K8sRelease.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(false).when(legacyReleaseHistory).isEmpty();
    doReturn(null).when(releaseHistory).getLastSuccessfulRelease(anyInt());
    doReturn(null).when(legacyReleaseHistory).getLastSuccessfulRelease(anyInt());

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.getRelease()).isEqualTo(latestRelease);
    assertThat(rollbackHandlerConfig.getReleaseHistory()).isEqualTo(releaseHistory);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testNoOpRollbackWhenLatestDeclarativeReleaseSucceededAndReleaseNumberIsNull() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);
    K8sRelease latestRelease = mock(K8sRelease.class);

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Succeeded).when(latestRelease).getReleaseStatus();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isNoopRollBack()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackWhenLatestDeclarativeReleaseIsFailedAndReleaseNumberIsNull() throws Exception {
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);
    K8sRelease latestRelease = mock(K8sRelease.class);
    K8sRelease lastSuccessfulRelease = mock(K8sRelease.class);

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Failed).when(latestRelease).getReleaseStatus();
    doReturn(lastSuccessfulRelease).when(releaseHistory).getLastSuccessfulRelease(Integer.MAX_VALUE);

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.getRelease()).isEqualTo(latestRelease);
    assertThat(rollbackHandlerConfig.getReleaseHistory()).isEqualTo(releaseHistory);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackWhenLatestDeclarativeReleaseIsFailedAndNoEligibleDeclarativeReleaseExists() throws Exception {
    List<KubernetesResource> resources =
        getKubernetesResourcesFromFiles(List.of("/k8s/deployment.yaml", "/k8s/configMap.yaml", "/k8s/service.yaml"));

    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);
    K8sRelease latestRelease = mock(K8sRelease.class);
    K8sLegacyRelease lastSuccessfulRelease = mock(K8sLegacyRelease.class);

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Failed).when(latestRelease).getReleaseStatus();
    doReturn(null).when(releaseHistory).getLastSuccessfulRelease(Integer.MAX_VALUE);
    doReturn(lastSuccessfulRelease).when(legacyReleaseHistory).getLastSuccessfulRelease(Integer.MAX_VALUE);
    doReturn(1).when(latestRelease).getReleaseNumber();
    doReturn(resources).when(latestRelease).getResourcesWithSpecs();
    doNothing().when(rollingBaseHandler).setManagedWorkloadsInRelease(any(), anyList(), any(), any());
    doNothing().when(rollingBaseHandler).setCustomWorkloadsInRelease(any(), any());

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isUseDeclarativeRollback()).isFalse();
    assertThat(rollbackHandlerConfig.getCurrentReleaseNumber()).isEqualTo(Integer.MAX_VALUE);
    assertThat(rollbackHandlerConfig.getReleaseHistory()).isEqualTo(legacyReleaseHistory);

    K8sLegacyRelease release = (K8sLegacyRelease) rollbackHandlerConfig.getRelease();
    List<KubernetesResourceId> actualResources = release.getResourceIds();
    List<KubernetesResourceId> expectedResources =
        resources.stream().map(KubernetesResource::getResourceId).collect(toList());
    assertThat(expectedResources.containsAll(actualResources)).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSkipRollbackIfFirstDeclarativeReleaseDidNotExecuteSuccessfully() throws Exception {
    // if after turning rollback FF on, first release does not save (for some failure before APPLY), skip rollback

    K8sLegacyRelease latestLegacyRelease = mock(K8sLegacyRelease.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = createRollbackConfigForInitTests(false);
    doReturn(latestLegacyRelease).when(legacyReleaseHistory).getLatestRelease();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isUseDeclarativeRollback()).isTrue();
    assertThat(rollbackHandlerConfig.isNoopRollBack()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackUsingImperativeHistoryWhenDeclarativeHistoryIsEmpty() throws Exception {
    // case of post prod rollback immediately after turning rollback FF on
    K8sLegacyRelease latestLegacyRelease = mock(K8sLegacyRelease.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = createRollbackConfigForInitTests(true);
    doReturn(latestLegacyRelease).when(legacyReleaseHistory).getLatestRelease();

    k8sRollingRollbackBaseHandler.init(rollbackHandlerConfig, "releaseName", logCallback);

    assertThat(rollbackHandlerConfig.isUseDeclarativeRollback()).isFalse();
    assertThat(rollbackHandlerConfig.getRelease()).isEqualTo(latestLegacyRelease);
    assertThat(rollbackHandlerConfig.getReleaseHistory()).isEqualTo(legacyReleaseHistory);
  }

  public K8sRollingRollbackHandlerConfig createRollbackConfigForInitTests(boolean setReleaseNumber) throws Exception {
    K8sLegacyRelease latestLegacyRelease = mock(K8sLegacyRelease.class);
    K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
    rollbackHandlerConfig.setUseDeclarativeRollback(true);

    if (setReleaseNumber) {
      rollbackHandlerConfig.setCurrentReleaseNumber(1);
    }

    K8sReleaseHandler legacyReleaseHandler = mock(K8sReleaseHandler.class);
    doReturn(legacyReleaseHandler).when(k8sTaskHelperBase).getReleaseHandler(false);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(true);
    doReturn(legacyReleaseHistory).when(legacyReleaseHandler).getReleaseHistory(any(), any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(true).when(releaseHistory).isEmpty();
    doReturn(false).when(legacyReleaseHistory).isEmpty();
    doReturn(latestLegacyRelease).when(legacyReleaseHistory).getLatestRelease();

    return rollbackHandlerConfig;
  }

  private List<KubernetesResource> getKubernetesResourcesFromFiles(List<String> fileNames) {
    List<KubernetesResource> resources = new ArrayList<>();
    fileNames.forEach(filename -> {
      URL url = this.getClass().getResource(filename);
      String fileContents = null;
      try {
        fileContents = Resources.toString(url, StandardCharsets.UTF_8);
      } catch (IOException e) {
        e.printStackTrace();
      }
      resources.add(processYaml(fileContents).get(0));
    });
    return resources;
  }
}
