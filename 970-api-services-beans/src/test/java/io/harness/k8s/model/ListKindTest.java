/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ListKindTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testResourcesForKindList() {
    List<String> k8sValidResourcesListKind = List.of("Deployment", "Service", "ConfigMap", "Namespace", "RoleBinding",
        "ClusterRoleBinding", "Secret", "ResourceQuota", "LimitRange", "StorageClass", "PersistentVolume",
        "PersistentVolumeClaim", "ServiceAccount", "CustomResourceDefinition", "Role", "DaemonSet", "Pod",
        "ReplicationController", "ReplicaSet", "StatefulSet", "Job", "CronJob", "Ingress", "APIService",
        "HorizontalPodAutoscaler", "PodDisruptionBudget");
    List<KubernetesResource> validResourcesForList = createKubernetesResourceList(k8sValidResourcesListKind);
    for (KubernetesResource kubernetesResource : validResourcesForList) {
      assertThat(isPartOfListKind(kubernetesResource)).isTrue();
    }
    List<String> k8sInvalidResourcesListKind =
        List.of("NOOP", "DestinationRule", "VirtualService", "DeploymentConfig", "PodSecurityPolicy");
    List<KubernetesResource> invalidResourcesForList = createKubernetesResourceList(k8sInvalidResourcesListKind);

    for (KubernetesResource kubernetesResource : invalidResourcesForList) {
      assertThat(isPartOfListKind(kubernetesResource)).isFalse();
    }
  }

  private List<KubernetesResource> createKubernetesResourceList(List<String> k8sValidResourcesListKinds) {
    List<KubernetesResource> resourcesForList = new ArrayList<>();
    for (String k8sValidResources : k8sValidResourcesListKinds) {
      resourcesForList.add(
          KubernetesResource.builder()
              .resourceId(KubernetesResourceId.builder().kind(k8sValidResources).name(k8sValidResources).build())
              .build());
    }
    return resourcesForList;
  }

  private boolean isPartOfListKind(KubernetesResource kubernetesResource) {
    for (ListKind listKind : ListKind.values()) {
      if (listKind.name().contains(kubernetesResource.getResourceId().getKind())) {
        return true;
      }
    }
    return false;
  }
}
