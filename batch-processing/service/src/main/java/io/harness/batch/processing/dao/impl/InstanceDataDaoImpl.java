/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.ccm.commons.beans.InstanceType.K8S_PV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HPersistence.upsertReturnOldOptions;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeAuthorityCount;
import static io.harness.persistence.HQuery.excludeCount;

import static java.util.Collections.singletonList;

import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.batch.processing.support.ActiveInstanceIterator;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.batch.InstanceData.InstanceDataKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class InstanceDataDaoImpl implements InstanceDataDao {
  @Autowired @Inject private HPersistence hPersistence;
  @Autowired private CostEventService costEventService;

  @Override
  public boolean create(InstanceData instanceData) {
    return hPersistence.save(instanceData) != null;
  }

  @Override
  public boolean updateInstanceStopTime(InstanceData instanceData, Instant stopTime) {
    instanceData.setUsageStopTime(stopTime);
    instanceData.setActiveInstanceIterator(stopTime);
    instanceData.setInstanceState(InstanceState.STOPPED);
    instanceData.setTtl(new Date(stopTime.plus(30, ChronoUnit.DAYS).toEpochMilli()));
    return hPersistence.save(instanceData) != null;
  }

  @Override
  public InstanceData fetchInstanceData(String instanceId) {
    return hPersistence.createQuery(InstanceData.class, excludeAuthority)
        .filter(InstanceDataKeys.instanceId, instanceId)
        .get();
  }

  @Override
  public List<InstanceData> fetchInstanceData(String accountId, Set<String> instanceIds) {
    if (instanceIds.isEmpty()) {
      return Collections.emptyList();
    } else {
      return hPersistence.createQuery(InstanceData.class, excludeAuthorityCount)
          .field(InstanceDataKeys.accountId)
          .equal(accountId)
          .field(InstanceDataKeys.instanceId)
          .in(instanceIds)
          .asList();
    }
  }

  private void updateDeploymentEvent(InstanceData instanceData) {
    CostEventData costEventData = CostEventData.builder()
                                      .settingId(instanceData.getSettingId())
                                      .accountId(instanceData.getAccountId())
                                      .clusterId(instanceData.getClusterId())
                                      .clusterType(InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                                          InstanceMetaDataConstants.CLUSTER_TYPE, instanceData))
                                      .cloudProvider(InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                                          InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData))
                                      .namespace(InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                                          InstanceMetaDataConstants.NAMESPACE, instanceData))
                                      .workloadName(InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                                          InstanceMetaDataConstants.WORKLOAD_NAME, instanceData))
                                      .workloadType(InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                                          InstanceMetaDataConstants.WORKLOAD_TYPE, instanceData))
                                      .deploymentId(instanceData.getHarnessServiceInfo().getDeploymentSummaryId())
                                      .build();
    costEventService.updateDeploymentEvent(costEventData);
  }

  @Override
  public void updateInstanceActiveIterationTime(InstanceData instanceData) {
    UpdateOperations<InstanceData> instanceDataUpdateOperations =
        hPersistence.createUpdateOperations(InstanceData.class)
            .set(InstanceDataKeys.activeInstanceIterator,
                ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(instanceData.getUsageStartTime()));
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class)
                                    .filter(InstanceDataKeys.accountId, instanceData.getAccountId())
                                    .filter(InstanceDataKeys.clusterId, instanceData.getClusterId())
                                    .filter(InstanceDataKeys.instanceId, instanceData.getInstanceId());

    hPersistence.upsert(query, instanceDataUpdateOperations, upsertReturnOldOptions);
  }

  @Override
  public void correctInstanceStateActiveIterationTime(InstanceData instanceData) {
    UpdateOperations<InstanceData> instanceDataUpdateOperations =
        hPersistence.createUpdateOperations(InstanceData.class)
            .set(InstanceDataKeys.activeInstanceIterator,
                ActiveInstanceIterator.getActiveInstanceIteratorFromStopTime(instanceData.getUsageStopTime()))
            .set(InstanceDataKeys.instanceState, InstanceState.STOPPED.name());
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class)
                                    .filter(InstanceDataKeys.accountId, instanceData.getAccountId())
                                    .filter(InstanceDataKeys.clusterId, instanceData.getClusterId())
                                    .filter(InstanceDataKeys.instanceId, instanceData.getInstanceId());

    hPersistence.upsert(query, instanceDataUpdateOperations, upsertReturnOldOptions);
  }

  @Override
  public InstanceData fetchActiveInstanceData(
      String accountId, String clusterId, String instanceId, List<InstanceState> instanceState) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterId)
        .filter(InstanceDataKeys.instanceId, instanceId)
        .field(InstanceDataKeys.instanceState)
        .in(instanceState)
        .get();
  }

  @Override
  public List<InstanceData> fetchActivePVList(
      String accountId, Set<String> clusterIds, Instant startTime, Instant endTime) {
    Query<InstanceData> query;
    if (isEmpty(clusterIds)) {
      query = getActiveInstanceQuery(accountId, startTime, endTime, singletonList(K8S_PV));
      return query.asList();
    } else {
      List<InstanceData> instanceDataList = new ArrayList<>();
      for (String clusterId : clusterIds) {
        instanceDataList.addAll(getInstanceDataListsOfTypesAndClusterIdWithoutBatchSize(
            accountId, startTime, endTime, singletonList(K8S_PV), clusterId));
      }
      return instanceDataList;
    }
  }

  @Override
  public InstanceData fetchInstanceData(String accountId, String instanceId) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.instanceId, instanceId)
        .get();
  }

  @Override
  public InstanceData fetchInstanceData(String accountId, String clusterId, String instanceId) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterId)
        .filter(InstanceDataKeys.instanceId, instanceId)
        .get();
  }

  @Override
  public InstanceData fetchInstanceDataWithName(
      String accountId, String clusterId, String instanceName, Long occurredAt) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterId)
        .filter(InstanceDataKeys.instanceName, instanceName)
        .order(Sort.descending(InstanceDataKeys.usageStartTime))
        .get();
  }

  /**
   * fetching only those instances which were started before given time and are still active
   */
  @Override
  public List<InstanceData> fetchClusterActiveInstanceData(
      String accountId, String clusterName, List<InstanceState> instanceState, Instant startTime) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterName)
        .field(InstanceDataKeys.instanceState)
        .in(instanceState)
        .field(InstanceDataKeys.usageStartTime)
        .lessThanOrEq(startTime)
        .useReadPreference(ReadPreference.secondaryPreferred())
        .asList();
  }

  @Override
  public Set<String> fetchClusterActiveInstanceIds(
      String accountId, String clusterName, List<InstanceState> instanceState, Instant startTime) {
    Set<String> instanceIds = new HashSet<>();
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class)
                                    .filter(InstanceDataKeys.accountId, accountId)
                                    .filter(InstanceDataKeys.clusterId, clusterName)
                                    .field(InstanceDataKeys.instanceState)
                                    .in(instanceState)
                                    .field(InstanceDataKeys.usageStartTime)
                                    .lessThanOrEq(startTime)
                                    .project(InstanceDataKeys.instanceId, true)
                                    .project(InstanceDataKeys.usageStopTime, true)
                                    .project(InstanceDataKeys.uuid, false);
    try (HIterator<InstanceData> instanceItr = new HIterator<>(query.fetch())) {
      for (InstanceData instanceData : instanceItr) {
        if (null == instanceData.getUsageStopTime()) {
          instanceIds.add(instanceData.getInstanceId());
        }
      }
    }
    return instanceIds;
  }

  @Override
  public List<InstanceData> fetchClusterActiveInstanceData(
      String accountId, String clusterName, List<InstanceType> instanceTypes, InstanceState instanceState) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterName)
        .field(InstanceDataKeys.instanceType)
        .in(instanceTypes)
        .filter(InstanceDataKeys.instanceState, instanceState)
        .useReadPreference(ReadPreference.secondaryPreferred())
        .asList();
  }

  @Override
  public InstanceData getK8sPodInstance(String accountId, String clusterId, String namespace, String podName) {
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class)
                                    .field(InstanceDataKeys.accountId)
                                    .equal(accountId)
                                    .field(InstanceDataKeys.clusterId)
                                    .equal(clusterId)
                                    .field(InstanceDataKeys.instanceName)
                                    .equal(podName)
                                    .field(InstanceDataKeys.metaData + "." + InstanceMetaDataConstants.NAMESPACE)
                                    .equal(namespace);
    return query.get();
  }

  @Override
  public List<InstanceData> fetchInstanceDataForGivenInstances(
      String accountId, String clusterId, List<String> instanceIds) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterId)
        .field(InstanceDataKeys.instanceId)
        .in(instanceIds)
        .asList();
  }

  @Override
  public List<InstanceData> getInstanceDataListsOfTypes(
      String accountId, int batchSize, Instant startTime, Instant endTime, List<InstanceType> instanceTypes) {
    Query<InstanceData> query = getActiveInstanceQuery(accountId, startTime, endTime, instanceTypes);
    return query.asList(new FindOptions().limit(batchSize));
  }

  private Query<InstanceData> getActiveInstanceQuery(
      String accountId, Instant startTime, Instant endTime, List<InstanceType> instanceTypes) {
    return hPersistence.createQuery(InstanceData.class, excludeCount)
        .filter(InstanceDataKeys.accountId, accountId)
        .field(InstanceDataKeys.activeInstanceIterator)
        .greaterThanOrEq(startTime)
        .field(InstanceDataKeys.usageStartTime)
        .lessThanOrEq(endTime)
        .field(InstanceDataKeys.instanceType)
        .in(instanceTypes)
        .order(InstanceDataKeys.accountId + "," + InstanceDataKeys.activeInstanceIterator)
        .useReadPreference(ReadPreference.secondaryPreferred());
  }

  public List<InstanceData> getInstanceDataListsOfTypesAndClusterId(String accountId, int batchSize, Instant startTime,
      Instant endTime, List<InstanceType> instanceTypes, String clusterId) {
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class, excludeCount)
                                    .filter(InstanceDataKeys.accountId, accountId)
                                    .filter(InstanceDataKeys.clusterId, clusterId)
                                    .field(InstanceDataKeys.activeInstanceIterator)
                                    .greaterThanOrEq(startTime)
                                    .field(InstanceDataKeys.usageStartTime)
                                    .lessThanOrEq(endTime)
                                    .field(InstanceDataKeys.instanceType)
                                    .in(instanceTypes)
                                    .order(InstanceDataKeys.accountId + "," + InstanceDataKeys.clusterId + ","
                                        + InstanceDataKeys.activeInstanceIterator)
                                    .useReadPreference(ReadPreference.secondaryPreferred());
    return query.asList(new FindOptions().limit(batchSize));
  }

  public List<InstanceData> getInstanceDataListsOfTypesAndClusterIdWithoutBatchSize(
      String accountId, Instant startTime, Instant endTime, List<InstanceType> instanceTypes, String clusterId) {
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class, excludeCount)
                                    .filter(InstanceDataKeys.accountId, accountId)
                                    .filter(InstanceDataKeys.clusterId, clusterId)
                                    .field(InstanceDataKeys.activeInstanceIterator)
                                    .greaterThanOrEq(startTime)
                                    .field(InstanceDataKeys.usageStartTime)
                                    .lessThanOrEq(endTime)
                                    .field(InstanceDataKeys.instanceType)
                                    .in(instanceTypes)
                                    .order(InstanceDataKeys.accountId + "," + InstanceDataKeys.clusterId + ","
                                        + InstanceDataKeys.activeInstanceIterator)
                                    .useReadPreference(ReadPreference.secondaryPreferred());
    return query.asList();
  }
}
