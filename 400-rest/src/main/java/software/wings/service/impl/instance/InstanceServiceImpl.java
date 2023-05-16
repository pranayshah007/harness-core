/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.FeatureName.CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.nullCheck;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;

import software.wings.api.InstanceEvent;
import software.wings.beans.Account;
import software.wings.beans.infrastructure.instance.ContainerDeploymentInfo;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.ManualSyncJob;
import software.wings.beans.infrastructure.instance.SyncStatus;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.InstanceKey;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsMongoPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCollection;
import dev.morphia.Key;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 8/13/17
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class InstanceServiceImpl implements InstanceService {
  @Inject FeatureFlagService featureFlagService;
  @Inject private WingsMongoPersistence wingsPersistence;
  @Inject private AppService appService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private QueuePublisher<InstanceEvent> eventQueue;

  @Override
  public Instance save(Instance instance) {
    if (log.isDebugEnabled()) {
      log.debug("Begin - Instance save called for uuid:" + instance.getUuid()
          + " and infraMappingId:" + instance.getInfraMappingId());
    }
    if (!appService.exist(instance.getAppId())) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "App does not exist: " + instance.getAppId());
    }

    Instance currentInstance = get(instance.getUuid(), false);
    nullCheck("Instance", currentInstance);

    String key = wingsPersistence.save(instance);
    Instance updatedInstance = wingsPersistence.getWithAppId(Instance.class, instance.getAppId(), key);
    if (log.isDebugEnabled()) {
      log.debug("End - Instance save called for uuid:" + instance.getUuid()
          + " and infraMappingId:" + instance.getInfraMappingId());
    }

    return updatedInstance;
  }

  @Override
  public List<Instance> saveOrUpdate(List<Instance> instances) {
    List<Instance> updatedInstanceList = Lists.newArrayList();
    for (Instance instance : instances) {
      Instance updatedInstance = saveOrUpdate(instance);
      if (updatedInstance != null) {
        updatedInstanceList.add(updatedInstance);
      }
    }
    return updatedInstanceList;
  }

  @Override
  public Instance get(String instanceId, boolean includeDeleted) {
    Instance instance = wingsPersistence.get(Instance.class, instanceId);
    if (instance != null && !includeDeleted && instance.isDeleted()) {
      return null;
    }
    return instance;
  }

  @Override
  public Instance saveOrUpdate(@Valid Instance instance) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.filter("appId", instance.getAppId());
    query.filter("isDeleted", false);
    InstanceKey instanceKey = addInstanceKeyFilterToQuery(query, instance);
    query.project(InstanceKeys.uuid, true);

    try (AcquiredLock acquiredLock = persistentLocker.waitToAcquireLockOptional(
             instanceKey.toString(), Duration.ofMinutes(1), Duration.ofMinutes(2))) {
      if (acquiredLock == null) {
        String msg = "Unable to acquire lock while trying save or update instance with key " + instanceKey.toString();
        log.warn(msg);
        throw new WingsException(msg);
      }

      Instance existingInstance = query.get();
      if (existingInstance == null) {
        return save(instance);
      } else {
        instance.setUuid(UUIDGenerator.generateUuid());
        return update(instance, existingInstance.getUuid());
      }
    }
  }

  @Override
  public Instance update(Instance instance, String oldInstanceId) {
    delete(Sets.newHashSet(oldInstanceId));

    // since this is a new version, we have to make sure that the deletedAt of old version and
    // createdAt of new version are off by at least 1 milliseconds.
    // Otherwise, if the stats collection happens in that exact millisecond,
    // they will see twice the instance count for the ones that were in the version update processing.
    instance.setCreatedAt(System.currentTimeMillis() + 1);
    String uuid = wingsPersistence.save(instance);
    return wingsPersistence.get(Instance.class, uuid);
  }

  private InstanceKey addInstanceKeyFilterToQuery(Query<Instance> query, Instance instance) {
    if (instance.getHostInstanceKey() != null) {
      HostInstanceKey hostInstanceKey = instance.getHostInstanceKey();
      query.filter("hostInstanceKey.hostName", hostInstanceKey.getHostName())
          .filter("hostInstanceKey.infraMappingId", hostInstanceKey.getInfraMappingId());
      return hostInstanceKey;
    } else if (instance.getContainerInstanceKey() != null) {
      ContainerInstanceKey containerInstanceKey = instance.getContainerInstanceKey();
      query.filter("containerInstanceKey.containerId", containerInstanceKey.getContainerId());
      return containerInstanceKey;
    } else if (instance.getPcfInstanceKey() != null) {
      PcfInstanceKey pcfInstanceKey = instance.getPcfInstanceKey();
      query.filter("pcfInstanceKey.id", pcfInstanceKey.getId());
      return pcfInstanceKey;
    } else if (instance.getPodInstanceKey() != null) {
      PodInstanceKey podInstanceKey = instance.getPodInstanceKey();
      query.filter("podInstanceKey.podName", podInstanceKey.getPodName())
          .filter("podInstanceKey.namespace", podInstanceKey.getNamespace())
          .filter(InstanceKeys.infraMappingId, instance.getInfraMappingId());
      return podInstanceKey;
    } else {
      String msg = "Either host or container or pcf instance key needs to be set";
      log.error(msg);
      throw new WingsException(msg);
    }
  }

  private void pruneByEntity(String fieldName, String value) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.filter(fieldName, value);
    long currentTimeMillis = System.currentTimeMillis();
    delete(query, currentTimeMillis);

    if ("appId".equals(fieldName)) {
      Query<SyncStatus> syncStatusQuery = wingsPersistence.createQuery(SyncStatus.class);
      syncStatusQuery.filter(fieldName, value);
      wingsPersistence.delete(syncStatusQuery);
    }
  }

  private void pruneByEntity(Map<String, String> inputs) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    inputs.forEach((key, value) -> query.filter(key, value));
    long currentTimeMillis = System.currentTimeMillis();
    delete(query, currentTimeMillis);

    Query<SyncStatus> syncStatusQuery = wingsPersistence.createQuery(SyncStatus.class);
    inputs.forEach(syncStatusQuery::filter);
    wingsPersistence.delete(syncStatusQuery);
  }

  @Override
  public void pruneByApplication(String appId) {
    pruneByEntity("appId", appId);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    pruneByEntity("accountId", accountId);
    deleteManualSyncJobsByAccountId(accountId);
    deleteContainerDeploymentInfo(accountId);
  }

  private void deleteManualSyncJobsByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ManualSyncJob.class).filter(ACCOUNT_ID_KEY2, accountId));
  }

  private void deleteContainerDeploymentInfo(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(ContainerDeploymentInfo.class).filter(ACCOUNT_ID_KEY2, accountId));
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    HashMap<String, String> map = new HashMap<>();
    map.put("appId", appId);
    map.put("envId", envId);
    pruneByEntity(map);
  }

  @Override
  public void pruneByInfrastructureMapping(String appId, String infraMappingId) {
    HashMap<String, String> map = new HashMap<>();
    map.put("appId", appId);
    map.put("infraMappingId", infraMappingId);
    pruneByEntity(map);
  }

  @Override
  public void pruneByService(String appId, String serviceId) {
    HashMap<String, String> map = new HashMap<>();
    map.put("appId", appId);
    map.put("serviceId", serviceId);
    pruneByEntity(map);
  }

  // Note: This method shouldn't be called with already deleted instance ids.
  @Override
  public boolean delete(Set<String> instanceIdSet) {
    final List<List<String>> partitions = Lists.partition(new ArrayList<>(instanceIdSet), 50);
    AtomicBoolean result = new AtomicBoolean(true);
    long currentTimeMillis = System.currentTimeMillis();

    partitions.forEach(partition -> {
      Query<Instance> queryDeletedAlready = wingsPersistence.createQuery(Instance.class, excludeAuthority);
      queryDeletedAlready.field("_id").in(partition);
      queryDeletedAlready.field(InstanceKeys.isDeleted).equal(true);
      queryDeletedAlready.project(InstanceKeys.accountId, true);
      queryDeletedAlready.project("_id", true);
      List<Instance> deletedInstances = queryDeletedAlready.asList();

      if (isNotEmpty(deletedInstances)) {
        final List<String> idList = deletedInstances.stream().map(Instance::getUuid).collect(Collectors.toList());
        log.error("Found some deleted instances that are being deleted again. accountId: [{}], instanceIds:[{}]",
            deletedInstances.get(0).getAccountId(), idList);
        log.error("Instance Sync Delete Stack trace: " + Arrays.toString(Thread.currentThread().getStackTrace()));
      }

      Query<Instance> query = wingsPersistence.createQuery(Instance.class);
      query.field("_id").in(partition);
      query.field(InstanceKeys.isDeleted).equal(false);
      result.set(result.get() && delete(query, currentTimeMillis));
    });
    return result.get();
  }

  private boolean delete(Query<Instance> query, long time) {
    // todo(abhinav): find way to limit query and iterate
    query.filter(InstanceKeys.isDeleted, false);
    UpdateOperations<Instance> updateOperations = wingsPersistence.createUpdateOperations(Instance.class);
    setUnset(updateOperations, "deletedAt", time);
    setUnset(updateOperations, "isDeleted", true);
    wingsPersistence.update(query, updateOperations);
    return true;
  }

  @Override
  public boolean purgeDeletedUpTo(Instant timestamp) {
    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createQuery(Account.class).project(Account.ID_KEY2, true).fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();
        try (AutoLogContext ignore1 = new AccountLogContext(account.getUuid(), OverrideBehavior.OVERRIDE_NESTS)) {
          Query<Instance> query;
          do {
            try {
              query = wingsPersistence.createQuery(Instance.class)
                          .filter(InstanceKeys.accountId, account.getUuid())
                          .filter(InstanceKeys.isDeleted, true)
                          .field(InstanceKeys.deletedAt)
                          .lessThan(timestamp.toEpochMilli())
                          .project(InstanceKeys.uuid, true)
                          .project(InstanceKeys.deletedAt, true)
                          .order(InstanceKeys.deletedAt);
              final List<Instance> instances = query.asList(new FindOptions().limit(2000));
              if (isEmpty(instances)) {
                break;
              }
              final Instance instance = instances.get(instances.size() - 1);

              DBCollection collection = wingsPersistence.getCollection(Instance.class);
              BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
              bulkWriteOperation
                  .find(wingsPersistence.createQuery(Instance.class, excludeAuthority)
                            .filter(InstanceKeys.accountId, account.getUuid())
                            .filter(InstanceKeys.isDeleted, true)
                            .field(InstanceKeys.deletedAt)
                            .lessThanOrEq(instance.getDeletedAt())
                            .getQueryObject())
                  .remove();
              BulkWriteResult writeResult = bulkWriteOperation.execute();
              log.info("Deleted {} records for instances", writeResult.getRemovedCount());
            } catch (Exception e) {
              log.error("Failed to delete some instances for account {}", account.getUuid(), e);
              break;
            }
          } while (true);
        }
      }
    }
    return true;
  }

  @Override
  public List<ContainerDeploymentInfo> getContainerDeploymentInfoList(String containerSvcNameNoRevision, String appId) {
    PageRequest<ContainerDeploymentInfo> pageRequest =
        aPageRequest()
            .addFilter("containerSvcNameNoRevision", EQ, containerSvcNameNoRevision)
            .addFilter("appId", EQ, appId)
            .addOrder("containerSvcName", OrderType.ASC)
            .build();
    PageResponse<ContainerDeploymentInfo> response = wingsPersistence.query(ContainerDeploymentInfo.class, pageRequest);
    return response.getResponse();
  }

  @Override
  public PageResponse<Instance> list(PageRequest<Instance> pageRequest) {
    pageRequest.addFilter("isDeleted", Operator.EQ, false);
    return wingsPersistence.querySecondary(Instance.class, pageRequest);
  }

  @Override
  public List<Instance> listV2(Query<Instance> query) {
    query.filter(InstanceKeys.isDeleted, false);
    return query.asList(wingsPersistence.analyticNodePreferenceOptions());
  }

  @Override
  public List<Instance> listInstancesNotRemovedFully(Query<Instance> query) {
    long twoDaysOldTimeInMills = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
    query.and(query.or(query.criteria(InstanceKeys.deletedAt).greaterThanOrEq(twoDaysOldTimeInMills),
        query.criteria(InstanceKeys.isDeleted).equal(false)));
    return query.asList(wingsPersistence.analyticNodePreferenceOptions());
  }

  @Override
  public void updateSyncSuccess(
      String appId, String serviceId, String envId, String infraMappingId, String infraMappingName, long timestamp) {
    SyncStatus syncStatus = getSyncStatus(appId, serviceId, envId, infraMappingId);
    if (syncStatus == null) {
      syncStatus = SyncStatus.builder()
                       .appId(appId)
                       .envId(envId)
                       .serviceId(serviceId)
                       .infraMappingId(infraMappingId)
                       .infraMappingName(infraMappingName)
                       .lastSyncedAt(timestamp)
                       .lastSuccessfullySyncedAt(timestamp)
                       .syncFailureReason(null)
                       .build();
    } else {
      syncStatus.setSyncFailureReason(null);
      syncStatus.setLastSuccessfullySyncedAt(timestamp);
      syncStatus.setLastSyncedAt(timestamp);
    }

    wingsPersistence.save(syncStatus);
  }

  private SyncStatus getSyncStatus(String appId, String serviceId, String envId, String infraMappingId) {
    return wingsPersistence.createQuery(SyncStatus.class)
        .filter(SyncStatus.APP_ID_KEY2, appId)
        .filter(SyncStatus.SERVICE_ID_KEY, serviceId)
        .filter(SyncStatus.ENV_ID_KEY, envId)
        .filter(SyncStatus.INFRA_MAPPING_ID_KEY, infraMappingId)
        .get();
  }

  @Override
  public boolean handleSyncFailure(String appId, String serviceId, String envId, String infraMappingId,
      String infraMappingName, long timestamp, String errorMsg) {
    SyncStatus syncStatus = getSyncStatus(appId, serviceId, envId, infraMappingId);
    if (syncStatus != null) {
      if ((timestamp - syncStatus.getLastSuccessfullySyncedAt()) >= Duration.ofDays(7).toMillis()) {
        log.info("Deleting the instances since sync has been failing for more than a week for infraMappingId: {}",
            infraMappingId);
        wingsPersistence.delete(SyncStatus.class, syncStatus.getUuid());
        pruneByInfrastructureMapping(appId, infraMappingId);
        return false;
      }
    }

    updateSyncFailure(appId, serviceId, envId, infraMappingId, infraMappingName, timestamp, errorMsg);
    return true;
  }

  private void updateSyncFailure(String appId, String serviceId, String envId, String infraMappingId,
      String infraMappingName, long timestamp, String errorMsg) {
    SyncStatus syncStatus = getSyncStatus(appId, serviceId, envId, infraMappingId);
    if (syncStatus == null) {
      syncStatus = SyncStatus.builder()
                       .appId(appId)
                       .envId(envId)
                       .serviceId(serviceId)
                       .infraMappingId(infraMappingId)
                       .infraMappingName(infraMappingName)
                       .lastSyncedAt(timestamp)
                       .syncFailureReason(errorMsg)
                       .build();
    } else {
      syncStatus.setSyncFailureReason(errorMsg);
      syncStatus.setLastSyncedAt(timestamp);
    }
    wingsPersistence.save(syncStatus);
  }

  @Override
  public void saveManualSyncJob(ManualSyncJob manualSyncJob) {
    wingsPersistence.save(manualSyncJob);
  }

  @Override
  public void deleteManualSyncJob(String appId, String manualSyncJobId) {
    wingsPersistence.delete(ManualSyncJob.class, appId, manualSyncJobId);
  }

  @Override
  public List<SyncStatus> getSyncStatus(String appId, String serviceId, String envId) {
    log.info("get sync status params APPID :{}, ServiceId:{}, EnvId:{}", appId, serviceId, envId);
    PageRequest<SyncStatus> pageRequest = aPageRequest()
                                              .addFilter("appId", EQ, appId)
                                              .addFilter("serviceId", EQ, serviceId)
                                              .addFilter("envId", EQ, envId)
                                              .build();
    PageResponse<SyncStatus> response = wingsPersistence.query(SyncStatus.class, pageRequest);
    log.info("InfraMappingId from SyncStatus: {}", response.getResponse());
    return response.getResponse();
  }

  @Override
  public List<Boolean> getManualSyncJobsStatus(String accountId, Set<String> manualJobIdSet) {
    List<Key<ManualSyncJob>> keyList = wingsPersistence.createQuery(ManualSyncJob.class)
                                           .filter(ACCOUNT_ID_KEY2, accountId)
                                           .field("_id")
                                           .in(manualJobIdSet)
                                           .asKeyList();
    Set<Object> jobIdSetInDB = keyList.stream().map(Key::getId).collect(Collectors.toSet());
    List<Boolean> result = Lists.newArrayList();
    manualJobIdSet.forEach(jobId -> result.add(!jobIdSetInDB.contains(jobId)));
    return result;
  }

  public List<Instance> getInstancesForAppAndInframapping(String appId, String infraMappingId) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class)
                                .filter(InstanceKeys.infraMappingId, infraMappingId)
                                .filter(InstanceKeys.appId, appId);
    return listV2(query);
  }

  public List<Instance> getInstancesForAppAndInframappingNotRemovedFully(String appId, String infraMappingId) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class)
                                .filter(InstanceKeys.infraMappingId, infraMappingId)
                                .filter(InstanceKeys.appId, appId);
    return listInstancesNotRemovedFully(query);
  }

  @Override
  public long getInstanceCount(String appId, String infraMappingId) {
    return wingsPersistence.createQuery(Instance.class)
        .filter(InstanceKeys.appId, appId)
        .filter(InstanceKeys.infraMappingId, infraMappingId)
        .filter(InstanceKeys.isDeleted, Boolean.FALSE)
        .count();
  }

  @Override
  public Instance getLastDiscoveredInstance(String appId, String infrastructureDefinitionId) {
    PageRequest<Instance> pageRequest = new PageRequest<>();
    pageRequest.addFilter(InstanceKeys.infraMappingId, Operator.EQ, infrastructureDefinitionId);
    pageRequest.addFilter(InstanceKeys.appId, Operator.EQ, appId);
    pageRequest.addOrder(SortOrder.Builder.aSortOrder().withField(InstanceKeys.lastUpdatedAt, OrderType.DESC).build());
    pageRequest.setLimit("1");
    Instance instance;
    // todo: GA this FF if works good and remove else clause
    if (featureFlagService.isGlobalEnabled(CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE)) {
      pageRequest.addFilter(InstanceKeys.lastWorkflowExecutionId, Operator.NOT_EQ, new Object[] {null});
      instance = wingsPersistence.convertToQuery(Instance.class, pageRequest)
                     .get(wingsPersistence.analyticNodePreferenceOptions());
    } else {
      instance = wingsPersistence.convertToQuery(Instance.class, pageRequest)
                     .get(wingsPersistence.analyticNodePreferenceOptions());
      if (instance.getLastWorkflowExecutionId() == null) {
        log.error("Got last workflow execution Id null which is not expected. Details: appid:{}, inframapping id: {}.",
            appId, infrastructureDefinitionId);
        pageRequest.addFilter(InstanceKeys.lastWorkflowExecutionId, Operator.EXISTS);
        instance = wingsPersistence.convertToQuery(Instance.class, pageRequest)
                       .get(wingsPersistence.analyticNodePreferenceOptions());
      }
    }

    return instance;
  }
}
