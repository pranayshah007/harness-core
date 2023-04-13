/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.delegatetasks.k8s.K8sTestConstants.CONFIG_MAP_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.SECRET_YAML;

import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
import io.harness.delegate.k8s.K8sApplyBaseHandler;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesYamlException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.delegatetasks.k8s.K8sTestHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sApplyResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sApplyTaskHandlerTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sApplyBaseHandler mockedK8sApplyBaseHandler;
  @InjectMocks private K8sApplyBaseHandler k8sApplyBaseHandler;
  @InjectMocks private K8sApplyTaskHandler k8sApplyTaskHandler;

  @Captor ArgumentCaptor<List<KubernetesResource>> kubernetesResourceListCaptor;

  @Before
  public void setup() {
    doReturn(mock(ExecutionLogCallback.class)).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    K8sApplyTaskParameters k8sApplyTaskParameters =
        K8sApplyTaskParameters.builder().skipDryRun(true).filePaths("abc/xyz.yaml").build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(nullable(K8sClusterConfig.class), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    when(k8sTaskHelper.renderTemplateForGivenFiles(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(asList(FileData.builder().build()));
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(Collections.emptyList());

    k8sApplyTaskHandler.init(k8sApplyTaskParameters, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(0)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelper, times(1))
        .getResourcesFromManifests(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    verify(containerDeploymentDelegateHelper, times(1))
        .getKubernetesConfig(nullable(K8sClusterConfig.class), eq(false));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsNotSkipped() throws Exception {
    K8sApplyTaskParameters k8sApplyTaskParameters =
        K8sApplyTaskParameters.builder().skipDryRun(false).filePaths("abc/xyz.yaml").build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(nullable(K8sClusterConfig.class), eq(false)))
        .thenReturn(KubernetesConfig.builder().build());
    when(k8sTaskHelper.renderTemplateForGivenFiles(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(asList(FileData.builder().build()));
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(Collections.emptyList());

    k8sApplyTaskHandler.init(k8sApplyTaskParameters, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelper, times(1))
        .getResourcesFromManifests(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    verify(containerDeploymentDelegateHelper, times(1))
        .getKubernetesConfig(nullable(K8sClusterConfig.class), eq(false));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sApplyTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void failureInFetchingManifestFiles() {
    doReturn(false)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());

    final K8sTaskExecutionResponse response =
        k8sApplyTaskHandler.executeTask(K8sApplyTaskParameters.builder().releaseName("release-name").build(),
            K8sDelegateTaskParams.builder().workingDirectory(".").build());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getK8sTaskResponse()).isNotNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void noFileSpecifiedInApply() {
    boolean success;
    success = k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));
    assertThat(success).isFalse();

    success = k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().filePaths("").build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void readAllFilesSpecifiedInApply() throws Exception {
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(nullable(K8sClusterConfig.class), eq(false));

    k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().skipRendering(true).filePaths("a,b,c").build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    verify(k8sTaskHelper, times(1))
        .getResourcesFromManifests(nullable(K8sDelegateTaskParams.class), nullable(K8sDelegateManifestConfig.class),
            any(), eq(asList("a", "b", "c")), nullable(List.class), nullable(String.class), nullable(String.class),
            any(ExecutionLogCallback.class), any(K8sTaskParameters.class), eq(true));

    k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().filePaths("a").build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    verify(k8sTaskHelper, times(1))
        .getResourcesFromManifests(nullable(K8sDelegateTaskParams.class), nullable(K8sDelegateManifestConfig.class),
            any(), eq(asList("a")), nullable(List.class), nullable(String.class), nullable(String.class),
            any(ExecutionLogCallback.class), any(K8sTaskParameters.class), anyBoolean());

    k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().filePaths("b ,").build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    verify(k8sTaskHelper, times(1))
        .getResourcesFromManifests(nullable(K8sDelegateTaskParams.class), nullable(K8sDelegateManifestConfig.class),
            any(), eq(asList("b")), nullable(List.class), nullable(String.class), nullable(String.class),
            any(ExecutionLogCallback.class), any(K8sTaskParameters.class), anyBoolean());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void invalidManifestFiles() throws Exception {
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), eq(false));

    doReturn(asList(ManifestFile.builder().build()))
        .when(k8sTaskHelper)
        .renderTemplateForGivenFiles(any(K8sDelegateTaskParams.class), any(K8sDelegateManifestConfig.class), any(),
            eq(asList("a", "b", "c")), any(), any(), any(), any(ExecutionLogCallback.class),
            any(K8sTaskParameters.class), anyBoolean());

    doThrow(new KubernetesYamlException("reason"))
        .when(k8sTaskHelperBase)
        .readManifests(any(), any(ExecutionLogCallback.class));

    final boolean success = k8sApplyTaskHandler.init(K8sApplyTaskParameters.builder().filePaths("a,b,c").build(),
        K8sDelegateTaskParams.builder().build(), Mockito.mock(ExecutionLogCallback.class));

    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void initFailure() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);

    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            nullable(K8sDelegateManifestConfig.class), any(), nullable(ExecutionLogCallback.class), anyLong());
    doReturn(false).when(handler).init(nullable(K8sApplyTaskParameters.class), nullable(K8sDelegateTaskParams.class),
        nullable(ExecutionLogCallback.class));

    final K8sTaskExecutionResponse response = handler.executeTaskInternal(K8sApplyTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(handler, times(1))
        .init(any(K8sApplyTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeTaskInternalExportManifests() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);
    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    k8sApplyHandlerConfig.setResources(kubernetesResources);
    Reflect.on(handler).set("k8sApplyHandlerConfig", k8sApplyHandlerConfig);

    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            nullable(K8sDelegateManifestConfig.class), any(), nullable(ExecutionLogCallback.class), anyLong());
    doReturn(true).when(handler).init(nullable(K8sApplyTaskParameters.class), nullable(K8sDelegateTaskParams.class),
        nullable(ExecutionLogCallback.class));

    final K8sTaskExecutionResponse response =
        handler.executeTaskInternal(K8sApplyTaskParameters.builder().exportManifests(true).build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(handler, times(1))
        .init(any(K8sApplyTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    verify(k8sTaskHelper, times(1)).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    verify(mockedK8sApplyBaseHandler, times(0)).prepare(any(), anyBoolean(), any());
    verify(k8sTaskHelperBase, times(0)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    verify(mockedK8sApplyBaseHandler, times(0)).steadyStateCheck(anyBoolean(), any(), any(), anyLong(), any(), any());
    verify(mockedK8sApplyBaseHandler, times(0)).wrapUp(any(), any(), any());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(((K8sApplyResponse) response.getK8sTaskResponse()).getResources()).isEqualTo(kubernetesResources);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeTaskInternalInheritManifests() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);
    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    Reflect.on(handler).set("k8sApplyHandlerConfig", k8sApplyHandlerConfig);

    doReturn(true).when(k8sTaskHelper).restore(any(), any(), any(), any(), any());
    doReturn(true).when(mockedK8sApplyBaseHandler).prepare(any(), anyBoolean(), any());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), eq(null));
    doReturn(true)
        .when(mockedK8sApplyBaseHandler)
        .steadyStateCheck(anyBoolean(), any(), any(), anyLong(), any(), any());
    doNothing().when(mockedK8sApplyBaseHandler).wrapUp(any(), any(), any());

    // Since Java 16 Path.get() start throwing NullPointerException if its first argument is null. Please make sure to
    // add not null check for the corresponding field in K8sDelegateTaskParams
    final K8sTaskExecutionResponse response =
        handler.executeTaskInternal(K8sApplyTaskParameters.builder()
                                        .k8sClusterConfig(K8sClusterConfig.builder().namespace("default").build())
                                        .inheritManifests(true)
                                        .kubernetesResources(kubernetesResources)
                                        .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(handler, times(0))
        .init(any(K8sApplyTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    verify(k8sTaskHelper, times(0)).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());
    verify(mockedK8sApplyBaseHandler, times(1)).prepare(any(), anyBoolean(), any());
    verify(k8sTaskHelperBase, times(1)).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    verify(mockedK8sApplyBaseHandler, times(1)).steadyStateCheck(anyBoolean(), any(), any(), anyLong(), any(), any());
    verify(mockedK8sApplyBaseHandler, times(1)).wrapUp(any(), any(), any());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(((K8sApplyResponse) response.getK8sTaskResponse()).getResources()).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void executeTaskInternalInheritManifestsRestoreFailed() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);
    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    Reflect.on(handler).set("k8sApplyHandlerConfig", k8sApplyHandlerConfig);

    doReturn(false).when(k8sTaskHelper).restore(any(), any(), any(), any(), any());

    final K8sTaskExecutionResponse response =
        handler.executeTaskInternal(K8sApplyTaskParameters.builder()
                                        .k8sClusterConfig(K8sClusterConfig.builder().namespace("default").build())
                                        .inheritManifests(true)
                                        .kubernetesResources(kubernetesResources)
                                        .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepare() throws IOException {
    List<KubernetesResource> resources = asList(K8sTestHelper.deployment(), K8sTestHelper.configMap());
    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    k8sApplyHandlerConfig.setResources(EMPTY_LIST);
    Reflect.on(k8sApplyTaskHandler).set("k8sApplyHandlerConfig", k8sApplyHandlerConfig);

    assertThat(k8sApplyBaseHandler.prepare(mock(ExecutionLogCallback.class), false, k8sApplyHandlerConfig)).isTrue();
    assertThat(k8sApplyHandlerConfig.getWorkloads()).isEmpty();

    k8sApplyHandlerConfig.setResources(resources);
    Reflect.on(k8sApplyTaskHandler).set("k8sApplyHandlerConfig", k8sApplyHandlerConfig);
    assertThat(k8sApplyBaseHandler.prepare(mock(ExecutionLogCallback.class), false, k8sApplyHandlerConfig)).isTrue();
    assertThat(k8sApplyHandlerConfig.getWorkloads()).hasSize(1);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void prepareWorkloadsFound() throws IOException {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    KubernetesResource customWorkload = ManifestHelper
                                            .processYaml("apiVersion: apps/v1\n"
                                                + "kind: Foo\n"
                                                + "metadata:\n"
                                                + "  name: foo\n"
                                                + "  annotations:\n"
                                                + "    harness.io/managed-workload: true\n"
                                                + "spec:\n"
                                                + "  replicas: 1")
                                            .get(0);
    List<KubernetesResource> resources = asList(K8sTestHelper.deployment(), K8sTestHelper.configMap(), customWorkload);

    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    k8sApplyHandlerConfig.setResources(resources);

    boolean success = k8sApplyBaseHandler.prepare(executionLogCallback, false, k8sApplyHandlerConfig);
    assertThat(success).isTrue();
    assertThat(k8sApplyHandlerConfig.getWorkloads()).hasSize(1);
    assertThat(k8sApplyHandlerConfig.getCustomWorkloads()).hasSize(1);

    verify(k8sTaskHelperBase, times(2)).getResourcesInTableFormat(kubernetesResourceListCaptor.capture());
    List<List<KubernetesResource>> kubernetesResourcesList = kubernetesResourceListCaptor.getAllValues();
    // first time it retrieves all
    List<KubernetesResource> workloadsFound = kubernetesResourcesList.get(0);
    assertThat(workloadsFound.size()).isEqualTo(3);
    // second time workload and custom workloads are filtered
    workloadsFound = kubernetesResourcesList.get(1);
    // one workload and one custom workload
    assertThat(workloadsFound.size()).isEqualTo(2);
    assertThat(workloadsFound.get(0).getResourceId().getName()).isEqualTo("nginx-deployment");
    assertThat(workloadsFound.get(1).getResourceId().getName()).isEqualTo("foo");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void prepareFailure() {
    //    PowerMockito.when(ManifestHelper.getWorkloadsForApplyState(any())).thenThrow(new RuntimeException());
    assertThat(k8sApplyBaseHandler.prepare(mock(ExecutionLogCallback.class), false, new K8sApplyHandlerConfig()))
        .isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void failureInApplyingManifestFiles() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);
    K8sApplyBaseHandler baseHandler = spy(k8sApplyBaseHandler);
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true).when(handler).init(
        any(K8sApplyTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(baseHandler)
        .prepare(any(ExecutionLogCallback.class), anyBoolean(), any(K8sApplyHandlerConfig.class));
    doReturn(false)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());

    final K8sTaskExecutionResponse response =
        k8sApplyTaskHandler.executeTask(K8sApplyTaskParameters.builder().releaseName("release-name").build(),
            K8sDelegateTaskParams.builder().workingDirectory(".").build());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getK8sTaskResponse()).isNotNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSteadyStateCheck() throws Exception {
    K8sApplyBaseHandler baseHandler = spy(k8sApplyBaseHandler);

    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(nullable(Kubectl.class), any(), nullable(K8sDelegateTaskParams.class), any(),
            nullable(ExecutionLogCallback.class), anyBoolean(), eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(nullable(Kubectl.class), any(), nullable(K8sDelegateTaskParams.class),
            nullable(ExecutionLogCallback.class), anyBoolean(), anyLong(), eq(false));

    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    k8sApplyHandlerConfig.setWorkloads(
        asList(KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(),
            KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build()));
    k8sApplyHandlerConfig.setCustomWorkloads(
        asList(KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(),
            KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build()));
    k8sApplyHandlerConfig.setResources(
        asList(KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(),
            KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build()));

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    boolean status = baseHandler.steadyStateCheck(
        false, "default", K8sDelegateTaskParams.builder().build(), 100000, null, k8sApplyHandlerConfig);

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(nullable(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(), nullable(ExecutionLogCallback.class), anyBoolean(), eq(false));

    @SuppressWarnings("unchecked")
    final List<KubernetesResourceId> capturedResources = (List<KubernetesResourceId>) captor.getValue();
    assertThat(status).isTrue();
    assertThat(capturedResources).hasSize(2);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void applyManifestFilesWithCrd() throws Exception {
    K8sApplyBaseHandler baseHandler = spy(k8sApplyBaseHandler);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(nullable(Kubectl.class), nullable(List.class),
            nullable(K8sDelegateTaskParams.class), any(), nullable(ExecutionLogCallback.class), anyBoolean(),
            eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(nullable(Kubectl.class), nullable(List.class),
            nullable(K8sDelegateTaskParams.class), nullable(ExecutionLogCallback.class), eq(true), anyLong(),
            eq(false));

    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    k8sApplyHandlerConfig.setWorkloads(
        asList(KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(),
            KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build()));

    List<KubernetesResource> managedResources = ManifestHelper.processYaml("apiVersion: apps/v1\n"
        + "kind: Foo\n"
        + "metadata:\n"
        + "  name: deployment\n"
        + "  annotations:\n"
        + "    harness.io/managed-workload: true\n"
        + "spec:\n"
        + "  replicas: 1");

    k8sApplyHandlerConfig.setResources(managedResources);
    k8sApplyHandlerConfig.setCustomWorkloads(managedResources);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    boolean status = baseHandler.steadyStateCheck(
        false, "default", K8sDelegateTaskParams.builder().build(), 100000, null, k8sApplyHandlerConfig);

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(nullable(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            nullable(ExecutionLogCallback.class), eq(true), anyLong(), eq(false));

    @SuppressWarnings("unchecked")
    final List<KubernetesResourceId> capturedResources = (List<KubernetesResourceId>) captor.getValue();
    assertThat(status).isTrue();
    assertThat(capturedResources).hasSize(1);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailCrdStatusCheck() throws Exception {
    K8sApplyBaseHandler baseHandler = spy(k8sApplyBaseHandler);

    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(),
            any(ExecutionLogCallback.class), anyBoolean());
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), eq(true), anyLong());

    List<KubernetesResource> managedResources = ManifestHelper.processYaml("apiVersion: apps/v1\n"
        + "kind: Foo\n"
        + "metadata:\n"
        + "  name: deployment\n"
        + "  annotations:\n"
        + "    harness.io/managed-workload: true\n"
        + "spec:\n"
        + "  replicas: 1");

    K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();
    k8sApplyHandlerConfig.setWorkloads(
        asList(KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build(),
            KubernetesResource.builder().resourceId(KubernetesResourceId.builder().build()).build()));
    k8sApplyHandlerConfig.setResources(managedResources);
    k8sApplyHandlerConfig.setCustomWorkloads(managedResources);

    boolean status = baseHandler.steadyStateCheck(
        false, "default", K8sDelegateTaskParams.builder().build(), 100000, null, k8sApplyHandlerConfig);
    assertThat(status).isFalse();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(nullable(Kubectl.class), captor.capture(),
            nullable(K8sDelegateTaskParams.class), nullable(ExecutionLogCallback.class), eq(true), anyLong(),
            eq(false));

    @SuppressWarnings("unchecked")
    final List<KubernetesResourceId> capturedResources = (List<KubernetesResourceId>) captor.getValue();
    assertThat(capturedResources).hasSize(1);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApplyManifests() throws Exception {
    K8sApplyTaskHandler handler = spy(k8sApplyTaskHandler);
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            nullable(K8sDelegateManifestConfig.class), any(), nullable(ExecutionLogCallback.class), anyLong());
    doReturn(true).when(handler).init(nullable(K8sApplyTaskParameters.class), nullable(K8sDelegateTaskParams.class),
        nullable(ExecutionLogCallback.class));
    doReturn(true)
        .when(mockedK8sApplyBaseHandler)
        .prepare(nullable(ExecutionLogCallback.class), anyBoolean(), nullable(K8sApplyHandlerConfig.class));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(nullable(Kubectl.class), any(), nullable(K8sDelegateTaskParams.class),
            nullable(ExecutionLogCallback.class), anyBoolean(), any());
    doReturn(true)
        .when(mockedK8sApplyBaseHandler)
        .steadyStateCheck(anyBoolean(), any(), nullable(K8sDelegateTaskParams.class), anyLong(),
            nullable(ExecutionLogCallback.class), nullable(K8sApplyHandlerConfig.class));

    final K8sTaskExecutionResponse response =
        handler.executeTask(K8sApplyTaskParameters.builder()
                                .releaseName("release-name")
                                .skipSteadyStateCheck(false)
                                .k8sClusterConfig(K8sClusterConfig.builder().namespace("default").build())
                                .build(),
            K8sDelegateTaskParams.builder().workingDirectory(".").build());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sTaskResponse()).isNotNull();
  }
}
