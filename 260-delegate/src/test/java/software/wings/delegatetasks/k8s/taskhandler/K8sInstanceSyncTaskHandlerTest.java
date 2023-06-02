/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BOJANA;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sInstanceSyncTaskHandlerTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private K8sInstanceSyncTaskHandler k8sInstanceSyncTaskHandler;

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sInstanceSyncTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void executeTaskInternalSuccess() throws Exception {
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());

    List<K8sPod> podsList = Arrays.asList(K8sPod.builder().build());
    doReturn(podsList).when(k8sTaskHelperBase).getPodDetails(any(), any(), any(), anyLong());

    k8sInstanceSyncTaskHandler.executeTaskInternal(getTaskParameters(), K8sDelegateTaskParams.builder().build());
    verify(k8sTaskHelperBase, times(1)).getPodDetails(any(), any(), any(), anyLong());
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void executeTaskInternalNoPods() throws Exception {
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());

    k8sInstanceSyncTaskHandler.executeTaskInternal(getTaskParameters(), K8sDelegateTaskParams.builder().build());
    verify(k8sTaskHelperBase, times(1)).getPodDetails(any(), any(), any(), anyLong());
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeTaskInternalWithDeprecateFabric8Disabled() throws Exception {
    K8sInstanceSyncTaskParameters taskParameters = getTaskParameters();
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());

    List<K8sPod> podsList = Arrays.asList(K8sPod.builder().build());
    doReturn(podsList).when(k8sTaskHelperBase).getPodDetails(any(), any(), any(), anyLong());

    k8sInstanceSyncTaskHandler.executeTaskInternal(taskParameters, K8sDelegateTaskParams.builder().build());
    verify(k8sTaskHelperBase, times(1)).getPodDetails(any(), any(), any(), anyLong());
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  private K8sInstanceSyncTaskParameters getTaskParameters() {
    return K8sInstanceSyncTaskParameters.builder().build();
  }
}
