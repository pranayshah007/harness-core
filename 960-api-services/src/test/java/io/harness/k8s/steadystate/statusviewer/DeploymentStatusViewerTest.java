/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_AVAILABLE;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_ROLLED_OUT;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_UPDATED;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PROGRESS_DEADLINE_EXCEEDED;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.WAITING_FOR_UPDATE;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class DeploymentStatusViewerTest extends CategoryTest {
  @InjectMocks private DeploymentStatusViewer statusViewer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testProgressingDeployment() {
    V1Deployment deployment = new V1Deployment();
    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(deployment);
    assertThat(response.isDone()).isFalse();
    assertThat(response.getMessage()).isEqualTo(WAITING_FOR_UPDATE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDeploymentProgressDeadlineExceeded() {
    V1Deployment deployment = new V1Deployment();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DeploymentStatus status = new V1DeploymentStatus();

    List<V1DeploymentCondition> deploymentConditions =
        List.of(new V1DeploymentCondition().type("Progressing").reason("ProgressDeadlineExceeded"));

    meta.setGeneration(1L);
    meta.setName("dep1");
    status.setObservedGeneration(2L);
    status.setConditions(deploymentConditions);
    deployment.setMetadata(meta);
    deployment.setStatus(status);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(deployment);
    assertThat(response.isFailed()).isTrue();
    assertThat(response.getMessage()).isEqualTo(String.format(PROGRESS_DEADLINE_EXCEEDED, meta.getName()));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartiallyUpdatedDeployment() {
    int replicas = 2;
    int updated = 1;
    V1Deployment deployment = new V1Deployment();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DeploymentStatus status = new V1DeploymentStatus();
    V1DeploymentSpec spec = new V1DeploymentSpec();

    meta.setGeneration(1L);
    meta.setName("dep1");
    status.setObservedGeneration(2L);
    spec.setReplicas(replicas);
    status.setUpdatedReplicas(updated);
    deployment.setMetadata(meta);
    deployment.setStatus(status);
    deployment.setSpec(spec);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(deployment);
    assertThat(response.isFailed()).isFalse();
    assertThat(response.getMessage()).isEqualTo(String.format(PARTIALLY_UPDATED, meta.getName(), updated, replicas));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartiallyRolledOutDeployment() {
    int desiredReplicas = 1;
    int updatedReplicas = 1;
    int currentReplicas = 2;
    V1Deployment deployment = new V1Deployment();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DeploymentStatus status = new V1DeploymentStatus();
    V1DeploymentSpec spec = new V1DeploymentSpec();

    meta.setGeneration(1L);
    meta.setName("dep1");
    status.setObservedGeneration(2L);
    status.setUpdatedReplicas(updatedReplicas);
    status.setReplicas(currentReplicas);
    spec.setReplicas(desiredReplicas);

    deployment.setMetadata(meta);
    deployment.setStatus(status);
    deployment.setSpec(spec);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(deployment);
    assertThat(response.isFailed()).isFalse();
    assertThat(response.getMessage())
        .isEqualTo(String.format(PARTIALLY_ROLLED_OUT, meta.getName(), currentReplicas - updatedReplicas));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartiallyAvailableDeployment() {
    int desiredReplicas = 1;
    int updatedReplicas = 1;
    int currentReplicas = 1;
    int availableReplicas = 0;
    V1Deployment deployment = new V1Deployment();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DeploymentStatus status = new V1DeploymentStatus();
    V1DeploymentSpec spec = new V1DeploymentSpec();

    meta.setGeneration(1L);
    meta.setName("dep1");
    status.setObservedGeneration(2L);
    status.setUpdatedReplicas(updatedReplicas);
    status.setReplicas(currentReplicas);
    status.setAvailableReplicas(availableReplicas);
    spec.setReplicas(desiredReplicas);

    deployment.setMetadata(meta);
    deployment.setStatus(status);
    deployment.setSpec(spec);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(deployment);
    assertThat(response.isFailed()).isFalse();
    assertThat(response.getMessage())
        .isEqualTo(String.format(PARTIALLY_AVAILABLE, meta.getName(), availableReplicas, updatedReplicas));
  }
}
