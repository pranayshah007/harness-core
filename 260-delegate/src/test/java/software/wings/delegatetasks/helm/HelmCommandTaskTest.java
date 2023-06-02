/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmCommandExecutionResponse;
import software.wings.helpers.ext.helm.HelmDeployService;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;

import java.io.IOException;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandTaskTest extends WingsBaseTest {
  @Mock private DelegateLogService delegateLogService;
  @Mock private HelmDeployService helmDeployService;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private HelmCommandHelper helmCommandHelper;
  @Mock private HelmCommandRequest dummyCommandRequest;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private HelmTaskHelperBase helmTaskHelperBase;

  @InjectMocks
  private final HelmCommandTask helmCommandTask = new HelmCommandTask(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().async(false).build()).build(),
      null, notifyResponseData -> {}, () -> true);

  @Before
  public void setup() {
    HelmCommandResponse ensureHelmInstalledResponse =
        new HelmCommandResponse(CommandExecutionStatus.SUCCESS, "Helm3 is installed at [mock]");
    doReturn(ensureHelmInstalledResponse).when(helmDeployService).ensureHelmInstalled(any(HelmCommandRequest.class));

    when(k8sGlobalConfigService.getOcPath()).thenReturn("/tmp");
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any())).thenReturn(KubernetesConfig.builder().build());
    when(containerDeploymentDelegateHelper.createKubeConfig(any())).thenReturn(".kube/config");
    when(helmTaskHelperBase.getHelmLocalRepositoryPath()).thenReturn("/helm-dir/");
    when(helmTaskHelperBase.isHelmLocalRepoSet()).thenReturn(true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitPrerequisite() {
    String kubeConfigLocation = ".kube/config";

    doReturn(mock(LogCallback.class)).when(dummyCommandRequest).getExecutionLogCallback();
    helmCommandTask.run(dummyCommandRequest);

    verify(helmDeployService, times(1)).ensureHelmInstalled(dummyCommandRequest);

    ArgumentCaptor<String> kubeLocationCaptor = ArgumentCaptor.forClass(String.class);
    verify(dummyCommandRequest, times(1)).setKubeConfigLocation(kubeLocationCaptor.capture());

    assertThat(kubeLocationCaptor.getValue()).isEqualTo(kubeConfigLocation);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunTaskWithInstallCommand() throws IOException {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().accountId("accountId").build();
    HelmInstallCommandResponse deployResponse =
        HelmInstallCommandResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(deployResponse).when(helmDeployService).deploy(request);
    HelmCommandExecutionResponse response = helmCommandTask.run(request);

    verify(helmDeployService, times(1)).deploy(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(deployResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunTaskWithRollbackCommand() throws IOException {
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder().accountId("accountId").build();
    HelmInstallCommandResponse rollbackResponse =
        HelmInstallCommandResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(rollbackResponse).when(helmDeployService).rollback(request);
    HelmCommandExecutionResponse response = helmCommandTask.run(request);

    verify(helmDeployService, times(1)).rollback(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(rollbackResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunTaskWithReleaseHistoryCommand() {
    HelmReleaseHistoryCommandRequest request =
        HelmReleaseHistoryCommandRequest.builder().accountId("accountId").build();
    HelmReleaseHistoryCommandResponse releaseHistoryResponse =
        HelmReleaseHistoryCommandResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(releaseHistoryResponse).when(helmDeployService).releaseHistory(request);
    HelmCommandExecutionResponse response = helmCommandTask.run(request);

    verify(helmDeployService, times(1)).releaseHistory(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(releaseHistoryResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunTaskWithException() throws IOException {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().accountId("accountId").build();

    doThrow(new IOException("Unable to deploy")).when(helmDeployService).deploy(request);
    HelmCommandExecutionResponse response = helmCommandTask.run(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Exception in processing helm task: Unable to deploy");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunTaskWithFailure() throws IOException {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().accountId("accountId").build();
    HelmInstallCommandResponse deployResponse = HelmInstallCommandResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .output("Error while deploying")
                                                    .build();

    doReturn(deployResponse).when(helmDeployService).deploy(request);
    HelmCommandExecutionResponse response = helmCommandTask.run(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Error while deploying");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetExecutionCallback() {
    HelmCommandTask helmCommandTask = getTask(false);
    HelmCommandTask asyncHelmCommandTask = getTask(true);
    ArgumentCaptor<LogCallback> logCallbackCaptor = ArgumentCaptor.forClass(LogCallback.class);

    doReturn(mock(LogCallback.class)).when(dummyCommandRequest).getExecutionLogCallback();
    helmCommandTask.run(dummyCommandRequest);
    verify(dummyCommandRequest, times(1)).setExecutionLogCallback(logCallbackCaptor.capture());
    assertThat(logCallbackCaptor.getValue()).isInstanceOf(NoopExecutionCallback.class);

    asyncHelmCommandTask.run(dummyCommandRequest);
    verify(dummyCommandRequest, times(2)).setExecutionLogCallback(logCallbackCaptor.capture());
    assertThat(logCallbackCaptor.getValue()).isInstanceOf(ExecutionLogCallback.class);
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunWithObjectList() {
    helmCommandTask.run(new Object[] {});
  }

  private static HelmCommandTask getTask(boolean async) {
    return new HelmCommandTask(
        DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().async(async).build()).build(),
        null, notifyResponseData -> {}, () -> true);
  }
}
