/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.client;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8sClientHelperTest extends CategoryTest {
  @InjectMocks K8sClientHelper k8sClientHelper;

  @Mock private KubernetesHelperService kubernetesHelperService;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock private KubernetesConfig kubernetesConfig;

  private List<KubernetesResourceId> resourceIds;
  private K8sSteadyStateDTO k8sSteadyStateDTO;
  private ApiClient apiClient;
  private Kubectl client;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    resourceIds = List.of(KubernetesResourceId.builder().name("some-resource-name").namespace("ns1").build(),
        KubernetesResourceId.builder().name("some-other-resource-name").namespace("ns1").build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    k8sSteadyStateDTO = K8sSteadyStateDTO.builder()
                            .resourceIds(resourceIds)
                            .k8sDelegateTaskParams(k8sDelegateTaskParams)
                            .request(K8sRollingDeployRequest.builder().releaseName("releaseName").build())
                            .build();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEventWatchInfoFormat() {
    K8sEventWatchDTO eventWatchDTO = k8sClientHelper.createEventWatchDTO(k8sSteadyStateDTO, apiClient);
    assertThat(eventWatchDTO.getEventInfoFormat()).isEqualTo("%-7s: %-24s   %s");
    assertThat(eventWatchDTO.getResourceIds()).isEqualTo(resourceIds);

    eventWatchDTO = k8sClientHelper.createEventWatchDTO(k8sSteadyStateDTO, client);
    assertThat(eventWatchDTO.getEventInfoFormat()).isEqualTo("%-7s: %-24s   %s");
    assertThat(eventWatchDTO.getResourceIds()).isEqualTo(resourceIds);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testStatusInfoFormat() {
    K8sStatusWatchDTO statusWatchDTO = k8sClientHelper.createStatusWatchDTO(k8sSteadyStateDTO, apiClient);
    assertThat(statusWatchDTO.getStatusFormat()).isEqualTo("%n%-7s: %-24s   %s");

    statusWatchDTO = k8sClientHelper.createStatusWatchDTO(k8sSteadyStateDTO, client);
    assertThat(statusWatchDTO.getStatusFormat()).isEqualTo("%n%-7s: %-24s   %s");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testNamespaceCollation() {
    Set<String> namespaces = k8sClientHelper.getNamespacesToMonitor(resourceIds, "ns2");
    assertThat(namespaces.size()).isEqualTo(2);
    assertThat(namespaces.contains("ns1")).isTrue();
    assertThat(namespaces.contains("ns2")).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testApiClientCreation() {
    ApiClient apiCLient = new ApiClient();
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(any(K8sInfraDelegateConfig.class), any(LogCallback.class));
    doReturn(apiCLient).when(kubernetesHelperService).getApiClient(eq(kubernetesConfig));

    LogCallback logCallback = mock(LogCallback.class);
    K8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    ApiClient generatedClient = k8sClientHelper.createKubernetesApiClient(k8sInfraDelegateConfig, logCallback);
    assertThat(generatedClient).isEqualTo(apiCLient);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSteadyStateInfoLogger() {
    LogCallback logCallback = mock(LogCallback.class);

    List<KubernetesResourceId> workloads = List.of(KubernetesResourceId.builder().kind("kind1").name("name1").build(),
        KubernetesResourceId.builder().kind("kind2").name("name2").build());
    Set<String> namespaces = Set.of("ns1");

    k8sClientHelper.logSteadyStateInfo(workloads, namespaces, logCallback);
    verify(logCallback, times(1))
        .saveExecutionLog(eq("Waiting for following workloads to finish: [kind1/name1, kind2/name2]"));
    verify(logCallback, times(1)).saveExecutionLog(eq("Watching following namespaces for events: [ns1]"));
  }
}
