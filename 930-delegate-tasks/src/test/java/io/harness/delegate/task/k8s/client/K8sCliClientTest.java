/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.client;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.watcher.event.K8sCliEventWatcher;
import io.harness.k8s.steadystate.watcher.workload.K8sWorkloadWatcherFactory;
import io.harness.k8s.steadystate.watcher.workload.WorkloadWatcher;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiException;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.StartedProcess;

public class K8sCliClientTest extends CategoryTest {
  @InjectMocks K8sCliClient k8sCliClient;

  @Mock private K8sClientHelper k8sClientHelper;
  @Mock private K8sCliEventWatcher k8sCliEventWatcher;
  @Mock private K8sWorkloadWatcherFactory workloadWatcherFactory;
  @Mock LogCallback executionLogCallback;
  @Mock WorkloadWatcher workloadWatcher;
  @Mock StartedProcess startedProcess;
  @Mock Kubectl cliClient;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any(LogLevel.class));
    doNothing()
        .when(executionLogCallback)
        .saveExecutionLog(anyString(), any(LogLevel.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSteadyStateSkippingWhenNoWorkloadsAreGiven() throws Exception {
    K8sSteadyStateDTO k8sSteadyStateDTO = K8sSteadyStateDTO.builder().resourceIds(Collections.emptyList()).build();
    boolean result = k8sCliClient.performSteadyStateCheck(k8sSteadyStateDTO);
    assertThat(result).isTrue();
    verify(k8sClientHelper, times(0))
        .createKubernetesApiClient(any(K8sInfraDelegateConfig.class), any(LogCallback.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testClientWhenStatusCheckThrowsException() throws Exception {
    Set<String> namespaces = Set.of("ns1", "ns2");
    K8sEventWatchDTO k8sEventWatchDTO = K8sEventWatchDTO.builder().build();
    K8sStatusWatchDTO k8SStatusWatchDTO = K8sStatusWatchDTO.builder().build();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(cliClient).when(k8sClientHelper).createKubernetesCliClient(any(K8sDelegateTaskParams.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(startedProcess)
        .when(k8sCliEventWatcher)
        .watchForEvents(anyString(), any(K8sEventWatchDTO.class), any(LogCallback.class));
    doThrow(ApiException.class)
        .when(workloadWatcher)
        .watchRolloutStatus(any(K8sStatusWatchDTO.class), any(KubernetesResourceId.class), any(LogCallback.class));

    K8sSteadyStateDTO k8sSteadyStateDTO =
        K8sSteadyStateDTO.builder()
            .resourceIds(Collections.singletonList(KubernetesResourceId.builder().kind("Deployment").build()))
            .executionLogCallback(executionLogCallback)
            .request(K8sRollingDeployRequest.builder()
                         .k8sInfraDelegateConfig(DirectK8sInfraDelegateConfig.builder().build())
                         .build())
            .namespace("ns1")
            .k8sDelegateTaskParams(K8sDelegateTaskParams.builder().build())
            .isErrorFrameworkEnabled(true)
            .build();
    assertThatThrownBy(() -> k8sCliClient.performSteadyStateCheck(k8sSteadyStateDTO)).isInstanceOf(ApiException.class);
    verify(k8sCliEventWatcher, times(1)).destroyRunning(anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testClientWhenStatusCheckFails() throws Exception {
    Set<String> namespaces = Set.of("ns1", "ns2");
    K8sEventWatchDTO k8sEventWatchDTO = K8sEventWatchDTO.builder().build();
    K8sStatusWatchDTO k8SStatusWatchDTO = K8sStatusWatchDTO.builder().build();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(cliClient).when(k8sClientHelper).createKubernetesCliClient(any(K8sDelegateTaskParams.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(startedProcess)
        .when(k8sCliEventWatcher)
        .watchForEvents(anyString(), any(K8sEventWatchDTO.class), any(LogCallback.class));
    doReturn(false)
        .when(workloadWatcher)
        .watchRolloutStatus(any(K8sStatusWatchDTO.class), any(KubernetesResourceId.class), any(LogCallback.class));

    K8sSteadyStateDTO k8sSteadyStateDTO =
        K8sSteadyStateDTO.builder()
            .resourceIds(Collections.singletonList(KubernetesResourceId.builder().kind("Deployment").build()))
            .executionLogCallback(executionLogCallback)
            .request(K8sRollingDeployRequest.builder()
                         .k8sInfraDelegateConfig(DirectK8sInfraDelegateConfig.builder().build())
                         .build())
            .namespace("ns1")
            .k8sDelegateTaskParams(K8sDelegateTaskParams.builder().build())
            .isErrorFrameworkEnabled(true)
            .build();
    boolean result = k8sCliClient.performSteadyStateCheck(k8sSteadyStateDTO);
    assertThat(result).isFalse();
    verify(k8sCliEventWatcher, times(1)).destroyRunning(anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuccessfulStatusCheck() throws Exception {
    Set<String> namespaces = Set.of("ns1", "ns2");
    K8sEventWatchDTO k8sEventWatchDTO = K8sEventWatchDTO.builder().build();
    K8sStatusWatchDTO k8SStatusWatchDTO = K8sStatusWatchDTO.builder().build();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(cliClient).when(k8sClientHelper).createKubernetesCliClient(any(K8sDelegateTaskParams.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(Kubectl.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(startedProcess)
        .when(k8sCliEventWatcher)
        .watchForEvents(anyString(), any(K8sEventWatchDTO.class), any(LogCallback.class));
    doReturn(true)
        .when(workloadWatcher)
        .watchRolloutStatus(any(K8sStatusWatchDTO.class), any(KubernetesResourceId.class), any(LogCallback.class));

    K8sSteadyStateDTO k8sSteadyStateDTO =
        K8sSteadyStateDTO.builder()
            .resourceIds(Collections.singletonList(KubernetesResourceId.builder().kind("Deployment").build()))
            .executionLogCallback(executionLogCallback)
            .request(K8sRollingDeployRequest.builder()
                         .k8sInfraDelegateConfig(DirectK8sInfraDelegateConfig.builder().build())
                         .build())
            .namespace("ns1")
            .k8sDelegateTaskParams(K8sDelegateTaskParams.builder().build())
            .isErrorFrameworkEnabled(true)
            .build();
    boolean result = k8sCliClient.performSteadyStateCheck(k8sSteadyStateDTO);
    assertThat(result).isTrue();
    verify(k8sCliEventWatcher, times(1)).destroyRunning(anyList());
  }
}
