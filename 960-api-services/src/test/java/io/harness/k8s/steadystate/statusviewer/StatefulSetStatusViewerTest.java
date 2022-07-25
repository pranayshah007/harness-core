/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTIAL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTITIONED_PARTIAL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTITIONED_SUCCESSFUL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.SUCCESSFUL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.UNSUPPORTED_STRATEGY_TYPE;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.WAITING_FOR_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.WAITING_FOR_UPDATE;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1RollingUpdateStatefulSetStrategy;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetSpec;
import io.kubernetes.client.openapi.models.V1StatefulSetStatus;
import io.kubernetes.client.openapi.models.V1StatefulSetUpdateStrategy;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class StatefulSetStatusViewerTest extends CategoryTest {
  @InjectMocks private StatefulSetStatusViewer statusViewer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testUnsupportedWorkloadUpdateStrategy() {
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetSpec statefulSetSpec = new V1StatefulSetSpec();
    statefulSetSpec.setUpdateStrategy(new V1StatefulSetUpdateStrategy().type("OnDelete"));
    statefulSet.setSpec(statefulSetSpec);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isTrue();
    assertThat(responseDTO.getMessage()).isEqualTo(UNSUPPORTED_STRATEGY_TYPE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testStatefulSetWaitingForUpdate() {
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetStatus status = new V1StatefulSetStatus();
    status.setObservedGeneration(0L);
    statefulSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(WAITING_FOR_UPDATE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testStatefulSetWaitingForRollout() {
    int desiredReplicas = 2;
    int readyReplicas = 1;
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetStatus status = new V1StatefulSetStatus();
    V1StatefulSetSpec spec = new V1StatefulSetSpec();
    spec.setReplicas(desiredReplicas);
    status.setReadyReplicas(readyReplicas);
    statefulSet.setSpec(spec);
    statefulSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(WAITING_FOR_ROLLOUT, desiredReplicas - readyReplicas));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartialPartitionedRollout() {
    int partition = 1;
    int desiredReplicas = 3;
    int updatedReplicas = 1;
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetSpec spec = new V1StatefulSetSpec();
    V1StatefulSetUpdateStrategy updateStrategy =
        new V1StatefulSetUpdateStrategy()
            .rollingUpdate(new V1RollingUpdateStatefulSetStrategy().partition(partition))
            .type("RollingUpdate");
    V1StatefulSetStatus status = new V1StatefulSetStatus();
    status.setUpdatedReplicas(updatedReplicas);
    status.setReadyReplicas(desiredReplicas);
    spec.setUpdateStrategy(updateStrategy);
    spec.setReplicas(desiredReplicas);
    statefulSet.setSpec(spec);
    statefulSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage())
        .isEqualTo(String.format(PARTITIONED_PARTIAL_ROLLOUT, updatedReplicas, desiredReplicas - partition));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuccessfulPartitionedRollout() {
    int partition = 1;
    int desiredReplicas = 3;
    int updatedReplicas = 2;
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetSpec spec = new V1StatefulSetSpec();
    V1StatefulSetUpdateStrategy updateStrategy =
        new V1StatefulSetUpdateStrategy()
            .rollingUpdate(new V1RollingUpdateStatefulSetStrategy().partition(partition))
            .type("RollingUpdate");
    V1StatefulSetStatus status = new V1StatefulSetStatus();
    status.setUpdatedReplicas(updatedReplicas);
    status.setReadyReplicas(desiredReplicas);
    spec.setUpdateStrategy(updateStrategy);
    spec.setReplicas(desiredReplicas);
    statefulSet.setSpec(spec);
    statefulSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isTrue();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(PARTITIONED_SUCCESSFUL_ROLLOUT, updatedReplicas));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartialRollout() {
    String currentRevision = "2";
    String updateRevision = "3";
    int updatedReplicas = 2;
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetStatus status = new V1StatefulSetStatus();

    status.setCurrentRevision(currentRevision);
    status.setUpdateRevision(updateRevision);
    status.setUpdatedReplicas(updatedReplicas);
    statefulSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(PARTIAL_ROLLOUT, updatedReplicas, updateRevision));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuccessfulRollout() {
    int currentReplicas = 2;
    String currentRevision = "4";
    V1StatefulSet statefulSet = new V1StatefulSet();
    V1StatefulSetStatus status = new V1StatefulSetStatus();
    status.setCurrentRevision(currentRevision);
    status.setCurrentReplicas(currentReplicas);
    statefulSet.setStatus(status);
    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(statefulSet);
    assertThat(responseDTO.isDone()).isTrue();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(SUCCESSFUL_ROLLOUT, currentReplicas, currentRevision));
  }
}
