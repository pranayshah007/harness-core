/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.ccm.commons.entities.k8s.K8sWorkload.encodeDotsInKey;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.entities.k8s.K8sWorkload.K8sWorkloadKeys;
import io.harness.perpetualtask.k8s.watch.Owner;
import io.harness.perpetualtask.k8s.watch.PodInfo;
import io.harness.persistence.HPersistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkloadRepositoryImpl implements WorkloadRepository {
  private final HPersistence hPersistence;
  private final Cache<CacheKey, Boolean> saved = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(20)).build();

  @Value
  private static class CacheKey {
    String clusterId;
    String uid;
  }

  @Autowired
  public WorkloadRepositoryImpl(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  @Override
  public void savePodWorkload(String accountId, PodInfo podInfo) {
    Owner topLevelOwner = podInfo.getTopLevelOwner();
    Map<String, String> labelMap = getLabelMap(podInfo);
    if (isNotEmpty(labelMap)) {
      final CacheKey cacheKey = new CacheKey(podInfo.getClusterId(), topLevelOwner.getUid());
      saved.get(cacheKey,
          key
          -> (hPersistence.upsert(hPersistence.createQuery(K8sWorkload.class)
                                      .field(K8sWorkloadKeys.accountId)
                                      .equal(accountId)
                                      .field(K8sWorkloadKeys.clusterId)
                                      .equal(key.clusterId)
                                      .field(K8sWorkloadKeys.uid)
                                      .equal(key.uid),
                 hPersistence.createUpdateOperations(K8sWorkload.class)
                     .set(K8sWorkloadKeys.accountId, accountId)
                     .set(K8sWorkloadKeys.clusterId, podInfo.getClusterId())
                     .set(K8sWorkloadKeys.settingId, podInfo.getCloudProviderId())
                     .set(K8sWorkloadKeys.name, topLevelOwner.getName())
                     .set(K8sWorkloadKeys.namespace, podInfo.getNamespace())
                     .set(K8sWorkloadKeys.uid, topLevelOwner.getUid())
                     .set(K8sWorkloadKeys.kind, topLevelOwner.getKind())
                     .set(K8sWorkloadKeys.ttl, new Date(Instant.now().plus(90, ChronoUnit.DAYS).toEpochMilli()))
                     .set(K8sWorkloadKeys.labels, encodeDotsInKey(labelMap)),
                 HPersistence.upsertReturnNewOptions))
              != null);
    }
  }

  /**
   * the precedence of labels is workloadLabels > namespaceLabels > podLabels
   */
  private Map<String, String> getLabelMap(PodInfo podInfo) {
    final Owner topLevelOwner = podInfo.getTopLevelOwner();

    Map<String, String> labelsMap = new HashMap<>();
    labelsMap.putAll(podInfo.getLabelsMap());
    labelsMap.putAll(podInfo.getNamespaceLabelsMap());
    labelsMap.putAll(topLevelOwner.getLabelsMap());

    return labelsMap;
  }

  @Override
  public List<K8sWorkload> getWorkload(String accountId, String clusterId, String namespace, Set<String> workloadName) {
    return hPersistence.createQuery(K8sWorkload.class, excludeAuthorityCount)
        .filter(K8sWorkloadKeys.accountId, accountId)
        .filter(K8sWorkloadKeys.clusterId, clusterId)
        .filter(K8sWorkloadKeys.namespace, namespace)
        .field(K8sWorkloadKeys.name)
        .in(workloadName)
        .order(Sort.descending(K8sWorkloadKeys.lastUpdatedAt))
        .asList();
  }

  @Override
  public List<K8sWorkload> getWorkloadByWorkloadUid(String accountId, String clusterId, Set<String> workloadUid) {
    return hPersistence.createQuery(K8sWorkload.class, excludeAuthorityCount)
        .filter(K8sWorkloadKeys.accountId, accountId)
        .filter(K8sWorkloadKeys.clusterId, clusterId)
        .field(K8sWorkloadKeys.uid)
        .in(workloadUid)
        .asList();
  }

  @Override
  public List<K8sWorkload> getWorkloadWithoutSorting(
      String accountId, String clusterId, String namespace, Set<String> workloadName) {
    return hPersistence.createQuery(K8sWorkload.class, excludeAuthorityCount)
        .filter(K8sWorkloadKeys.accountId, accountId)
        .filter(K8sWorkloadKeys.clusterId, clusterId)
        .filter(K8sWorkloadKeys.namespace, namespace)
        .field(K8sWorkloadKeys.name)
        .in(workloadName)
        .asList();
  }

  @Override
  public Optional<K8sWorkload> getWorkload(String accountId, String clusterId, String uid) {
    return Optional.ofNullable(hPersistence.createQuery(K8sWorkload.class)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.clusterId)
                                   .equal(clusterId)
                                   .field(K8sWorkloadKeys.uid)
                                   .equal(uid)
                                   .get());
  }

  /**
   * Fetch workload matching given parameters. If there are multiple matches (different uid), fetch the latest.
   * @param workloadId details to id the workload uniquely.
   * @return the workload.
   */
  @Override
  public Optional<K8sWorkload> getWorkload(ResourceId workloadId) {
    return Optional.ofNullable(hPersistence.createQuery(K8sWorkload.class)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(workloadId.getAccountId())
                                   .field(K8sWorkloadKeys.clusterId)
                                   .equal(workloadId.getClusterId())
                                   .field(K8sWorkloadKeys.namespace)
                                   .equal(workloadId.getNamespace())
                                   .field(K8sWorkloadKeys.name)
                                   .equal(workloadId.getName())
                                   .field(K8sWorkloadKeys.kind)
                                   .equal(workloadId.getKind())
                                   .order(Sort.descending(K8sWorkloadKeys.lastUpdatedAt))
                                   .get());
  }
}
