/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BOJANA;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.K8sRollingRollbackBaseHandler;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployRollbackTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sRollingDeployRollbackTaskHandlerTest extends WingsBaseTest {
  @Mock private K8sTaskHelper taskHelper;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sRollingRollbackBaseHandler k8sRollingRollbackBaseHandler;

  @InjectMocks private K8sRollingDeployRollbackTaskHandler k8sRollingDeployRollbackTaskHandler;

  @Mock private ExecutionLogCallback logCallback;
  @Mock private KubernetesConfig kubernetesConfig;
  @Mock private K8sClusterConfig k8sClusterConfig;

  private K8sRollingRollbackHandlerConfig rollbackHandlerConfig;
  private K8sRollingDeployRollbackTaskParameters k8sRollingDeployRollbackTaskParameters;
  private K8sDelegateTaskParams k8sDelegateTaskParams;

  private final Integer releaseNumber = 2;
  private final Integer timeoutIntervalInMin = 10;
  private final String releaseName = "releaseName";

  @Before
  public void setUp() throws Exception {
    doReturn(logCallback).when(taskHelper).getExecutionLogCallback(any(), any());

    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(k8sClusterConfig, false);

    rollbackHandlerConfig = k8sRollingDeployRollbackTaskHandler.getRollbackHandlerConfig();
    k8sRollingDeployRollbackTaskParameters = K8sRollingDeployRollbackTaskParameters.builder()
                                                 .k8sClusterConfig(k8sClusterConfig)
                                                 .releaseName(releaseName)
                                                 .releaseNumber(releaseNumber)
                                                 .timeoutIntervalInMin(timeoutIntervalInMin)
                                                 .build();
    k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackSuccess() throws Exception {
    doReturn(true)
        .when(k8sRollingRollbackBaseHandler)
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);
    K8sTaskExecutionResponse response = k8sRollingDeployRollbackTaskHandler.executeTaskInternal(
        k8sRollingDeployRollbackTaskParameters, k8sDelegateTaskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(rollbackHandlerConfig.getKubernetesConfig()).isSameAs(kubernetesConfig);
    assertThat(rollbackHandlerConfig.getClient()).isNotNull();
    verify(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);
    verify(k8sRollingRollbackBaseHandler)
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);
    verify(k8sRollingRollbackBaseHandler)
        .steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, timeoutIntervalInMin, logCallback);
    verify(k8sRollingRollbackBaseHandler).postProcess(rollbackHandlerConfig, releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackInitFailed() throws Exception {
    RuntimeException exception = new RuntimeException("failed");
    doThrow(exception).when(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);

    assertThatThrownBy(()
                           -> k8sRollingDeployRollbackTaskHandler.executeTaskInternal(
                               k8sRollingDeployRollbackTaskParameters, k8sDelegateTaskParams))
        .isSameAs(exception);

    assertThat(rollbackHandlerConfig.getKubernetesConfig()).isSameAs(kubernetesConfig);
    assertThat(rollbackHandlerConfig.getClient()).isNotNull();
    verify(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);
    verify(k8sRollingRollbackBaseHandler, never())
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);
    verify(k8sRollingRollbackBaseHandler, never())
        .steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, timeoutIntervalInMin, logCallback);
    verify(k8sRollingRollbackBaseHandler, never()).postProcess(rollbackHandlerConfig, releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackInitThrownException() throws Exception {
    InvalidRequestException thrownException = new InvalidRequestException("failed to init");

    doThrow(thrownException).when(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);

    assertThatThrownBy(()
                           -> k8sRollingDeployRollbackTaskHandler.executeTaskInternal(
                               k8sRollingDeployRollbackTaskParameters, k8sDelegateTaskParams))
        .isEqualTo(thrownException);

    verify(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);
    verify(logCallback).saveExecutionLog(getMessage(thrownException), ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackRollbackFailed() throws Exception {
    doReturn(false)
        .when(k8sRollingRollbackBaseHandler)
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);

    K8sTaskExecutionResponse response = k8sRollingDeployRollbackTaskHandler.executeTaskInternal(
        k8sRollingDeployRollbackTaskParameters, k8sDelegateTaskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(rollbackHandlerConfig.getKubernetesConfig()).isSameAs(kubernetesConfig);
    assertThat(rollbackHandlerConfig.getClient()).isNotNull();
    verify(k8sRollingRollbackBaseHandler).init(rollbackHandlerConfig, releaseName, logCallback);
    verify(k8sRollingRollbackBaseHandler)
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);
    verify(k8sRollingRollbackBaseHandler, never())
        .steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, timeoutIntervalInMin, logCallback);
    verify(k8sRollingRollbackBaseHandler, never()).postProcess(rollbackHandlerConfig, releaseName);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sRollingDeployRollbackTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalPostProcessThrownException() throws Exception {
    InvalidRequestException thrownException = new InvalidRequestException("failed post process");

    doReturn(true)
        .when(k8sRollingRollbackBaseHandler)
        .rollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, emptySet(), false, null);

    doThrow(thrownException).when(k8sRollingRollbackBaseHandler).postProcess(rollbackHandlerConfig, releaseName);

    assertThatThrownBy(()
                           -> k8sRollingDeployRollbackTaskHandler.executeTaskInternal(
                               k8sRollingDeployRollbackTaskParameters, k8sDelegateTaskParams))
        .isEqualTo(thrownException);

    verify(k8sRollingRollbackBaseHandler)
        .steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, timeoutIntervalInMin, logCallback);
    verify(k8sRollingRollbackBaseHandler).postProcess(rollbackHandlerConfig, releaseName);
    verify(logCallback).saveExecutionLog(getMessage(thrownException), ERROR, FAILURE);
    verify(logCallback, never()).saveExecutionLog(anyString(), any(LogLevel.class), eq(CommandExecutionStatus.SUCCESS));
  }
}
