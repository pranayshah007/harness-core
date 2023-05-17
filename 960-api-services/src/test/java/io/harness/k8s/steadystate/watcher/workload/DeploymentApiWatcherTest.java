/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1DeploymentSpecBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class DeploymentApiWatcherTest extends ApiWatcherTestBase<V1Deployment> {
  @Mock private DeploymentStatusViewer statusViewer;

  @InjectMocks private DeploymentApiWatcher deploymentApiWatcher;

  private final V1ObjectMeta metadata = new V1ObjectMetaBuilder().withName("test-resource").build();
  private final V1Deployment successfulDeployment =
      new V1DeploymentBuilder()
          .withMetadata(metadata)
          .withSpec(new V1DeploymentSpecBuilder().withReplicas(1).build()) // added to not be equal with failed one
          .build();
  private final V1Deployment failedDeployment = new V1DeploymentBuilder()
                                                    .withMetadata(metadata)
                                                    .withSpec(new V1DeploymentSpecBuilder().withReplicas(3).build())
                                                    .build();

  @Before
  public void setup() {
    doReturn(K8ApiResponseDTO.builder().isFailed(true).message("Failed to reach steady state").build())
        .when(statusViewer)
        .extractRolloutStatus(failedDeployment);
    doReturn(K8ApiResponseDTO.builder().isDone(true).build())
        .when(statusViewer)
        .extractRolloutStatus(successfulDeployment);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusSuccessful() {
    prepareWatchClient(DeploymentApiWatcher.v1DeploymentType, createAddedWatchResponse(successfulDeployment));
    boolean result =
        deploymentApiWatcher.watchRolloutStatusInternal(createTestDTO(), createTestResourceId(), logCallback);
    assertThat(result).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusSuccessfulModifiedEvent() {
    prepareWatchClient(DeploymentApiWatcher.v1DeploymentType, createModifiedWatchResponse(successfulDeployment));
    boolean result =
        deploymentApiWatcher.watchRolloutStatusInternal(createTestDTO(), createTestResourceId(), logCallback);
    assertThat(result).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusFailed() {
    prepareWatchClient(DeploymentApiWatcher.v1DeploymentType, createAddedWatchResponse(failedDeployment));
    assertThatThrownBy(
        () -> deploymentApiWatcher.watchRolloutStatusInternal(createTestDTO(), createTestResourceId(), logCallback))
        .isInstanceOf(KubernetesCliTaskRuntimeException.class)
        .hasStackTraceContaining("Failed to reach steady state");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusDeleted() {
    prepareWatchClient(DeploymentApiWatcher.v1DeploymentType, createDeletedWatchResponse(failedDeployment));
    assertThatThrownBy(
        () -> deploymentApiWatcher.watchRolloutStatusInternal(createTestDTO(), createTestResourceId(), logCallback))
        .isInstanceOf(KubernetesCliTaskRuntimeException.class)
        .hasStackTraceContaining("object has been deleted");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusUnknownEvent() {
    prepareWatchClient(DeploymentApiWatcher.v1DeploymentType, createUnknownWatchResponse(failedDeployment));
    boolean result =
        deploymentApiWatcher.watchRolloutStatusInternal(createTestDTO(), createTestResourceId(), logCallback);

    // This is the result of the processEvent method returning false as part of the mock.
    // In real scenario K8sWatchClient#waitOnCondition will continue to watch for resource and will not
    // return while a known event will be received and resource failed. If logic change this test need to be
    assertThat(result).isFalse();
  }

  private KubernetesResourceId createTestResourceId() {
    return KubernetesResourceId.builder().kind("Deployment").name("test-resource").build();
  }
}