/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.delegatetasks.k8s.K8sTestConstants.CONFIG_MAP_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.SECRET_YAML;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.K8sCanaryBaseHandler;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.delegatetasks.k8s.K8sTestHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sCanaryDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sCanaryDeployTaskHandlerTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sCanaryBaseHandler k8sCanaryBaseHandler;
  @InjectMocks private K8sCanaryDeployTaskHandler k8sCanaryDeployTaskHandler;

  private ReleaseHistory releaseHistory;
  private KubernetesResource deployment;

  @Before
  public void setup() throws Exception {
    releaseHistory = ReleaseHistory.createNew();
    deployment = K8sTestHelper.deployment();

    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), anyString(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    doReturn(Mockito.mock(ExecutionLogCallback.class))
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sCanaryDeployTaskParameters.class), anyString());
    doReturn(KubernetesConfig.builder().namespace("default").build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    K8sCanaryDeployTaskParameters canaryDeployTaskParams =
        K8sCanaryDeployTaskParameters.builder().skipDryRun(true).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(KubernetesConfig.builder().build());
    when(k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(any(), any())).thenReturn(null);
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(Collections.emptyList());
    when(k8sTaskHelperBase.updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any())).thenReturn(null);
    when(k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any())).thenReturn(null);

    k8sCanaryDeployTaskHandler.init(canaryDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(0)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1)).updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifests(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class), eq(false));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsNotSkipped() throws Exception {
    K8sCanaryDeployTaskParameters canaryDeployTaskParams =
        K8sCanaryDeployTaskParameters.builder().skipDryRun(false).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(Collections.emptyList());
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    when(k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(any(), any())).thenReturn(null);
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelperBase.updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any())).thenReturn(null);
    when(k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any())).thenReturn(null);

    k8sCanaryDeployTaskHandler.init(canaryDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1)).updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifests(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class), eq(false));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanary() throws Exception {
    K8sCanaryDeployTaskParameters canaryDeployTaskParams =
        K8sCanaryDeployTaskParameters.builder().skipDryRun(false).instanceUnitType(COUNT).instances(1).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    canaryHandlerConfig.setResources(kubernetesResources);
    canaryHandlerConfig.setReleaseHistory(ReleaseHistory.createNew());

    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, delegateTaskParams, null, executionLogCallback, false, false);
    doReturn(1)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    boolean result =
        k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, canaryDeployTaskParams, executionLogCallback);

    assertThat(result).isTrue();
    verify(k8sTaskHelperBase, times(1)).getReleaseHistoryDataFromConfigMap(any(), any());
    verify(k8sCanaryBaseHandler, times(1))
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, 1, executionLogCallback);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sCanaryDeployTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void failureInFetchingManifestFiles() {
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    doReturn(false)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), anyString(), any(ExecutionLogCallback.class), anyLong());

    K8sTaskExecutionResponse response;
    response = k8sCanaryDeployTaskHandler.executeTask(
        K8sCanaryDeployTaskParameters.builder()
            .k8sDelegateManifestConfig(
                K8sDelegateManifestConfig.builder().gitFileConfig(GitFileConfig.builder().build()).build())
            .releaseName("release-name")
            .build(),
        K8sDelegateTaskParams.builder().workingDirectory(".").build());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getK8sTaskResponse()).isNotNull();

    canaryHandlerConfig.setCanaryWorkload(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().namespace("default").name("canary").kind("Deployment").build())
            .build());

    response = k8sCanaryDeployTaskHandler.executeTask(
        K8sCanaryDeployTaskParameters.builder()
            .k8sDelegateManifestConfig(
                K8sDelegateManifestConfig.builder().gitFileConfig(GitFileConfig.builder().build()).build())
            .releaseName("release-name")
            .build(),
        K8sDelegateTaskParams.builder().workingDirectory(".").build());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(((K8sCanaryDeployResponse) response.getK8sTaskResponse()).getCanaryWorkload())
        .isEqualTo("default/Deployment/canary");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    doReturn(true).when(handler).init(
        any(K8sCanaryDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForCanary(
        any(K8sDelegateTaskParams.class), any(K8sCanaryDeployTaskParameters.class), any(ExecutionLogCallback.class));
    doReturn(Arrays.asList(K8sPod.builder().build()))
        .when(k8sCanaryBaseHandler)
        .getAllPods(eq(canaryHandlerConfig), anyString(), anyLong());

    releaseHistory.setReleases(asList(Release.builder().number(2).build()));
    canaryHandlerConfig.setCanaryWorkload(deployment);
    canaryHandlerConfig.setResources(Collections.emptyList());
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    canaryHandlerConfig.setCurrentRelease(releaseHistory.getLatestRelease());
    canaryHandlerConfig.setTargetInstances(3);

    final K8sTaskExecutionResponse response =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .build(),
            K8sDelegateTaskParams.builder().build());
    verify(k8sCanaryBaseHandler, times(1))
        .wrapUp(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    verify(k8sTaskHelperBase, times(1))
        .saveReleaseHistoryInConfigMap(any(KubernetesConfig.class), anyString(), anyString());
    final K8sCanaryDeployResponse canaryDeployResponse = (K8sCanaryDeployResponse) response.getK8sTaskResponse();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(canaryDeployResponse.getCanaryWorkload()).isEqualTo("Deployment/nginx-deployment");
    assertThat(canaryDeployResponse.getCurrentInstances()).isEqualTo(3);
    assertThat(canaryDeployResponse.getReleaseNumber()).isEqualTo(2);
    assertThat(canaryDeployResponse.getK8sPodList()).hasSize(1);

    // status check fails
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    final K8sTaskExecutionResponse failureResponse =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .releaseName("release")
                                .build(),
            K8sDelegateTaskParams.builder().build());
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveKubernetesRelease(canaryHandlerConfig, "release");
    assertThat(failureResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);

    // apply manifests fails
    doReturn(false)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean());

    K8sTaskExecutionResponse taskExecutionResponse =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .releaseName("release-Name")
                                .build(),
            K8sDelegateTaskParams.builder().build());
    assertThat(taskExecutionResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sCanaryBaseHandler).failAndSaveKubernetesRelease(canaryHandlerConfig, "release-Name");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testInit() throws Exception {
    doReturn(Arrays.asList(deployment)).when(k8sTaskHelperBase).readManifests(anyList(), any());
    k8sCanaryDeployTaskHandler.init(K8sCanaryDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(anyString(), any(ExecutionLogCallback.class));
    verify(k8sTaskHelper, times(1))
        .renderTemplate(any(K8sDelegateTaskParams.class), any(K8sDelegateManifestConfig.class), anyString(), anyList(),
            anyString(), anyString(), any(), any(K8sTaskParameters.class));
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(asList(deployment), "default");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testInitException() throws Exception {
    doThrow(new RuntimeException())
        .when(k8sTaskHelper)
        .renderTemplate(any(K8sDelegateTaskParams.class), any(K8sDelegateManifestConfig.class), anyString(), anyList(),
            anyString(), anyString(), any(), any(K8sTaskParameters.class));
    final boolean success = k8sCanaryDeployTaskHandler.init(K8sCanaryDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), mock(ExecutionLogCallback.class));
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryCount() throws Exception {
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployTaskParameters deployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                             .skipVersioningForAllK8sObjects(false)
                                                             .instanceUnitType(COUNT)
                                                             .instances(4)
                                                             .build();
    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, delegateTaskParams, false, executionLogCallback, false, false);
    doReturn(1)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);

    k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, deployTaskParameters, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).getReleaseHistoryDataFromConfigMap(any(), any());
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, 4, executionLogCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryPercentage() throws Exception {
    Integer currentInstances = 4;
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployTaskParameters deployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                             .skipVersioningForAllK8sObjects(false)
                                                             .instanceUnitType(PERCENTAGE)
                                                             .instances(70)
                                                             .build();
    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, delegateTaskParams, false, executionLogCallback, false, false);
    doReturn(currentInstances)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    doReturn(3).when(k8sTaskHelperBase).getTargetInstancesForCanary(70, currentInstances, executionLogCallback);

    k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, deployTaskParameters, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).getReleaseHistoryDataFromConfigMap(any(), any());
    verify(k8sTaskHelperBase, times(1)).getTargetInstancesForCanary(70, currentInstances, executionLogCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, 3, executionLogCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAssignHelmChartInfo() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    handler.getCanaryHandlerConfig().setCanaryWorkload(ManifestHelper.processYaml(DEPLOYMENT_YAML).get(0));
    handler.getCanaryHandlerConfig().setReleaseHistory(ReleaseHistory.createNew());
    handler.getCanaryHandlerConfig().setCurrentRelease(Release.builder().build());
    doReturn(true).when(handler).init(
        any(K8sCanaryDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForCanary(
        any(K8sDelegateTaskParams.class), any(K8sCanaryDeployTaskParameters.class), any(ExecutionLogCallback.class));
    doReturn(helmChartInfo)
        .when(k8sTaskHelper)
        .getHelmChartDetails(manifestConfig, Paths.get(".", MANIFEST_FILES_DIR).toString());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));

    K8sTaskExecutionResponse response = handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                                                .skipDryRun(true)
                                                                .k8sDelegateManifestConfig(manifestConfig)
                                                                .releaseName("release-name")
                                                                .build(),
        K8sDelegateTaskParams.builder().workingDirectory(".").build());

    K8sCanaryDeployResponse canaryDeployResponse = (K8sCanaryDeployResponse) response.getK8sTaskResponse();
    assertThat(canaryDeployResponse.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldSaveReleaseHistoryUsingK8sClient() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    doReturn(true).when(handler).init(
        any(K8sCanaryDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForCanary(
        any(K8sDelegateTaskParams.class), any(K8sCanaryDeployTaskParameters.class), any(ExecutionLogCallback.class));

    handler.getCanaryHandlerConfig().setCanaryWorkload(deployment);
    handler.getCanaryHandlerConfig().setResources(Collections.emptyList());
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(asList(Release.builder().number(2).build()));
    handler.getCanaryHandlerConfig().setReleaseHistory(releaseHist);
    handler.getCanaryHandlerConfig().setCurrentRelease(releaseHist.getLatestRelease());
    handler.getCanaryHandlerConfig().setTargetInstances(3);

    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .build(),
        K8sDelegateTaskParams.builder().build());
    verify(k8sTaskHelperBase, times(1))
        .saveReleaseHistoryInConfigMap(any(KubernetesConfig.class), anyString(), anyString());

    // status check fails
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .releaseName("release-name-1")
                            .build(),
        K8sDelegateTaskParams.builder().build());
    verify(k8sCanaryBaseHandler, times(1))
        .failAndSaveKubernetesRelease(handler.getCanaryHandlerConfig(), "release-name-1");

    doReturn(false)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean());
    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .releaseName("release-name-2")
                            .build(),
        K8sDelegateTaskParams.builder().build());
    verify(k8sCanaryBaseHandler, times(1))
        .failAndSaveKubernetesRelease(handler.getCanaryHandlerConfig(), "release-name-2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldCatchGetAllPodsException() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    canaryHandlerConfig.setCanaryWorkload(deployment);
    InvalidRequestException thrownException = new InvalidRequestException("Failed to get all pods");

    doReturn(true).when(handler).init(
        any(K8sCanaryDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForCanary(
        any(K8sDelegateTaskParams.class), any(K8sCanaryDeployTaskParameters.class), any(ExecutionLogCallback.class));
    doThrow(thrownException).when(k8sCanaryBaseHandler).getAllPods(eq(canaryHandlerConfig), anyString(), anyLong());

    assertThatThrownBy(
        ()
            -> handler.executeTaskInternal(K8sCanaryDeployTaskParameters.builder()
                                               .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                               .releaseName("releaseName")
                                               .build(),
                K8sDelegateTaskParams.builder().build()))
        .isEqualTo(thrownException);
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveKubernetesRelease(canaryHandlerConfig, "releaseName");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalExportManifests() throws Exception {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));

    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    canaryHandlerConfig.setCanaryWorkload(deployment);
    canaryHandlerConfig.setResources(kubernetesResources);
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory("./working-dir")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("kubeconfig")
                                                      .build();
    K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters =
        K8sCanaryDeployTaskParameters.builder()
            .exportManifests(true)
            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
            .k8sTaskType(K8sTaskType.CANARY_DEPLOY)
            .build();
    doReturn(true).when(handler).init(
        any(K8sCanaryDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    K8sTaskExecutionResponse response =
        handler.executeTaskInternal(k8sCanaryDeployTaskParameters, k8sDelegateTaskParams);

    verify(handler, times(1)).init(eq(k8sCanaryDeployTaskParameters), eq(k8sDelegateTaskParams), any());
    verify(handler, times(0)).prepareForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(0)).applyManifests(any(), any(), any(), any(), anyBoolean());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((K8sCanaryDeployResponse) response.getK8sTaskResponse()).getResources()).isEqualTo(kubernetesResources);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalInheritManifests() throws Exception {
    List<KubernetesResource> inheritedKubernetesResources = new ArrayList<>();
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    canaryHandlerConfig.setCanaryWorkload(deployment);
    canaryHandlerConfig.setReleaseHistory(ReleaseHistory.builder().build());
    canaryHandlerConfig.setCurrentRelease(Release.builder().number(1).build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory("./working-dir")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("kubeconfig")
                                                      .build();
    K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                                      .inheritManifests(true)
                                                                      .kubernetesResources(inheritedKubernetesResources)
                                                                      .k8sTaskType(K8sTaskType.CANARY_DEPLOY)
                                                                      .build();

    when(k8sTaskHelperBase.getReleaseHistoryData(any(), any())).thenReturn(null);
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      K8sHandlerConfig k8sRollingHandlerConfig = (K8sHandlerConfig) args[3];
      k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
      k8sRollingHandlerConfig.setClient(Kubectl.client("", ""));
      k8sRollingHandlerConfig.setResources(inheritedKubernetesResources);
      return true;
    })
        .when(k8sTaskHelper)
        .restore(any(), any(), any(), any(), any());
    doReturn(true).when(handler).prepareForCanary(any(), any(), any());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());

    K8sTaskExecutionResponse response =
        handler.executeTaskInternal(k8sCanaryDeployTaskParameters, k8sDelegateTaskParams);

    verify(handler, times(0)).init(eq(k8sCanaryDeployTaskParameters), eq(k8sDelegateTaskParams), any());
    verify(handler, times(1)).prepareForCanary(any(), any(), any());
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).applyManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1)).doStatusCheck(any(), any(), any(), any());
    verify(k8sCanaryBaseHandler, times(1)).wrapUp(any(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalInheritManifestsRestoreFailed() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    canaryHandlerConfig.setCanaryWorkload(deployment);
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory("./working-dir")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("kubeconfig")
                                                      .build();
    K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                                      .inheritManifests(true)
                                                                      .kubernetesResources(null)
                                                                      .k8sTaskType(K8sTaskType.CANARY_DEPLOY)
                                                                      .build();
    doReturn(false).when(k8sTaskHelper).restore(any(), any(), any(), any(), any());

    K8sTaskExecutionResponse response =
        handler.executeTaskInternal(k8sCanaryDeployTaskParameters, k8sDelegateTaskParams);
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
