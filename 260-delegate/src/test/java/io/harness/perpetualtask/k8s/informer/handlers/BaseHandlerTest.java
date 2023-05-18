/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer.handlers;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.watch.K8sObjectReference;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BaseHandlerTest extends CategoryTest {
  private EventPublisher eventPublisher;

  private ClusterDetails clusterDetails;
  private K8sWatchEvent expectedClusterDetailsProto;

  @Before
  public void setUp() throws Exception {
    eventPublisher = mock(EventPublisher.class);
    clusterDetails = ClusterDetails.builder()
                         .clusterId("test-cluster-id")
                         .cloudProviderId("test-cloud-provider-id")
                         .clusterName("test-cluster-name")
                         .kubeSystemUid("test-cluster-uid")
                         .build();
    expectedClusterDetailsProto = K8sWatchEvent.newBuilder()
                                      .setClusterId("test-cluster-id")
                                      .setCloudProviderId("test-cloud-provider-id")
                                      .setClusterName("test-cluster-name")
                                      .setKubeSystemUid("test-cluster-uid")
                                      .build();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishWhenOnAddForControlledResource() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    handler.onAdd(new V1ReplicaSetBuilder()
                      .withKind("ReplicaSet")
                      .withApiVersion("apps/v1")
                      .withNewMetadata()
                      .withName("nginx-ingress-controller-589f8749b5")
                      .withNamespace("ingress-nginx")
                      .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                      .withResourceVersion("200777582")
                      .addNewOwnerReference()
                      .withApiVersion("apps/v1")
                      .withBlockOwnerDeletion(true)
                      .withController(true)
                      .withKind("Deployment")
                      .withName("nginx-ingress-controller")
                      .withUid("e1f53198-43a2-11e9-9fa6-4201ac10650a")
                      .endOwnerReference()
                      .endMetadata()
                      .build());
    verifyZeroInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishWhenOnAddForTopLevelResource() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    V1ReplicaSet replicaSet = new V1ReplicaSetBuilder()
                                  .withKind("ReplicaSet")
                                  .withApiVersion("apps/v1")
                                  .withNewMetadata()
                                  .withName("nginx-ingress-controller-589f8749b5")
                                  .withNamespace("ingress-nginx")
                                  .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                  .withResourceVersion("200777582")
                                  .endMetadata()
                                  .build();
    handler.onAdd(replicaSet);

    verify(eventPublisher)
        .publishMessage(eq(K8sWatchEvent.newBuilder(expectedClusterDetailsProto)
                                .mergeFrom(K8sWatchEvent.newBuilder()
                                               .setType(K8sWatchEvent.Type.TYPE_ADDED)
                                               .setResourceRef(K8sObjectReference.newBuilder()
                                                                   .setKind("ReplicaSet")
                                                                   .setName("nginx-ingress-controller-589f8749b5")
                                                                   .setNamespace("ingress-nginx")
                                                                   .setUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                                                   .build())
                                               .setNewResourceVersion("200777582")
                                               .setNewResourceYaml("apiVersion: apps/v1\n"
                                                   + "kind: ReplicaSet\n"
                                                   + "metadata:\n"
                                                   + "  name: nginx-ingress-controller-589f8749b5\n"
                                                   + "  namespace: ingress-nginx\n"
                                                   + "  uid: 228947ad-3cba-11ea-886a-4201ac10650a\n")
                                               .setDescription("ReplicaSet created")
                                               .build())
                                .build()),
            any(), anyMapOf(String.class, String.class));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishWhenOnUpdateForControlledResource() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    V1ReplicaSet v1ReplicaSetOld = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777582")
                                       .addNewOwnerReference()
                                       .withApiVersion("apps/v1")
                                       .withBlockOwnerDeletion(true)
                                       .withController(true)
                                       .withKind("Deployment")
                                       .withName("nginx-ingress-controller")
                                       .withUid("e1f53198-43a2-11e9-9fa6-4201ac10650a")
                                       .endOwnerReference()
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(1)
                                       .endSpec()
                                       .build();
    V1ReplicaSet v1ReplicaSetNew = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777583")
                                       .addNewOwnerReference()
                                       .withApiVersion("apps/v1")
                                       .withBlockOwnerDeletion(true)
                                       .withController(true)
                                       .withKind("Deployment")
                                       .withName("nginx-ingress-controller")
                                       .withUid("e1f53198-43a2-11e9-9fa6-4201ac10650a")
                                       .endOwnerReference()
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(3)
                                       .endSpec()
                                       .build();
    handler.onUpdate(v1ReplicaSetOld, v1ReplicaSetNew);
    verifyZeroInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishWhenOnUpdateIfNoSpecChange() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    V1ReplicaSet v1ReplicaSetOld = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777582")
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(1)
                                       .endSpec()
                                       .withNewStatus()
                                       .withAvailableReplicas(0)
                                       .endStatus()
                                       .build();
    V1ReplicaSet v1ReplicaSetNew = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777583")
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(1)
                                       .endSpec()
                                       .withNewStatus()
                                       .withAvailableReplicas(1)
                                       .endStatus()
                                       .build();
    handler.onUpdate(v1ReplicaSetOld, v1ReplicaSetNew);
    verifyZeroInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishWhenOnUpdateForTopLevelResourceSpecChange() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    V1ReplicaSet v1ReplicaSetOld = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777582")
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(1)
                                       .endSpec()
                                       .withNewStatus()
                                       .withAvailableReplicas(1)
                                       .endStatus()
                                       .build();
    V1ReplicaSet v1ReplicaSetNew = new V1ReplicaSetBuilder()
                                       .withKind("ReplicaSet")
                                       .withApiVersion("apps/v1")
                                       .withNewMetadata()
                                       .withName("nginx-ingress-controller-589f8749b5")
                                       .withNamespace("ingress-nginx")
                                       .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                       .withResourceVersion("200777583")
                                       .endMetadata()
                                       .withNewSpec()
                                       .withReplicas(3)
                                       .endSpec()
                                       .withNewStatus()
                                       .withAvailableReplicas(1)
                                       .endStatus()
                                       .build();
    handler.onUpdate(v1ReplicaSetOld, v1ReplicaSetNew);
    verify(eventPublisher)
        .publishMessage(eq(K8sWatchEvent.newBuilder(expectedClusterDetailsProto)
                                .mergeFrom(K8sWatchEvent.newBuilder()
                                               .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                                               .setResourceRef(K8sObjectReference.newBuilder()
                                                                   .setKind("ReplicaSet")
                                                                   .setName("nginx-ingress-controller-589f8749b5")
                                                                   .setNamespace("ingress-nginx")
                                                                   .setUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                                                   .build())
                                               .setOldResourceVersion("200777582")
                                               .setNewResourceVersion("200777583")
                                               .setOldResourceYaml("apiVersion: apps/v1\n"
                                                   + "kind: ReplicaSet\n"
                                                   + "metadata:\n"
                                                   + "  name: nginx-ingress-controller-589f8749b5\n"
                                                   + "  namespace: ingress-nginx\n"
                                                   + "  uid: 228947ad-3cba-11ea-886a-4201ac10650a\n"
                                                   + "spec:\n"
                                                   + "  replicas: 1\n")
                                               .setNewResourceYaml("apiVersion: apps/v1\n"
                                                   + "kind: ReplicaSet\n"
                                                   + "metadata:\n"
                                                   + "  name: nginx-ingress-controller-589f8749b5\n"
                                                   + "  namespace: ingress-nginx\n"
                                                   + "  uid: 228947ad-3cba-11ea-886a-4201ac10650a\n"
                                                   + "spec:\n"
                                                   + "  replicas: 3\n")
                                               .setDescription("ReplicaSet updated")
                                               .build())
                                .build()),
            any(), anyMapOf(String.class, String.class));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishWhenOnDeleteForControlledResource() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    handler.onDelete(new V1ReplicaSetBuilder()
                         .withKind("ReplicaSet")
                         .withApiVersion("apps/v1")
                         .withNewMetadata()
                         .withName("nginx-ingress-controller-589f8749b5")
                         .withNamespace("ingress-nginx")
                         .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                         .withResourceVersion("200777582")
                         .addNewOwnerReference()
                         .withApiVersion("apps/v1")
                         .withBlockOwnerDeletion(true)
                         .withController(true)
                         .withKind("Deployment")
                         .withName("nginx-ingress-controller")
                         .withUid("e1f53198-43a2-11e9-9fa6-4201ac10650a")
                         .endOwnerReference()
                         .endMetadata()
                         .build(),
        false);
    verifyZeroInteractions(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishWhenOnDeleteForTopLevelResource() throws Exception {
    V1ReplicaSetHandler handler = new V1ReplicaSetHandler(eventPublisher, clusterDetails);
    V1ReplicaSet replicaSet = new V1ReplicaSetBuilder()
                                  .withKind("ReplicaSet")
                                  .withApiVersion("apps/v1")
                                  .withNewMetadata()
                                  .withName("nginx-ingress-controller-589f8749b5")
                                  .withNamespace("ingress-nginx")
                                  .withUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                  .withResourceVersion("200777582")
                                  .endMetadata()
                                  .build();
    handler.onDelete(replicaSet, false);
    verify(eventPublisher)
        .publishMessage(eq(K8sWatchEvent.newBuilder(expectedClusterDetailsProto)
                                .mergeFrom(K8sWatchEvent.newBuilder()
                                               .setType(K8sWatchEvent.Type.TYPE_DELETED)
                                               .setResourceRef(K8sObjectReference.newBuilder()
                                                                   .setKind("ReplicaSet")
                                                                   .setName("nginx-ingress-controller-589f8749b5")
                                                                   .setNamespace("ingress-nginx")
                                                                   .setUid("228947ad-3cba-11ea-886a-4201ac10650a")
                                                                   .build())
                                               .setOldResourceVersion("200777582")
                                               .setOldResourceYaml("apiVersion: apps/v1\n"
                                                   + "kind: ReplicaSet\n"
                                                   + "metadata:\n"
                                                   + "  name: nginx-ingress-controller-589f8749b5\n"
                                                   + "  namespace: ingress-nginx\n"
                                                   + "  uid: 228947ad-3cba-11ea-886a-4201ac10650a\n")
                                               .setDescription("ReplicaSet deleted")
                                               .build())
                                .build()),
            any(), anyMapOf(String.class, String.class));
    verifyNoMoreInteractions(eventPublisher);
  }
}
