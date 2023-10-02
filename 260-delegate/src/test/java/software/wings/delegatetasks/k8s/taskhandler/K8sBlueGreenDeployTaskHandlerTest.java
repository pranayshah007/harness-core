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
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.delegatetasks.k8s.K8sTestConstants.CONFIG_MAP_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.DAEMON_SET_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.PRIMARY_SERVICE_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.SECRET_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.SERVICE_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.STAGE_SERVICE_YAML;
import static software.wings.delegatetasks.k8s.K8sTestHelper.configMap;
import static software.wings.delegatetasks.k8s.K8sTestHelper.deployment;
import static software.wings.delegatetasks.k8s.K8sTestHelper.primaryService;
import static software.wings.delegatetasks.k8s.K8sTestHelper.service;
import static software.wings.delegatetasks.k8s.K8sTestHelper.stageService;
import static software.wings.utils.WingsTestConstants.LONG_TIMEOUT_INTERVAL;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.K8sBGBaseHandler;
import io.harness.delegate.k8s.beans.K8sBlueGreenHandlerConfig;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.request.K8sBlueGreenDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.response.K8sBlueGreenDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sBlueGreenDeployTaskHandlerTest extends CategoryTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock public K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private IK8sReleaseHistory releaseHistory;
  @Mock private Kubectl client;
  @Mock private K8sBGBaseHandler mockedK8sBGBaseHandler;
  @Mock private K8sReleaseHandler releaseHandler;
  @Mock private IK8sRelease release;

  @InjectMocks private K8sBGBaseHandler k8sBGBaseHandler;
  @InjectMocks private K8sBlueGreenDeployTaskHandler k8sBlueGreenDeployTaskHandler;

  private K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig;
  private K8sRequestHandlerContext k8sRequestHandlerContext;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    initializeLogging();
    k8sBlueGreenHandlerConfig = new K8sBlueGreenHandlerConfig();
    k8sBlueGreenHandlerConfig.setReleaseHistory(releaseHistory);
    k8sBlueGreenHandlerConfig.setClient(client);
    on(k8sBlueGreenDeployTaskHandler).set("k8sBlueGreenHandlerConfig", k8sBlueGreenHandlerConfig);

    k8sRequestHandlerContext = new K8sRequestHandlerContext();
    on(k8sBlueGreenDeployTaskHandler).set("k8sRequestHandlerContext", k8sRequestHandlerContext);

    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(10).when(releaseHistory).getAndIncrementLastReleaseNumber();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void executeInternalSkeleton() throws Exception {
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(true).when(spyHandler).init(any(), any(), any());
    doReturn(true).when(spyHandler).prepareForBlueGreen(any(), any(), any());
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap())));
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn("latest-rev").when(k8sTaskHelperBase).getLatestRevision(any(), eq(deployment().getResourceId()), any());

    spyHandler.executeTaskInternal(K8sBlueGreenDeployTaskParameters.builder()
                                       .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
                                       .useDeclarativeRollback(true)
                                       .build(),
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());

    ArgumentCaptor<K8sBlueGreenDeployResponse> captor = ArgumentCaptor.forClass(K8sBlueGreenDeployResponse.class);

    verify(k8sTaskHelper, times(1)).getK8sTaskExecutionResponse(captor.capture(), eq(SUCCESS));
    final K8sBlueGreenDeployResponse k8sTaskResponse = captor.getValue();
    assertThat(k8sTaskResponse.getPrimaryServiceName()).isEqualTo(primaryService().getResourceId().getName());
    assertThat(k8sTaskResponse.getStageServiceName()).isEqualTo(stageService().getResourceId().getName());
    assertThat(k8sTaskResponse.getReleaseNumber()).isEqualTo(0);

    K8sBlueGreenDeployTaskParameters deployTaskParams = K8sBlueGreenDeployTaskParameters.builder()
                                                            .releaseName("releaseName-statusCheck")
                                                            .useDeclarativeRollback(true)
                                                            .build();
    K8sDelegateTaskParams taskParams = K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build();

    doReturn(false).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    K8sTaskExecutionResponse response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(2))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-statusCheck"));

    deployTaskParams.setReleaseName("releaseName-apply");
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(1))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-apply"));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void executeInternalSkeletonWithLegacyReleaseImpl() throws Exception {
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(true).when(spyHandler).init(any(), any(), any());
    doReturn(true).when(spyHandler).prepareForBlueGreen(any(), any(), any());
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(K8sLegacyRelease.builder().build());
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap())));
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn("latest-rev").when(k8sTaskHelperBase).getLatestRevision(any(), eq(deployment().getResourceId()), any());

    spyHandler.executeTaskInternal(K8sBlueGreenDeployTaskParameters.builder()
                                       .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
                                       .useDeclarativeRollback(false)
                                       .build(),
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());

    ArgumentCaptor<K8sBlueGreenDeployResponse> captor = ArgumentCaptor.forClass(K8sBlueGreenDeployResponse.class);

    verify(k8sTaskHelper, times(1)).getK8sTaskExecutionResponse(captor.capture(), eq(SUCCESS));
    final K8sBlueGreenDeployResponse k8sTaskResponse = captor.getValue();
    assertThat(k8sTaskResponse.getPrimaryServiceName()).isEqualTo(primaryService().getResourceId().getName());
    assertThat(k8sTaskResponse.getStageServiceName()).isEqualTo(stageService().getResourceId().getName());
    assertThat(k8sTaskResponse.getReleaseNumber()).isEqualTo(0);

    K8sBlueGreenDeployTaskParameters deployTaskParams =
        K8sBlueGreenDeployTaskParameters.builder().releaseName("releaseName-statusCheck").build();
    K8sDelegateTaskParams taskParams = K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build();

    doReturn(false).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    K8sTaskExecutionResponse response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(2))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-statusCheck"));

    deployTaskParams.setReleaseName("releaseName-apply");
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(1))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-apply"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeInternalInheritManifests() throws Exception {
    List<KubernetesResource> inheritedKubernetesResources = new ArrayList<>();
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters =
        K8sBlueGreenDeployTaskParameters.builder()
            .inheritManifests(true)
            .kubernetesResources(inheritedKubernetesResources)
            .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
            .useDeclarativeRollback(true)
            .build();
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      K8sHandlerConfig k8sHandlerConfig = (K8sHandlerConfig) args[3];
      k8sHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
      k8sHandlerConfig.setClient(client);
      k8sHandlerConfig.setResources(inheritedKubernetesResources);
      return true;
    })
        .when(k8sTaskHelper)
        .restore(eq(k8sBlueGreenDeployTaskParameters.getKubernetesResources()), any(), any(), any(),
            eq(k8sRequestHandlerContext), any());
    doReturn(executionLogCallback)
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sBlueGreenDeployTaskParameters.class), any());
    doReturn(true)
        .when(spyHandler)
        .prepareForBlueGreen(any(K8sBlueGreenDeployTaskParameters.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    doReturn("latest-rev")
        .when(k8sTaskHelperBase)
        .getLatestRevision(any(Kubectl.class), eq(deployment().getResourceId()), any(K8sDelegateTaskParams.class));

    spyHandler.executeTaskInternal(k8sBlueGreenDeployTaskParameters,
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());

    ArgumentCaptor<K8sBlueGreenDeployResponse> captor = ArgumentCaptor.forClass(K8sBlueGreenDeployResponse.class);

    verify(spyHandler, times(0)).init(any(), any(), any());
    verify(k8sTaskHelper, times(1)).getK8sTaskExecutionResponse(captor.capture(), eq(SUCCESS));
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), eq(k8sRequestHandlerContext), any());
    verify(k8sTaskHelperBase, times(1)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    final K8sBlueGreenDeployResponse k8sTaskResponse = captor.getValue();
    assertThat(k8sTaskResponse.getPrimaryServiceName()).isEqualTo(primaryService().getResourceId().getName());
    assertThat(k8sTaskResponse.getStageServiceName()).isEqualTo(stageService().getResourceId().getName());
    assertThat(k8sTaskResponse.getReleaseNumber()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeInternalInheritManifestsRestoreFailed() throws Exception {
    List<KubernetesResource> inheritedKubernetesResources = new ArrayList<>();
    K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters =
        K8sBlueGreenDeployTaskParameters.builder()
            .inheritManifests(true)
            .kubernetesResources(inheritedKubernetesResources)
            .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
            .build();
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(false)
        .when(k8sTaskHelper)
        .restore(eq(k8sBlueGreenDeployTaskParameters.getKubernetesResources()), any(), any(), any(),
            eq(k8sRequestHandlerContext), any());
    doReturn(executionLogCallback)
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sBlueGreenDeployTaskParameters.class), any());

    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());

    K8sTaskExecutionResponse k8sTaskExecutionResponse = spyHandler.executeTaskInternal(k8sBlueGreenDeployTaskParameters,
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());
    assertThat(k8sTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), eq(k8sRequestHandlerContext), any());
    verify(k8sTaskHelperBase, times(0)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeInternalExportManifests() throws Exception {
    List<KubernetesResource> kubernetesResources =
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap()));
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(true).when(spyHandler).init(any(), any(), any());
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(kubernetesResources);
    doReturn("latest-rev").when(k8sTaskHelperBase).getLatestRevision(any(), eq(deployment().getResourceId()), any());

    K8sTaskExecutionResponse response = spyHandler.executeTaskInternal(K8sBlueGreenDeployTaskParameters.builder()
                                                                           .exportManifests(true)
                                                                           .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
                                                                           .build(),
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());

    verify(k8sTaskHelper, times(0)).restore(any(), any(), any(), any(), eq(k8sRequestHandlerContext), any());
    verify(k8sTaskHelperBase, times(0)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    verify(spyHandler, times(1)).init(any(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(((K8sBlueGreenDeployResponse) response.getK8sTaskResponse()).getResources())
        .isEqualTo(kubernetesResources);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    K8sBlueGreenDeployTaskParameters blueGreenDeployTaskParams =
        K8sBlueGreenDeployTaskParameters.builder().skipDryRun(true).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(emptyList());

    k8sBlueGreenDeployTaskHandler.init(blueGreenDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(0)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1)).readManifests(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsNotSkipped() throws Exception {
    K8sBlueGreenDeployTaskParameters blueGreenDeployTaskParams =
        K8sBlueGreenDeployTaskParameters.builder().skipDryRun(false).build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(emptyList());

    k8sBlueGreenDeployTaskHandler.init(blueGreenDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).dryRunManifests(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifests(any(), any());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryColorFromService() {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<KubernetesResource> resources = new ArrayList<>();
    resources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    resources.addAll(ManifestHelper.processYaml(PRIMARY_SERVICE_YAML));
    resources.addAll(ManifestHelper.processYaml(STAGE_SERVICE_YAML));
    k8sBlueGreenHandlerConfig.setResources(resources);
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);
    V1Service primaryService = new V1ServiceBuilder()
                                   .withNewSpec()
                                   .withSelector(ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorGreen))
                                   .endSpec()
                                   .build();
    V1Service stageService = new V1ServiceBuilder()
                                 .withNewSpec()
                                 .withSelector(ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue))
                                 .endSpec()
                                 .build();

    when(kubernetesContainerService.getService(null, "primary-service")).thenReturn(primaryService);
    when(kubernetesContainerService.getService(null, "stage-service")).thenReturn(stageService);

    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().build(), delegateTaskParams, executionLogCallback);
    K8sBlueGreenHandlerConfig finalK8sBlueGreenHandlerConfig =
        on(k8sBlueGreenDeployTaskHandler).get("k8sBlueGreenHandlerConfig");
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorGreen);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMissingLabelInServiceUsing() {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(SERVICE_YAML));

    V1Service service = new V1ServiceBuilder()
                            .withApiVersion("v1")
                            .withNewMetadata()
                            .withName("servicename")
                            .endMetadata()
                            .withNewSpec()
                            .withType("LoadBalancer")
                            .addNewPort()
                            .withPort(80)
                            .endPort()
                            .withClusterIP("1.2.3.4")
                            .endSpec()
                            .withNewStatus()
                            .endStatus()
                            .build();

    k8sBlueGreenHandlerConfig.setResources(kubernetesResources);
    k8sBlueGreenHandlerConfig.setReleaseHistory(releaseHistory);
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);

    when(kubernetesContainerService.getService(null, "servicename")).thenReturn(service);

    boolean result = k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().build(), delegateTaskParams, executionLogCallback);
    assertThat(result).isFalse();

    verify(kubernetesContainerService, times(2)).getService(any(), any());
    verify(executionLogCallback, times(1))
        .saveExecutionLog(
            "Found conflicting service [servicename] in the cluster. For blue/green deployment, the label [harness.io/color] is required in service selector. Delete this existing service to proceed",
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSupportedWorkloadsInBgWorkflow() {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DAEMON_SET_YAML));

    k8sBlueGreenHandlerConfig.setResources(kubernetesResources);

    boolean result = k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().build(), delegateTaskParams, executionLogCallback);
    assertThat(result).isFalse();

    verify(executionLogCallback, times(1))
        .saveExecutionLog(
            "\nNo workload found in the Manifests. Can't do  Blue/Green Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Blue/Green workflow type.",
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepareIfOnlyPrimaryServiceGiven() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(primaryService(), deployment())));
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);

    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), executionLogCallback);

    K8sBlueGreenHandlerConfig finalK8sBlueGreenHandlerConfig =
        on(k8sBlueGreenDeployTaskHandler).get("k8sBlueGreenHandlerConfig");

    assertThat(finalK8sBlueGreenHandlerConfig.getResources()).hasSize(3);
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService())
        .isNotEqualTo(k8sBlueGreenHandlerConfig.getStageService());
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService().getResourceId().getName()).endsWith("-stage");
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorDefault);
    assertThat(finalK8sBlueGreenHandlerConfig.getStageColor()).isEqualTo(HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepareIfOnlyStageServiceGiven() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(stageService(), deployment())));
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);
    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), executionLogCallback);

    K8sBlueGreenHandlerConfig finalK8sBlueGreenHandlerConfig =
        on(k8sBlueGreenDeployTaskHandler).get("k8sBlueGreenHandlerConfig");

    assertThat(finalK8sBlueGreenHandlerConfig.getResources()).hasSize(2);
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService()).isNotNull();
    assert finalK8sBlueGreenHandlerConfig.getStageService() == finalK8sBlueGreenHandlerConfig.getPrimaryService();
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorDefault);
    assertThat(finalK8sBlueGreenHandlerConfig.getStageColor()).isEqualTo(HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepareIfPrimarySecondaryServiceNotGiven() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(service(), deployment())));
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);

    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), executionLogCallback);

    K8sBlueGreenHandlerConfig finalK8sBlueGreenHandlerConfig =
        on(k8sBlueGreenDeployTaskHandler).get("k8sBlueGreenHandlerConfig");

    assertThat(finalK8sBlueGreenHandlerConfig.getResources()).hasSize(3);
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService())
        .isNotEqualTo(finalK8sBlueGreenHandlerConfig.getStageService());
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService().getResourceId().getName()).endsWith("-stage");
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorDefault);
    assertThat(finalK8sBlueGreenHandlerConfig.getStageColor()).isEqualTo(HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepareIfPrimaryServiceExistsInCluster() throws IOException {
    V1Service clusterPrimary = new V1Service();
    V1ServiceSpec spec = new V1ServiceSpec();
    spec.setSelector(ImmutableMap.of(HarnessLabels.color, "blue"));
    clusterPrimary.setSpec(spec);
    doReturn(clusterPrimary).when(kubernetesContainerService).getService(any(), any());
    K8sRelease release = K8sRelease.builder().releaseSecret(new V1Secret()).build();
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());
    List resources = new ArrayList<>(asList(primaryService(), stageService(), deployment()));
    k8sBlueGreenHandlerConfig.setResources(resources);
    k8sBlueGreenHandlerConfig.setReleaseName("release-name");
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);
    k8sRequestHandlerContext.setResources(resources);

    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().useDeclarativeRollback(true).build(),
        K8sDelegateTaskParams.builder().build(), executionLogCallback);

    K8sBlueGreenHandlerConfig finalK8sBlueGreenHandlerConfig =
        on(k8sBlueGreenDeployTaskHandler).get("k8sBlueGreenHandlerConfig");

    assertThat(finalK8sBlueGreenHandlerConfig.getResources()).hasSize(3);
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getStageService()).isNotNull();
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryService())
        .isNotEqualTo(finalK8sBlueGreenHandlerConfig.getStageService());
    assertThat(finalK8sBlueGreenHandlerConfig.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorBlue);
    assertThat(finalK8sBlueGreenHandlerConfig.getStageColor()).isEqualTo(HarnessLabelValues.colorGreen);
    assertThat(finalK8sBlueGreenHandlerConfig.getManagedWorkload().getResourceId().getName()).endsWith("-green");
    assertThat(
        ((Map) finalK8sBlueGreenHandlerConfig.getPrimaryService().getField("spec.selector")).get("harness.io/color"))
        .isEqualTo("blue");

    assertThat(
        ((Map) finalK8sBlueGreenHandlerConfig.getStageService().getField("spec.selector")).get("harness.io/color"))
        .isEqualTo("green");

    assertThat(((Map) finalK8sBlueGreenHandlerConfig.getManagedWorkload().getField("spec.selector.matchLabels"))
                   .get("harness.io/color"))
        .isEqualTo("green");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void moreThan1ServiceInManifest() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(service(), service(), deployment())));

    final boolean success =
        k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
            K8sDelegateTaskParams.builder().build(), executionLogCallback);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeast(1)).saveExecutionLog(captor.capture(), eq(ERROR), eq(FAILURE));
    assertThat(success).isFalse();
    assertThat(captor.getValue()).contains("Could not locate a Primary Service in Manifests");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void noServiceInManifests() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(deployment())));

    final boolean success =
        k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
            K8sDelegateTaskParams.builder().build(), executionLogCallback);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeast(1)).saveExecutionLog(captor.capture(), eq(ERROR), eq(FAILURE));
    assertThat(success).isFalse();
    assertThat(captor.getValue()).contains("No service is found in manifests");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void moreThan1Workload() throws IOException {
    k8sBlueGreenHandlerConfig.setResources(new ArrayList<>(asList(deployment(), deployment())));
    assertThat(k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(K8sBlueGreenDeployTaskParameters.builder().build(),
                   K8sDelegateTaskParams.builder().build(), executionLogCallback))
        .isFalse();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeast(1)).saveExecutionLog(captor.capture(), eq(ERROR), eq(FAILURE));
    assertThat(captor.getValue()).contains("There are multiple workloads in the Service Manifests you are deploying");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testOnlyOneWorkloadSupportedInBgWorkflow() {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));

    k8sBlueGreenHandlerConfig.setResources(kubernetesResources);

    boolean result = k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().build(), delegateTaskParams, executionLogCallback);
    assertThat(result).isFalse();

    verify(executionLogCallback, times(1))
        .saveExecutionLog(
            "\nThere are multiple workloads in the Service Manifests you are deploying. Blue/Green Workflows support a single Deployment, DeploymentConfig (OpenShift) or StatefulSet workload only. To deploy additional workloads in Manifests, annotate them with harness.io/direct-apply: true",
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetAllPods() throws Exception {
    KubernetesResource kubernetesResource =
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().namespace("default").build()).build();
    k8sBlueGreenHandlerConfig.setManagedWorkload(kubernetesResource);
    k8sBlueGreenHandlerConfig.setReleaseName("releaseName");
    k8sBlueGreenHandlerConfig.setStageColor("stageColor");
    k8sBlueGreenHandlerConfig.setPrimaryColor("primaryColor");
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);

    testGetAllPodsWithStageAndPrimary();
    testGetAllPodsWitNoPrimary();
  }

  private void testGetAllPodsWitNoPrimary() throws Exception {
    when(k8sTaskHelperBase.getPodDetailsWithColor(
             any(KubernetesConfig.class), any(), any(), eq("stageColor"), anyLong()))
        .thenReturn(asList(podWithName("stage-1"), podWithName("stage-2")));
    when(k8sTaskHelperBase.getPodDetailsWithColor(
             any(KubernetesConfig.class), any(), any(), eq("primaryColor"), anyLong()))
        .thenReturn(emptyList());

    final List<K8sPod> allPods = k8sBGBaseHandler.getAllPods(LONG_TIMEOUT_INTERVAL, KubernetesConfig.builder().build(),
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(), "primaryColor",
        "stageColor", "releaseName");

    assertThat(allPods).hasSize(2);
    assertThat(allPods.stream().filter(K8sPod::isNewPod).count()).isEqualTo(2);
  }

  private void testGetAllPodsWithStageAndPrimary() throws Exception {
    when(k8sTaskHelperBase.getPodDetailsWithColor(
             any(KubernetesConfig.class), any(), any(), eq("stageColor"), anyLong()))
        .thenReturn(asList(podWithName("stage-1"), podWithName("stage-2")));
    when(k8sTaskHelperBase.getPodDetailsWithColor(
             any(KubernetesConfig.class), any(), any(), eq("primaryColor"), anyLong()))
        .thenReturn(asList(podWithName("primary-1"), podWithName("primary-2")));

    final List<K8sPod> allPods = k8sBGBaseHandler.getAllPods(LONG_TIMEOUT_INTERVAL, KubernetesConfig.builder().build(),
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(), "primaryColor",
        "stageColor", "releaseName");

    assertThat(allPods).hasSize(4);
    assertThat(allPods.stream().filter(K8sPod::isNewPod).map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("stage-1", "stage-2");
    assertThat(allPods.stream().filter(pod -> !pod.isNewPod()).map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("primary-1", "primary-2");
  }

  private K8sPod podWithName(String name) {
    return K8sPod.builder().name(name).build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAssignHelmChartInfo() throws Exception {
    K8sBlueGreenDeployTaskHandler handler = spy(k8sBlueGreenDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sBlueGreenDeployTaskParameters deployTaskParameters = K8sBlueGreenDeployTaskParameters.builder()
                                                                .k8sDelegateManifestConfig(manifestConfig)
                                                                .useDeclarativeRollback(true)
                                                                .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();

    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(executionLogCallback)
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sBlueGreenDeployTaskParameters.class), any());
    doReturn(true).when(handler).init(
        any(K8sBlueGreenDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForBlueGreen(
        any(K8sBlueGreenDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    doReturn(helmChartInfo)
        .when(k8sTaskHelper)
        .getHelmChartDetails(manifestConfig, Paths.get(".", MANIFEST_FILES_DIR).toString());
    doAnswer(invocation
        -> K8sTaskExecutionResponse.builder()
               .k8sTaskResponse(invocation.getArgument(0, K8sBlueGreenDeployResponse.class))
               .build())
        .when(k8sTaskHelper)
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap())));

    K8sTaskExecutionResponse response = handler.executeTask(deployTaskParameters, delegateTaskParams);
    K8sBlueGreenDeployResponse deployResponse = (K8sBlueGreenDeployResponse) response.getK8sTaskResponse();

    assertThat(deployResponse.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sBlueGreenDeployTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailWorkloadStatusCheck() throws Exception {
    K8sBlueGreenDeployTaskHandler handler = spy(k8sBlueGreenDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sBlueGreenDeployTaskParameters deployTaskParameters =
        K8sBlueGreenDeployTaskParameters.builder().k8sDelegateManifestConfig(manifestConfig).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();

    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(executionLogCallback)
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sBlueGreenDeployTaskParameters.class), any());
    doReturn(true).when(handler).init(
        any(K8sBlueGreenDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true).when(handler).prepareForBlueGreen(
        any(K8sBlueGreenDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));

    doAnswer(invocation
        -> K8sTaskExecutionResponse.builder()
               .k8sTaskResponse(invocation.getArgument(0, K8sBlueGreenDeployResponse.class))
               .build())
        .when(k8sTaskHelper)
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap())));

    K8sTaskExecutionResponse response = handler.executeTask(deployTaskParameters, delegateTaskParams);
    assertThat(FAILURE).isEqualTo(response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldFetchReleaseDataUsingK8sClient() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    List<KubernetesResource> resources = new ArrayList<>();
    resources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    resources.addAll(ManifestHelper.processYaml(PRIMARY_SERVICE_YAML));
    resources.addAll(ManifestHelper.processYaml(STAGE_SERVICE_YAML));
    k8sBlueGreenHandlerConfig.setResources(resources);
    on(k8sBlueGreenDeployTaskHandler).set("k8sBGBaseHandler", k8sBGBaseHandler);
    V1Service primaryService = new V1ServiceBuilder()
                                   .withNewSpec()
                                   .withSelector(ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorGreen))
                                   .endSpec()
                                   .build();
    V1Service stageService = new V1ServiceBuilder()
                                 .withNewSpec()
                                 .withSelector(ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue))
                                 .endSpec()
                                 .build();

    when(kubernetesContainerService.getService(null, "primary-service")).thenReturn(primaryService);
    when(kubernetesContainerService.getService(null, "stage-service")).thenReturn(stageService);

    k8sBlueGreenDeployTaskHandler.prepareForBlueGreen(
        K8sBlueGreenDeployTaskParameters.builder().build(), delegateTaskParams, executionLogCallback);
    verify(releaseHandler).getReleaseHistory(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldSaveReleaseHistoryUsingK8sClient() throws Exception {
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(true).when(spyHandler).init(any(), any(), any());
    doReturn(true).when(spyHandler).prepareForBlueGreen(any(), any(), any());

    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());
    k8sBlueGreenHandlerConfig.setResources(
        new ArrayList<>(asList(primaryService(), deployment(), stageService(), configMap())));
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn("latest-rev").when(k8sTaskHelperBase).getLatestRevision(any(), eq(deployment().getResourceId()), any());

    spyHandler.executeTaskInternal(K8sBlueGreenDeployTaskParameters.builder()
                                       .releaseName("release-success")
                                       .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
                                       .useDeclarativeRollback(true)
                                       .build(),
        K8sDelegateTaskParams.builder()
            .workingDirectory("./working-dir")
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .build());

    verify(k8sTaskHelper, times(1)).getK8sTaskExecutionResponse(any(), eq(SUCCESS));
    verify(k8sTaskHelperBase, times(2)).saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), any());

    K8sBlueGreenDeployTaskParameters deployTaskParams = K8sBlueGreenDeployTaskParameters.builder()
                                                            .releaseName("releaseName-statusCheck")
                                                            .useDeclarativeRollback(true)
                                                            .build();
    K8sDelegateTaskParams taskParams = K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build();

    doReturn(false).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    K8sTaskExecutionResponse response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(2))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-statusCheck"));

    deployTaskParams.setReleaseName("releaseName-apply");
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    response = spyHandler.executeTaskInternal(deployTaskParams, taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(k8sTaskHelperBase, times(1))
        .saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName-apply"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldCatchGetPodListException() throws Exception {
    K8sBlueGreenDeployTaskHandler spyHandler = spy(k8sBlueGreenDeployTaskHandler);
    doReturn(true).when(spyHandler).init(any(), any(), any());
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(true).when(spyHandler).prepareForBlueGreen(any(), any(), any());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).doStatusCheck(any(), any(), any(), any());
    doReturn("latest-rev").when(k8sTaskHelperBase).getLatestRevision(any(), eq(deployment().getResourceId()), any());
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    k8sBlueGreenHandlerConfig.setManagedWorkload(deployment());
    k8sBlueGreenHandlerConfig.setCurrentRelease(release);
    k8sBlueGreenHandlerConfig.setPrimaryService(primaryService());
    k8sBlueGreenHandlerConfig.setStageService(stageService());

    InvalidRequestException thrownException = new InvalidRequestException("Failed to get pod details");
    doThrow(thrownException).when(mockedK8sBGBaseHandler).getAllPods(anyLong(), any(), any(), any(), any(), any());

    assertThatThrownBy(()
                           -> spyHandler.executeTaskInternal(K8sBlueGreenDeployTaskParameters.builder()
                                                                 .k8sTaskType(K8sTaskType.BLUE_GREEN_DEPLOY)
                                                                 .useDeclarativeRollback(true)
                                                                 .build(),
                               K8sDelegateTaskParams.builder()
                                   .workingDirectory("./")
                                   .kubectlPath("kubectl")
                                   .kubeconfigPath("kubeconfig")
                                   .build()))
        .isEqualTo(thrownException);

    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(thrownException.getMessage(), ERROR, FAILURE);
    verify(k8sTaskHelperBase, times(2)).saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), any());
  }
}
