/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.client;

import static io.harness.annotations.dev.HarnessTeam.CDP;
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
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.watcher.event.K8sApiEventWatcher;
import io.harness.k8s.steadystate.watcher.workload.K8sWorkloadWatcherFactory;
import io.harness.k8s.steadystate.watcher.workload.WorkloadWatcher;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sApiClientTest extends CategoryTest {
  @InjectMocks K8sApiClient k8sApiClient;

  @Mock private K8sClientHelper k8sClientHelper;
  @Mock private K8sApiEventWatcher k8sApiEventWatcher;
  @Mock private K8sWorkloadWatcherFactory workloadWatcherFactory;
  @Mock LogCallback executionLogCallback;
  @Mock WorkloadWatcher workloadWatcher;

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
    boolean result = k8sApiClient.performSteadyStateCheck(k8sSteadyStateDTO);
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
    ApiClient apiClient = new ApiClient();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(apiClient)
        .when(k8sClientHelper)
        .createKubernetesApiClient(any(K8sInfraDelegateConfig.class), any(LogCallback.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(new CompletableFuture<>())
        .when(k8sApiEventWatcher)
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
            .isErrorFrameworkEnabled(true)
            .build();
    assertThatThrownBy(() -> k8sApiClient.performSteadyStateCheck(k8sSteadyStateDTO)).isInstanceOf(ApiException.class);
    verify(k8sApiEventWatcher, times(1)).destroyRunning(anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testClientWhenStatusCheckFails() throws Exception {
    Set<String> namespaces = Set.of("ns1", "ns2");
    K8sEventWatchDTO k8sEventWatchDTO = K8sEventWatchDTO.builder().build();
    K8sStatusWatchDTO k8SStatusWatchDTO = K8sStatusWatchDTO.builder().build();
    ApiClient apiClient = new ApiClient();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(apiClient)
        .when(k8sClientHelper)
        .createKubernetesApiClient(any(K8sInfraDelegateConfig.class), any(LogCallback.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(new CompletableFuture<>())
        .when(k8sApiEventWatcher)
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
            .isErrorFrameworkEnabled(true)
            .build();
    boolean result = k8sApiClient.performSteadyStateCheck(k8sSteadyStateDTO);
    assertThat(result).isFalse();
    verify(k8sApiEventWatcher, times(1)).destroyRunning(anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuccessfulStatusCheck() throws Exception {
    Set<String> namespaces = Set.of("ns1", "ns2");
    K8sEventWatchDTO k8sEventWatchDTO = K8sEventWatchDTO.builder().build();
    K8sStatusWatchDTO k8SStatusWatchDTO = K8sStatusWatchDTO.builder().build();
    ApiClient apiClient = new ApiClient();

    doReturn(namespaces).when(k8sClientHelper).getNamespacesToMonitor(anyList(), anyString());
    doReturn(apiClient)
        .when(k8sClientHelper)
        .createKubernetesApiClient(any(K8sInfraDelegateConfig.class), any(LogCallback.class));
    doReturn(k8sEventWatchDTO)
        .when(k8sClientHelper)
        .createEventWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(k8SStatusWatchDTO)
        .when(k8sClientHelper)
        .createStatusWatchDTO(any(K8sSteadyStateDTO.class), any(ApiClient.class));
    doReturn(workloadWatcher).when(workloadWatcherFactory).getWorkloadWatcher(anyString(), anyBoolean());
    doReturn(new CompletableFuture<>())
        .when(k8sApiEventWatcher)
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
            .isErrorFrameworkEnabled(true)
            .build();
    boolean result = k8sApiClient.performSteadyStateCheck(k8sSteadyStateDTO);
    assertThat(result).isTrue();
    verify(k8sApiEventWatcher, times(1)).destroyRunning(anyList());
  }
}
