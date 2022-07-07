/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.DONE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.PARTIALLY_AVAILABLE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.PARTIALLY_UPDATED;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.UNSUPPORTED_STRATEGY_TYPE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.WAITING;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetSpec;
import io.kubernetes.client.openapi.models.V1DaemonSetStatus;
import io.kubernetes.client.openapi.models.V1DaemonSetUpdateStrategy;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class DaemonSetStatusViewerTest extends CategoryTest {
  @InjectMocks private DaemonSetStatusViewer statusViewer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testUnsupportedWorkloadUpdateStrategy() {
    V1DaemonSet daemonSet = new V1DaemonSet();
    V1DaemonSetSpec daemonSetSpec = new V1DaemonSetSpec();
    daemonSetSpec.setUpdateStrategy(new V1DaemonSetUpdateStrategy().type("OnDelete"));
    daemonSet.setSpec(daemonSetSpec);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(daemonSet);
    assertThat(responseDTO.isDone()).isTrue();
    assertThat(responseDTO.getMessage()).isEqualTo(UNSUPPORTED_STRATEGY_TYPE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testWorkloadWaitingForRollout() {
    V1DaemonSet daemonSet = new V1DaemonSet();
    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(daemonSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(WAITING);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartiallyUpdatedWorkload() {
    int updated = 1;
    int desired = 2;
    String name = "ds1";
    V1DaemonSet daemonSet = new V1DaemonSet();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DaemonSetStatus status = new V1DaemonSetStatus();

    meta.setName(name);
    meta.setGeneration(1L);
    status.setObservedGeneration(2L);
    status.setUpdatedNumberScheduled(updated);
    status.setDesiredNumberScheduled(desired);
    daemonSet.setMetadata(meta);
    daemonSet.setStatus(status);

    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(daemonSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(PARTIALLY_UPDATED, name, updated, desired));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartiallyAvailableWorkload() {
    int available = 1;
    int desired = 2;

    String name = "ds1";
    V1DaemonSet daemonSet = new V1DaemonSet();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DaemonSetStatus status = new V1DaemonSetStatus();

    meta.setName(name);
    meta.setGeneration(1L);
    status.setObservedGeneration(2L);
    status.setNumberAvailable(available);
    status.setUpdatedNumberScheduled(desired);
    status.setDesiredNumberScheduled(desired);
    daemonSet.setMetadata(meta);
    daemonSet.setStatus(status);
    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(daemonSet);
    assertThat(responseDTO.isDone()).isFalse();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(PARTIALLY_AVAILABLE, name, available, desired));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testAvailableDeployment() {
    String name = "ds1";
    V1DaemonSet daemonSet = new V1DaemonSet();
    V1ObjectMeta meta = new V1ObjectMeta();
    V1DaemonSetStatus status = new V1DaemonSetStatus();

    meta.setName(name);
    meta.setGeneration(1L);
    status.setObservedGeneration(2L);
    status.setUpdatedNumberScheduled(2);
    status.setNumberAvailable(2);
    status.setDesiredNumberScheduled(2);
    daemonSet.setMetadata(meta);
    daemonSet.setStatus(status);
    K8ApiResponseDTO responseDTO = statusViewer.extractRolloutStatus(daemonSet);
    assertThat(responseDTO.isDone()).isTrue();
    assertThat(responseDTO.getMessage()).isEqualTo(String.format(DONE, name, 2));
  }
}
