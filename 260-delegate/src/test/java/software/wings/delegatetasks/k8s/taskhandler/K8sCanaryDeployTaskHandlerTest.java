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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.K8sCanaryBaseHandler;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesYamlException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
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
import lombok.SneakyThrows;
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
  @Mock K8sReleaseHandler releaseHandler;
  @InjectMocks private K8sCanaryDeployTaskHandler k8sCanaryDeployTaskHandler;

  @Mock private IK8sReleaseHistory releaseHistory;
  @Mock private IK8sRelease release;
  private KubernetesResource deployment;

  @Before
  public void setup() throws Exception {
    deployment = K8sTestHelper.deployment();

    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn(Mockito.mock(ExecutionLogCallback.class)).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(KubernetesConfig.builder().namespace("default").build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(), anyBoolean());
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(1).when(releaseHistory).getAndIncrementLastReleaseNumber();
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());
    doReturn(release).when(release).setReleaseData(anyList(), anyBoolean());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    K8sCanaryDeployTaskParameters canaryDeployTaskParams =
        K8sCanaryDeployTaskParameters.builder().skipDryRun(true).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(), anyBoolean()))
        .thenReturn(KubernetesConfig.builder().build());
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
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
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
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelperBase.updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any())).thenReturn(null);
    when(k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any())).thenReturn(null);

    k8sCanaryDeployTaskHandler.init(canaryDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).dryRunManifests(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifests(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanary() throws Exception {
    K8sCanaryDeployTaskParameters canaryDeployTaskParams = K8sCanaryDeployTaskParameters.builder()
                                                               .skipDryRun(false)
                                                               .instanceUnitType(COUNT)
                                                               .useDeclarativeRollback(true)
                                                               .instances(1)
                                                               .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    canaryHandlerConfig.setResources(kubernetesResources);
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    K8sRequestHandlerContext context = k8sCanaryDeployTaskHandler.getK8sRequestHandlerContext();

    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, context, delegateTaskParams, null, executionLogCallback, false);
    doReturn(1)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    boolean result =
        k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, canaryDeployTaskParams, executionLogCallback);

    assertThat(result).isTrue();
    verify(k8sCanaryBaseHandler, times(1))
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, context, 1, executionLogCallback);
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
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());

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
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    K8sCanaryDeployTaskHandler handler = spy(k8sCanaryDeployTaskHandler);
    K8sCanaryHandlerConfig canaryHandlerConfig = handler.getCanaryHandlerConfig();
    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(handler).prepareForCanary(any(), any(), any());
    doReturn(Arrays.asList(K8sPod.builder().build()))
        .when(k8sCanaryBaseHandler)
        .getAllPods(eq(canaryHandlerConfig), any(), anyLong());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());

    canaryHandlerConfig.setCanaryWorkload(deployment);
    canaryHandlerConfig.setResources(Collections.emptyList());
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    canaryHandlerConfig.setCurrentRelease(K8sLegacyRelease.builder().number(2).build());
    canaryHandlerConfig.setTargetInstances(3);
    canaryHandlerConfig.setManifestFilesDirectory("/manifest/file/dir");

    final K8sTaskExecutionResponse response =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    verify(k8sCanaryBaseHandler, times(1)).wrapUp(any(), any(), any());
    final K8sCanaryDeployResponse canaryDeployResponse = (K8sCanaryDeployResponse) response.getK8sTaskResponse();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(canaryDeployResponse.getCanaryWorkload()).isEqualTo("Deployment/nginx-deployment");
    assertThat(canaryDeployResponse.getCurrentInstances()).isEqualTo(3);
    assertThat(canaryDeployResponse.getReleaseNumber()).isEqualTo(2);
    assertThat(canaryDeployResponse.getK8sPodList()).hasSize(1);

    // status check fails
    doReturn(false).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    final K8sTaskExecutionResponse failureResponse =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .releaseName("release")
                                .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveRelease(canaryHandlerConfig);
    assertThat(failureResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);

    // apply manifests fails
    clearInvocations(k8sCanaryBaseHandler);
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());

    K8sTaskExecutionResponse taskExecutionResponse =
        handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .releaseName("release-Name")
                                .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    assertThat(taskExecutionResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveRelease(canaryHandlerConfig);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testInit() throws Exception {
    doReturn(Arrays.asList(deployment)).when(k8sTaskHelperBase).readManifests(any(), any());
    k8sCanaryDeployTaskHandler.init(K8sCanaryDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(asList(deployment), "default");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testInitException() throws Exception {
    doThrow(new RuntimeException())
        .when(k8sTaskHelper)
        .renderTemplate(any(K8sDelegateTaskParams.class), any(K8sDelegateManifestConfig.class), any(), any(), any(),
            any(), any(), any(K8sTaskParameters.class));
    final boolean success = k8sCanaryDeployTaskHandler.init(K8sCanaryDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), mock(ExecutionLogCallback.class));
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryCount() throws Exception {
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    K8sRequestHandlerContext context = k8sCanaryDeployTaskHandler.getK8sRequestHandlerContext();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployTaskParameters deployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                             .skipVersioningForAllK8sObjects(false)
                                                             .instanceUnitType(COUNT)
                                                             .instances(4)
                                                             .build();
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, context, delegateTaskParams, false, executionLogCallback, false);
    doReturn(1)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);

    k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, deployTaskParameters, executionLogCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, context, 4, executionLogCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryPercentage() throws Exception {
    Integer currentInstances = 4;
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryDeployTaskHandler.getCanaryHandlerConfig();
    K8sRequestHandlerContext context = k8sCanaryDeployTaskHandler.getK8sRequestHandlerContext();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployTaskParameters deployTaskParameters = K8sCanaryDeployTaskParameters.builder()
                                                             .skipVersioningForAllK8sObjects(false)
                                                             .instanceUnitType(PERCENTAGE)
                                                             .instances(70)
                                                             .build();
    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, context, delegateTaskParams, false, executionLogCallback, false);
    doReturn(currentInstances)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(canaryHandlerConfig, delegateTaskParams, executionLogCallback);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    doReturn(3).when(k8sTaskHelperBase).getTargetInstancesForCanary(70, currentInstances, executionLogCallback);

    k8sCanaryDeployTaskHandler.prepareForCanary(delegateTaskParams, deployTaskParameters, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).getTargetInstancesForCanary(70, currentInstances, executionLogCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, context, 3, executionLogCallback);
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
    handler.getCanaryHandlerConfig().setReleaseHistory(releaseHistory);
    handler.getCanaryHandlerConfig().setCurrentRelease(K8sLegacyRelease.builder().build());
    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(handler).prepareForCanary(any(), any(), any());
    doReturn(helmChartInfo)
        .when(k8sTaskHelper)
        .getHelmChartDetails(manifestConfig, Paths.get(".", MANIFEST_FILES_DIR).toString());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());

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
    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(handler).prepareForCanary(any(), any(), any());

    handler.getCanaryHandlerConfig().setCanaryWorkload(deployment);
    handler.getCanaryHandlerConfig().setResources(Collections.emptyList());
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(asList(K8sLegacyRelease.builder().number(2).build()));
    K8SLegacyReleaseHistory releaseHistory = K8SLegacyReleaseHistory.builder().releaseHistory(releaseHist).build();
    handler.getCanaryHandlerConfig().setReleaseHistory(releaseHistory);
    handler.getCanaryHandlerConfig().setCurrentRelease(releaseHist.getLatestRelease());
    handler.getCanaryHandlerConfig().setTargetInstances(3);

    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    // status check fails
    doReturn(false).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .releaseName("release-name-1")
                            .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveRelease(handler.getCanaryHandlerConfig());

    clearInvocations(k8sCanaryBaseHandler);
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    handler.executeTask(K8sCanaryDeployTaskParameters.builder()
                            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                            .releaseName("release-name-2")
                            .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    verify(k8sCanaryBaseHandler, times(1)).failAndSaveRelease(handler.getCanaryHandlerConfig());
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
    doThrow(thrownException).when(k8sCanaryBaseHandler).getAllPods(eq(canaryHandlerConfig), any(), anyLong());

    assertThatThrownBy(
        ()
            -> handler.executeTaskInternal(K8sCanaryDeployTaskParameters.builder()
                                               .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                               .releaseName("releaseName")
                                               .build(),
                K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build()))
        .isEqualTo(thrownException);
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
    verify(k8sTaskHelperBase, times(0)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
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
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    canaryHandlerConfig.setCurrentRelease(K8sLegacyRelease.builder().number(1).build());
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
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());

    K8sTaskExecutionResponse response =
        handler.executeTaskInternal(k8sCanaryDeployTaskParameters, k8sDelegateTaskParams);

    verify(handler, times(0)).init(eq(k8sCanaryDeployTaskParameters), eq(k8sDelegateTaskParams), any());
    verify(handler, times(1)).prepareForCanary(any(), any(), any());
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
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

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDontPopulateCanaryWorkloadsIfNotUpdated() {
    final K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters =
        K8sCanaryDeployTaskParameters.builder()
            .inheritManifests(false)
            .k8sTaskType(K8sTaskType.CANARY_DEPLOY)
            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
            .k8sClusterConfig(K8sClusterConfig.builder().build())
            .releaseName("test-release")
            .instanceUnitType(COUNT)
            .instances(1)
            .valuesYamlList(Collections.emptyList())
            .skipVersioningForAllK8sObjects(false)
            .useDeclarativeRollback(
                true) // hack to avoid class cast exception at
                      // software/wings/delegatetasks/k8s/taskhandler/K8sCanaryDeployTaskHandler.java:347
            .build();
    final List<FileData> renderedManifestFiles = List.of(FileData.builder().fileName("test1").build());
    final List<KubernetesResource> renderedResources = ManifestHelper.processYaml(DEPLOYMENT_YAML);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(renderedResources);

    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    doReturn(renderedManifestFiles)
        .when(k8sTaskHelper)
        .renderTemplate(any(K8sDelegateTaskParams.class), any(K8sDelegateManifestConfig.class), anyString(), anyList(),
            anyString(), anyString(), any(ExecutionLogCallback.class), eq(k8sCanaryDeployTaskParameters));
    doReturn(renderedResources)
        .when(k8sTaskHelperBase)
        .readManifests(eq(renderedManifestFiles), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), anyString(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(any(Kubectl.class), eq(renderedResources), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean());
    doAnswer(answer -> {
      K8sCanaryHandlerConfig canaryHandlerConfig = answer.getArgument(0);
      // A hacky solution to avoid big test configuration and dependencies
      canaryHandlerConfig.setCanaryWorkload(canaryHandlerConfig.getResources().get(0));
      return true;
    })
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(any(K8sCanaryHandlerConfig.class), any(K8sRequestHandlerContext.class),
            any(K8sDelegateTaskParams.class), anyBoolean(), any(ExecutionLogCallback.class), anyBoolean());
    doAnswer(answer -> answer.getArgument(0))
        .when(k8sCanaryBaseHandler)
        .appendSecretAndConfigMapNamesToCanaryWorkloads(anyString(), anyList());
    // This method should update the canary workload name. If this does not happen then workload name will match primary
    // workload name
    doThrow(new KubernetesYamlException("Something went wrong"))
        .when(k8sCanaryBaseHandler)
        .updateTargetInstances(
            any(K8sCanaryHandlerConfig.class), any(K8sRequestHandlerContext.class), anyInt(), any(LogCallback.class));

    K8sTaskExecutionResponse response = k8sCanaryDeployTaskHandler.executeTask(
        k8sCanaryDeployTaskParameters, K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    K8sCanaryDeployResponse canaryDeployResponse = (K8sCanaryDeployResponse) response.getK8sTaskResponse();

    // Null value is still valid use case. It's an issue only if workload name wasn't updated
    if (canaryDeployResponse.getCanaryWorkload() != null) {
      assertThat(canaryDeployResponse.getCanaryWorkload())
          .endsWith(K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR);
    }
  }
}
