/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.intfc;

import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.perpetualtask.k8s.watch.PodInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkloadRepository {
  void savePodWorkload(String accountId, PodInfo podInfo);
  List<K8sWorkload> getWorkload(String accountId, String clusterId, String namespace, Set<String> workloadName);
  List<K8sWorkload> getWorkloadByWorkloadUid(String accountId, String clusterId, Set<String> workloadUid);
  List<K8sWorkload> getWorkloadWithoutSorting(
      String accountId, String clusterId, String namespace, Set<String> workloadName);
  Optional<K8sWorkload> getWorkload(String accountId, String clusterId, String uid);
  Optional<K8sWorkload> getWorkload(ResourceId workloadId);
}
