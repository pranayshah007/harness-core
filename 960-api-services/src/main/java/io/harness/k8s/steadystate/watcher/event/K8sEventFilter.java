/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.KubernetesResourceId;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
@OwnedBy(CDP)
public class K8sEventFilter implements Predicate<CoreV1Event> {
  private static final String POD_KIND = "Pod";
  private static final String REPLICASET_KIND = "ReplicaSet";

  Set<String> workloadsNames = new HashSet<>();
  Set<KubernetesResourceReference> includedResourceRefs = new HashSet<>();
  Set<KubernetesResourceReference> excludedResourceRefs = new HashSet<>();
  CoreV1Api coreV1Api;
  AppsV1Api appsV1Api;
  String namespace;
  String releaseName;

  public K8sEventFilter(List<KubernetesResourceId> resourceIds, CoreV1Api coreV1Api, AppsV1Api appsV1Api,
      String namespace, String releaseName) {
    this.coreV1Api = coreV1Api;
    this.appsV1Api = appsV1Api;
    this.namespace = namespace;
    this.releaseName = releaseName;
    resourceIds.stream().map(resourceId -> reference(resourceId.getKind(), resourceId.getName())).forEach(ref -> {
      includedResourceRefs.add(ref);
      workloadsNames.add(ref.getName());
    });
  }

  @Override
  public boolean test(CoreV1Event event) {
    V1ObjectReference eventRef = event.getInvolvedObject();

    if (eventRef == null || isEmpty(eventRef.getName())) {
      return true;
    }

    if (eventRef.getKind() == null) {
      return workloadsNames.contains(eventRef.getName());
    }

    KubernetesResourceReference ref = reference(eventRef.getKind(), eventRef.getName());
    if (includedResourceRefs.contains(ref)) {
      return true;
    }

    if (excludedResourceRefs.contains(ref)) {
      return false;
    }

    if (ref.getKind().equalsIgnoreCase(REPLICASET_KIND)) {
      processReplicaSetRef(ref);
      return includedResourceRefs.contains(ref);
    }

    if (ref.getKind().equalsIgnoreCase(POD_KIND)) {
      processPodEvent(ref);
      return includedResourceRefs.contains(ref);
    }

    return false;
  }

  private void processReplicaSetRef(KubernetesResourceReference ref) {
    String replicaSetName = ref.getName();
    if (workloadsNames.stream().noneMatch(replicaSetName::startsWith)) {
      return;
    }
    V1ReplicaSet replicaSet = readNamespacedReplicaSet(replicaSetName);
    KubernetesResourceReference replicaSetRef = reference(REPLICASET_KIND, replicaSetName);
    if (isAnyWorkloadOwnerOfReplicaSet(replicaSet)) {
      includedResourceRefs.add(replicaSetRef);
      return;
    }

    excludedResourceRefs.add(replicaSetRef);
  }

  private boolean isAnyWorkloadOwnerOfReplicaSet(V1ReplicaSet replicaSet) {
    if (replicaSet == null) {
      return false;
    }
    V1ObjectMeta replicaSetMetadata = replicaSet.getMetadata();
    if (replicaSetMetadata == null) {
      return false;
    }
    List<V1OwnerReference> ownerReferences = replicaSetMetadata.getOwnerReferences();
    if (isEmpty(ownerReferences)) {
      return false;
    }

    return ownerReferences.stream()
        .filter(Objects::nonNull)
        .map(ownerRef -> reference(ownerRef.getKind(), ownerRef.getName()))
        .anyMatch(includedResourceRefs::contains);
  }

  private V1ReplicaSet readNamespacedReplicaSet(String replicaSetName) {
    try {
      return appsV1Api.readNamespacedReplicaSet(replicaSetName, namespace, null);
    } catch (ApiException e) {
      log.warn("Failed to read namespaced ReplicaSet {}/{}", namespace, replicaSetName);
      return null;
    }
  }

  private void processPodEvent(KubernetesResourceReference ref) {
    String podName = ref.getName();

    if (isEmpty(releaseName)) {
      // For backward compatibility, if a request can't pass the release name we will automatically fallback to
      // previous logic with contains
      if (workloadsNames.stream().anyMatch(podName::contains)) {
        includedResourceRefs.add(ref);
      } else {
        excludedResourceRefs.add(ref);
      }

      return;
    }

    if (workloadsNames.stream().anyMatch(podName::contains)) {
      V1Pod v1Pod = tryReadNamespacedPod(podName, namespace, coreV1Api);
      if (isPodOfCurrentDeployment(v1Pod)) {
        includedResourceRefs.add(ref);
      }

      return;
    }
    excludedResourceRefs.add(ref);
  }

  private boolean isPodOfCurrentDeployment(V1Pod v1Pod) {
    return v1Pod != null && v1Pod.getMetadata() != null && isNotEmpty(v1Pod.getMetadata().getLabels())
        && v1Pod.getMetadata().getLabels().containsKey(HarnessLabels.releaseName)
        && releaseName.equals(v1Pod.getMetadata().getLabels().get(HarnessLabels.releaseName));
  }

  @Nullable
  private static V1Pod tryReadNamespacedPod(String name, String namespace, CoreV1Api apiClient) {
    try {
      return apiClient.readNamespacedPod(name, namespace, null);
    } catch (ApiException e) {
      log.warn("Unable to retrieve the pod {}/{}", namespace, namespace, e);
      return null;
    }
  }

  private static KubernetesResourceReference reference(String kind, String name) {
    return KubernetesResourceReference.builder()
        .kind(kind == null ? null : kind.toLowerCase(Locale.ROOT))
        .name(name)
        .build();
  }

  @Value
  @Builder
  @EqualsAndHashCode
  private static class KubernetesResourceReference {
    String kind;
    String name;
  }
}
