/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.k8s;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.models.V1ContainerStateBuilder;
import io.kubernetes.client.openapi.models.V1ContainerStateRunning;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatusBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodConditionBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodListBuilder;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class PodHealthCheckTest {
  @InjectMocks private PodHealthCheck podHealthCheck;
  @Mock private NamespaceService namespaceService;
  @Mock private K8sClient k8sClient;
  private static final String NAMESPACE = "default";
  private static final String ACCOUNT_ID = "123";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockNamespaceInfo();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCurrentStatusWithNoPods() {
    V1PodList v1PodList = new V1PodListBuilder().build();
    when(k8sClient.getBackstagePodList(any())).thenReturn(v1PodList);
    Optional<StatusInfo> statusInfo = podHealthCheck.getCurrentStatus(ACCOUNT_ID);
    Assert.assertEquals(StatusInfo.CurrentStatusEnum.NOT_FOUND, statusInfo.get().getCurrentStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCurrentStatusForRunning() {
    V1Pod pod1 = buildRunningPod();
    V1Pod pod2 = buildPendingPod();
    when(k8sClient.getBackstagePodList(any())).thenReturn(new V1PodList().addItemsItem(pod1).addItemsItem(pod2));
    Optional<StatusInfo> statusInfo = podHealthCheck.getCurrentStatus(ACCOUNT_ID);
    Assert.assertEquals(StatusInfo.CurrentStatusEnum.RUNNING, statusInfo.get().getCurrentStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCurrentStatusForPending() {
    V1Pod pod1 = buildPendingPod();
    V1Pod pod2 = buildPendingPod();
    when(k8sClient.getBackstagePodList(any())).thenReturn(new V1PodList().addItemsItem(pod1).addItemsItem(pod2));
    Optional<StatusInfo> statusInfo = podHealthCheck.getCurrentStatus(ACCOUNT_ID);
    Assert.assertEquals(StatusInfo.CurrentStatusEnum.PENDING, statusInfo.get().getCurrentStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCurrentStatusForFailed() {
    V1Pod pod1 = buildFailedPod();
    V1Pod pod2 = buildFailedPod();
    when(k8sClient.getBackstagePodList(any())).thenReturn(new V1PodList().addItemsItem(pod1).addItemsItem(pod2));
    Optional<StatusInfo> statusInfo = podHealthCheck.getCurrentStatus(ACCOUNT_ID);
    Assert.assertEquals(StatusInfo.CurrentStatusEnum.FAILED, statusInfo.get().getCurrentStatus());
  }

  private void mockNamespaceInfo() {
    NamespaceInfo namespace = new NamespaceInfo();
    namespace.setNamespace(NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(ACCOUNT_ID)).thenReturn(namespace);
  }

  private V1Pod buildRunningPod() {
    V1PodStatus podStatus =
        new V1PodStatusBuilder()
            .withPhase("Running")
            .withConditions(ImmutableList.of(new V1PodConditionBuilder().withType("Ready").withStatus("True").build()))
            .withContainerStatuses(ImmutableList.of(
                new V1ContainerStatusBuilder()
                    .withState(new V1ContainerStateBuilder().withRunning(new V1ContainerStateRunning()).build())
                    .build()))
            .build();
    return new V1PodBuilder().withStatus(podStatus).build();
  }

  private V1Pod buildPendingPod() {
    V1PodStatus podStatus =
        new V1PodStatusBuilder()
            .withPhase("Pending")
            .withConditions(ImmutableList.of(new V1PodConditionBuilder()
                                                 .withType("ContainersReady")
                                                 .withStatus("False")
                                                 .withMessage("containers not ready")
                                                 .build()))
            .withContainerStatuses(ImmutableList.of(
                new V1ContainerStatusBuilder()
                    .withState(new V1ContainerStateBuilder().withWaiting(new V1ContainerStateWaiting()).build())
                    .withLastState(new V1ContainerStateBuilder().withWaiting(new V1ContainerStateWaiting()).build())
                    .build()))
            .build();
    return new V1PodBuilder().withStatus(podStatus).build();
  }

  private V1Pod buildFailedPod() {
    V1PodStatus podStatus =
        new V1PodStatusBuilder()
            .withPhase("Failed")
            .withConditions(ImmutableList.of(new V1PodConditionBuilder().withType("Ready").withStatus("False").build()))
            .withContainerStatuses(ImmutableList.of(
                new V1ContainerStatusBuilder()
                    .withState(new V1ContainerStateBuilder()
                                   .withTerminated(new V1ContainerStateTerminated().message("CrashLoopBackOff"))
                                   .build())
                    .withLastState(
                        new V1ContainerStateBuilder().withTerminated(new V1ContainerStateTerminated()).build())
                    .build()))
            .build();
    return new V1PodBuilder().withStatus(podStatus).build();
  }
}
