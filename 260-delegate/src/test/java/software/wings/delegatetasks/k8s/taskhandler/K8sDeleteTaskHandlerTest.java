/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.delegatetasks.k8s.K8sTestHelper.deployment;
import static software.wings.delegatetasks.k8s.K8sTestHelper.deploymentConfig;
import static software.wings.delegatetasks.k8s.K8sTestHelper.namespace;
import static software.wings.delegatetasks.k8s.K8sTestHelper.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sDeleteResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sDeleteTaskHandlerTest extends WingsBaseTest {
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private ContainerDeploymentDelegateHelper deploymentDelegateHelper;
  @InjectMocks private K8sDeleteTaskHandler handler;

  private final K8sDeleteTaskParameters deleteTaskParameters =
      K8sDeleteTaskParameters.builder().resources("default/Deployment/test,default/Service/test-svc").build();
  private final K8sDelegateTaskParams taskParams =
      K8sDelegateTaskParams.builder().kubectlPath("kubectl").kubeconfigPath("kubeconfig").workingDirectory(".").build();
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

  @Before
  public void setUp() throws Exception {
    doReturn(kubernetesConfig).when(deploymentDelegateHelper).getKubernetesConfig(any(), eq(false));
    doReturn(mock(ExecutionLogCallback.class))
        .when(k8sTaskHelper)
        .getExecutionLogCallback(any(K8sDeleteTaskParameters.class), eq(FetchFiles));
    doReturn(asList(deployment().getResourceId(), service().getResourceId(), deploymentConfig().getResourceId(),
                 namespace().getResourceId()))
        .when(k8sTaskHelperBase)
        .getResourceIdsForDeletion(
            anyBoolean(), any(), eq(kubernetesConfig), any(ExecutionLogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeTaskNoResourcesAndFiles() throws Exception {
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());

    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder().build();
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    verify(k8sTaskHelper, times(1)).getK8sTaskExecutionResponse(K8sDeleteResponse.builder().build(), SUCCESS);
    verify(k8sTaskHelperBase, never()).delete(any(Kubectl.class), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void exceptionInReadingResources() throws Exception {
    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder().resources("random").build();
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    verify(k8sTaskHelperBase, never())
        .delete(any(Kubectl.class), any(), any(), any(ExecutionLogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void deleteSpecificResources() throws Exception {
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteTaskParameters, taskParams);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(taskParams), captor.capture(), any(ExecutionLogCallback.class), anyBoolean());

    @SuppressWarnings("unchecked") List<KubernetesResourceId> deletedResources = captor.getValue();

    assertThat(deletedResources).hasSize(2);
    assertThat(deletedResources.stream().map(KubernetesResourceId::getKind).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("Deployment", "Service");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void deleteIfStarGiveInResources() throws Exception {
    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder().resources("*").build();
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1)).delete(any(), eq(taskParams), captor.capture(), any(), anyBoolean());

    @SuppressWarnings("unchecked") List<KubernetesResourceId> deletedResources = captor.getValue();

    assertThat(deletedResources).hasSize(4);
    assertThat(deletedResources.stream().map(KubernetesResourceId::getKind).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("Deployment", "DeploymentConfig", "Service", "Namespace");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void invalidTaskParameters() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> handler.executeTaskInternal(null, null));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> handler.executeTaskInternal(K8sApplyTaskParameters.builder().build(), null));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> handler.executeTaskInternal(
                            K8sApplyTaskParameters.builder().build(), K8sDelegateTaskParams.builder().build()));
  }

  @Test
  @Owner(developers = OwnerRule.SAHIL)
  @Category(UnitTests.class)
  public void deleteGivenFiles() throws Exception {
    doReturn(KubernetesConfig.builder().build()).when(deploymentDelegateHelper).getKubernetesConfig(any(), eq(false));
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());

    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder().filePaths("a,b,c").build();

    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    verify(k8sTaskHelper, times(1)).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    verify(deploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
    verify(k8sTaskHelper)
        .getResourcesFromManifests(any(), any(), any(), eq(ImmutableList.of("a", "b", "c")), any(), any(), any(), any(),
            eq(deleteAllParams), eq(false));
  }

  @Test
  @Owner(developers = OwnerRule.SAHIL)
  @Category(UnitTests.class)
  public void deleteFilesGivenNoFilePaths() throws Exception {
    doReturn(KubernetesConfig.builder().build())
        .when(deploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), eq(false));
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());

    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder().filePaths("").build();
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    verify(k8sTaskHelper)
        .getK8sTaskExecutionResponse(K8sDeleteResponse.builder().build(), CommandExecutionStatus.SUCCESS);
    verify(deploymentDelegateHelper, times(1)).getKubernetesConfig(any(), eq(false));
  }

  @Test
  @Owner(developers = OwnerRule.SAHIL)
  @Category(UnitTests.class)
  public void deleteFilesAndResourcesGiven() throws Exception {
    K8sDeleteTaskParameters deleteAllParams = K8sDeleteTaskParameters.builder()
                                                  .filePaths("a,b,c")
                                                  .resources("default/Deployment/test,default/Service/test-svc")
                                                  .build();
    final K8sTaskExecutionResponse taskResponse = handler.executeTaskInternal(deleteAllParams, taskParams);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(taskParams), captor.capture(), any(ExecutionLogCallback.class), anyBoolean());

    @SuppressWarnings("unchecked") List<KubernetesResourceId> deletedResources = captor.getValue();

    assertThat(deletedResources).hasSize(2);
    assertThat(deletedResources.stream().map(KubernetesResourceId::getKind).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("Deployment", "Service");
  }

  @Test
  @Owner(developers = OwnerRule.TARUN_UBA)
  @Category(UnitTests.class)
  public void deleteCanaryWorkloadResources() throws Exception {
    K8sReleaseHandler releaseHandler = mock(K8sReleaseHandler.class);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(KubernetesConfig.builder().build())
        .when(deploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), eq(false));
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());

    K8sDeleteTaskParameters deleteAllResources =
        K8sDeleteTaskParameters.builder().k8sCanaryDelete(true).resources("${k8s.canaryWorkload}").build();
    handler.executeTaskInternal(deleteAllResources, taskParams);

    verify(releaseHandler).getReleaseHistory(any(), any());
  }
}
