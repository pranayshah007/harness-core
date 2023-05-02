/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.batch.processing.billing.timeseries.data.InstanceLifecycleInfo;
import io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.tasklet.util.CacheUtils;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.entities.batch.InstanceData;

import software.wings.dl.WingsPersistence;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InstanceDataServiceImpl extends CacheUtils implements InstanceDataService {
  @Autowired private InstanceDataDao instanceDataDao;
  @Autowired private WingsPersistence wingsPersistence;

  private final LoadingCache<CacheKey, PrunedInstanceData> instanceDataCache =
      Caffeine.newBuilder()
          .recordStats()
          .expireAfterAccess(24, TimeUnit.HOURS)
          .maximumSize(15_000)
          .build(key -> pruneInstanceData(key.accountId, key.clusterId, key.instanceId, key.occurredAt));

  @Value
  private static class CacheKey {
    private String accountId;
    private String clusterId;
    private String instanceId;
    @EqualsAndHashCode.Exclude private long occurredAt;
  }

  @Override
  public boolean create(InstanceData instanceData) {
    return instanceDataDao.create(instanceData);
  }

  @Override
  public InstanceData fetchActiveInstanceData(
      String accountId, String clusterId, String instanceId, List<InstanceState> instanceState) {
    return instanceDataDao.fetchActiveInstanceData(accountId, clusterId, instanceId, instanceState);
  }

  @Override
  public InstanceData fetchInstanceData(String accountId, String instanceId) {
    return instanceDataDao.fetchInstanceData(accountId, instanceId);
  }

  @Override
  public InstanceData fetchInstanceData(String instanceId) {
    return instanceDataDao.fetchInstanceData(instanceId);
  }

  @Override
  public InstanceData fetchInstanceData(String accountId, String clusterId, String instanceId) {
    return instanceDataDao.fetchInstanceData(accountId, clusterId, instanceId);
  }

  @Override
  public InstanceData fetchInstanceDataWithName(
      String accountId, String clusterId, String instanceName, Long occurredAt) {
    return instanceDataDao.fetchInstanceDataWithName(accountId, clusterId, instanceName, occurredAt);
  }

  @Override
  public List<InstanceData> fetchClusterActiveInstanceData(String accountId, String clusterId, Instant startTime) {
    List<InstanceState> instanceStates =
        new ArrayList<>(Arrays.asList(InstanceState.INITIALIZING, InstanceState.RUNNING));
    return instanceDataDao.fetchClusterActiveInstanceData(accountId, clusterId, instanceStates, startTime);
  }

  @Override
  public Set<String> fetchClusterActiveInstanceIds(String accountId, String clusterId, Instant startTime) {
    List<InstanceState> instanceStates =
        new ArrayList<>(Arrays.asList(InstanceState.INITIALIZING, InstanceState.RUNNING));
    return instanceDataDao.fetchClusterActiveInstanceIds(accountId, clusterId, instanceStates, startTime);
  }

  @Override
  public List<InstanceLifecycleInfo> fetchInstanceDataForGivenInstances(String accountId, Set<String> instanceIds) {
    List<InstanceData> instanceDataList = instanceDataDao.fetchInstanceData(accountId, instanceIds);
    return instanceDataList.stream()
        .map(instanceData
            -> InstanceLifecycleInfo.builder()
                   .instanceId(instanceData.getInstanceId())
                   .usageStartTime(instanceData.getUsageStartTime())
                   .usageStopTime(instanceData.getUsageStopTime())
                   .build())
        .collect(Collectors.toList());
  }

  public PrunedInstanceData fetchPrunedInstanceDataWithName(
      String accountId, String clusterId, String instanceId, Long occurredAt) {
    final CacheKey cacheKey = new CacheKey(accountId, clusterId, instanceId, occurredAt);
    return instanceDataCache.get(cacheKey);
  }

  private PrunedInstanceData pruneInstanceData(String accountId, String clusterId, String instanceId, Long occurredAt) {
    InstanceData instanceData = instanceDataDao.fetchInstanceDataWithName(accountId, clusterId, instanceId, occurredAt);
    if (null != instanceData) {
      return PrunedInstanceData.builder()
          .instanceId(instanceData.getInstanceId())
          .cloudProviderInstanceId(instanceData.getCloudProviderInstanceId())
          .totalResource(instanceData.getTotalResource())
          .limitResource(instanceData.getLimitResource())
          .metaData(instanceData.getMetaData())
          .build();
    } else {
      log.debug("Instance detail not found clusterId {} instanceId {}", clusterId, instanceId);
      return PrunedInstanceData.builder().build();
    }
  }
}
