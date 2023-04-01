/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class K8sEventFilterTest extends CategoryTest {
  @Mock private CoreV1Api coreV1Api = mock(CoreV1Api.class);

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestKindNull() {
    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().name("k8s-pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, new CoreV1Api(), "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withName("k8s-pod").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestKindNotPod() {
    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().kind("Event").name("k8s-pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, new CoreV1Api(), "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withKind("Event").withName("k8s-pod").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestKindNotPodNegative() {
    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().kind("pod").name("k8s-pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, new CoreV1Api(), "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withKind("Event").withName("k8s-pod").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isFalse();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestNameNull() {
    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().kind("pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, new CoreV1Api(), "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withKind("Event").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestKindPod() throws ApiException {
    V1Pod pod = getPodWithSuccessSate("releaseName");

    when(coreV1Api.readNamespacedPod(any(String.class), any(String.class), any())).thenReturn(pod);
    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().kind("pod").name("k8s-pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, coreV1Api, "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withKind("pod").withName("k8s-pod-1").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTestKindPodNegative() throws ApiException {
    V1Pod pod = getPodWithSuccessSate("releaseName1");

    when(coreV1Api.readNamespacedPod(any(), any(), any())).thenReturn(pod);

    List<KubernetesResourceId> resourceIds =
        Arrays.asList(KubernetesResourceId.builder().kind("pod").name("k8s-pod").namespace("namespace").build());
    K8sEventFilter k8sEventFilter = new K8sEventFilter(resourceIds, coreV1Api, "namespace", "releaseName");

    V1ObjectReference eventRef = new V1ObjectReferenceBuilder().withKind("Event").withName("k8s-pod-1").build();
    CoreV1Event event = new CoreV1EventBuilder().withInvolvedObject(eventRef).build();
    assertThat(k8sEventFilter.test(event)).isFalse();
  }

  private V1Pod getPodWithSuccessSate(String releaseName) {
    return new V1Pod()
        .status(new V1PodStatusBuilder().withPodIP("podIP").build())
        .metadata(new V1ObjectMetaBuilder()
                      .addToLabels(HarnessLabels.releaseName, releaseName)
                      .withName("nginxPod")
                      .withNamespace("namespace")
                      .withUid("podId")
                      .build());
  }
}
