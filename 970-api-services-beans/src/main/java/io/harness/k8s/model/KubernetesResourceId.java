/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.model.Kind.SCALABLE_WORKLOAD_KINDS;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.exception.WingsException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubernetesResourceId {
  private String kind;
  private String name;
  private String namespace;
  private boolean versioned;

  public static List<KubernetesResourceId> findScalableKubernetesResourceId(List<String> kindNameRefs) {
    if (isNotEmpty(kindNameRefs)) {
      return kindNameRefs.stream()
          .map(KubernetesResourceId::createKubernetesResourceIdFromNamespaceKindName)
          .filter(k8sResourceId -> SCALABLE_WORKLOAD_KINDS.contains(Kind.fromString(k8sResourceId.getKind())))
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public static KubernetesResourceId createKubernetesResourceIdFromNamespaceKindName(String kindName) {
    String splitArray[] = kindName.trim().split("/");
    if (splitArray.length == 3) {
      return KubernetesResourceId.builder().namespace(splitArray[0]).kind(splitArray[1]).name(splitArray[2]).build();
    } else if (splitArray.length == 2) {
      return KubernetesResourceId.builder().kind(splitArray[0]).name(splitArray[1]).build();
    } else {
      throw new WingsException("Invalid Kubernetes resource name " + kindName + ". Should be in format Kind/Name");
    }
  }

  public static List<KubernetesResourceId> createKubernetesResourceIdsFromKindName(String resources) {
    String resourceArray[] = resources.trim().split(",");

    List<KubernetesResourceId> result = new ArrayList<>();

    for (String resource : resourceArray) {
      result.add(createKubernetesResourceIdFromNamespaceKindName(resource));
    }

    return result;
  }

  public String kindNameRef() {
    return this.getKind() + "/" + this.getName();
  }

  public String namespaceKindNameRef() {
    if (!isBlank(this.getNamespace())) {
      return this.getNamespace() + "/" + this.kindNameRef();
    }
    return this.kindNameRef();
  }

  public KubernetesResourceId cloneInternal() {
    return KubernetesResourceId.builder().kind(this.kind).name(this.name).namespace(this.namespace).build();
  }
}
