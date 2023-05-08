/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.K8sBatchQueryFnFactory;
import io.harness.batch.processing.support.ActiveInstanceIterator;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.batch.InstanceData.InstanceDataKeys;
import io.harness.event.payloads.Lifecycle;
import io.harness.grpc.utils.HTimestamps;

import software.wings.dl.WingsPersistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InstanceDataBulkWriteServiceImpl implements InstanceDataBulkWriteService {
  @Autowired private WingsPersistence wingsPersistence;
  @Autowired private BatchMainConfig config;

  private static final Gson GSON = new Gson();
  private static Document objectToDocument(Object obj) {
    return Document.parse(GSON.toJson(obj));
  }

  private final K8sBatchQueryFnFactory<InstanceInfo> instanceInfoUpsertQueryFn = (bulkWriteOperation, instanceInfo) -> {
    long createdAt = Instant.now().toEpochMilli();
    BasicDBObject instanceInfoDocument =
        new BasicDBObject()
            .append(InstanceDataKeys.accountId, instanceInfo.getAccountId())
            .append(InstanceDataKeys.settingId, instanceInfo.getSettingId())
            .append(InstanceDataKeys.instanceId, instanceInfo.getInstanceId())
            .append(InstanceDataKeys.instanceName, instanceInfo.getInstanceName())
            .append(InstanceDataKeys.instanceType, instanceInfo.getInstanceType().name())
            .append(InstanceDataKeys.clusterId, instanceInfo.getClusterId())
            .append(InstanceDataKeys.clusterName, instanceInfo.getClusterName())
            .append(InstanceDataKeys.instanceState, instanceInfo.getInstanceState().name())
            .append(InstanceDataKeys.usageStartTime, instanceInfo.getUsageStartTime())
            .append(InstanceDataKeys.activeInstanceIterator,
                ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(instanceInfo.getUsageStartTime()))
            .append(InstanceDataKeys.createdAt, createdAt)
            .append(InstanceDataKeys.lastUpdatedAt, createdAt);

    if (!isNull(instanceInfo.getResource())) {
      instanceInfoDocument.append(InstanceDataKeys.totalResource, objectToDocument(instanceInfo.getResource()));
    }

    if (!isNull(instanceInfo.getCloudProviderInstanceId())) {
      instanceInfoDocument.append(InstanceDataKeys.cloudProviderInstanceId, instanceInfo.getCloudProviderInstanceId());
    }

    if (!isNull(instanceInfo.getResourceLimit())) {
      instanceInfoDocument.append(InstanceDataKeys.limitResource, objectToDocument(instanceInfo.getResourceLimit()));
    }

    if (!isNull(instanceInfo.getAllocatableResource())) {
      instanceInfoDocument.append(
          InstanceDataKeys.allocatableResource, objectToDocument(instanceInfo.getAllocatableResource()));
    }

    if (!isNull(instanceInfo.getPricingResource())) {
      instanceInfoDocument.append(
          InstanceDataKeys.pricingResource, objectToDocument(instanceInfo.getPricingResource()));
    }

    if (!isNull(instanceInfo.getStorageResource())) {
      instanceInfoDocument.append(
          InstanceDataKeys.storageResource, objectToDocument(instanceInfo.getStorageResource()));
    }

    if (!isNull(instanceInfo.getLabels())) {
      instanceInfoDocument.append(InstanceDataKeys.labels, instanceInfo.getLabels());
    }

    if (!isNull(instanceInfo.getNamespaceLabels()) && !instanceInfo.getNamespaceLabels().isEmpty()) {
      instanceInfoDocument.append(InstanceDataKeys.namespaceLabels, instanceInfo.getNamespaceLabels());
    }

    if (!isNull(instanceInfo.getTopOwnerLabels()) && !instanceInfo.getTopOwnerLabels().isEmpty()) {
      instanceInfoDocument.append(InstanceDataKeys.topOwnerLabels, instanceInfo.getTopOwnerLabels());
    }

    if (!isNull(instanceInfo.getMetadataAnnotations()) && !instanceInfo.getMetadataAnnotations().isEmpty()) {
      instanceInfoDocument.append(InstanceDataKeys.metadataAnnotations, instanceInfo.getMetadataAnnotations());
    }

    if (!isNull(instanceInfo.getMetaData())) {
      instanceInfoDocument.append(InstanceDataKeys.metaData, instanceInfo.getMetaData());
    }

    if (!isNull(instanceInfo.getHarnessServiceInfo())) {
      instanceInfoDocument.append(
          InstanceDataKeys.harnessServiceInfo, objectToDocument(instanceInfo.getHarnessServiceInfo()));
    }

    if (!isNull(instanceInfo.getHarnessServiceInfoNg())) {
      instanceInfoDocument.append(
          InstanceDataKeys.harnessServiceInfoNG, objectToDocument(instanceInfo.getHarnessServiceInfoNg()));
    }

    if (!isEmpty(instanceInfo.getPvcClaimNames())) {
      instanceInfoDocument.append(InstanceDataKeys.pvcClaimNames, instanceInfo.getPvcClaimNames());
    }

    final BasicDBObject filter = new BasicDBObject(
        ImmutableMap.of(InstanceDataKeys.accountId, instanceInfo.getAccountId(), InstanceDataKeys.clusterId,
            instanceInfo.getClusterId(), InstanceDataKeys.instanceId, instanceInfo.getInstanceId()));

    bulkWriteOperation.find(filter).upsert().update(new BasicDBObject("$set", instanceInfoDocument));
  };

  private final K8sBatchQueryFnFactory<InstanceEvent> instanceEventUpdateQueryFn =
      (bulkWriteOperation, instanceEvent) -> {
    BasicDBObject updateOperations;
    BasicDBObject filter = new BasicDBObject(
        ImmutableMap.of(InstanceDataKeys.accountId, instanceEvent.getAccountId(), InstanceDataKeys.clusterId,
            instanceEvent.getClusterId(), InstanceDataKeys.instanceId, instanceEvent.getInstanceId()));

    Instant instant = instanceEvent.getTimestamp();

    Preconditions.checkNotNull(instanceEvent.getType(), "InstanceEvent Type is null");
    switch (instanceEvent.getType()) {
      case STOP:
        filter.append(InstanceDataKeys.instanceState,
            new BasicDBObject(
                "$in", ImmutableList.of(InstanceState.RUNNING.name(), InstanceState.INITIALIZING.name())));

        updateOperations = new BasicDBObject(
            ImmutableMap.of(InstanceDataKeys.usageStopTime, instant, InstanceDataKeys.activeInstanceIterator,
                ActiveInstanceIterator.getActiveInstanceIteratorFromStopTime(instant), InstanceDataKeys.instanceState,
                InstanceState.STOPPED.name()));

        if (ImmutableSet.of(InstanceType.K8S_POD, InstanceType.K8S_POD_FARGATE)
                .contains(instanceEvent.getInstanceType())) {
          updateOperations.append(InstanceDataKeys.ttl, new Date(instant.plus(30, ChronoUnit.DAYS).toEpochMilli()));
        } else if (ImmutableList.of(InstanceType.K8S_NODE, InstanceType.K8S_PV)
                       .contains(instanceEvent.getInstanceType())) {
          updateOperations.append(InstanceDataKeys.ttl, new Date(instant.plus(180, ChronoUnit.DAYS).toEpochMilli()));
        }

        updateOperations.append(InstanceDataKeys.lastUpdatedAt, Instant.now().toEpochMilli());
        bulkWriteOperation.find(filter).update(new BasicDBObject("$set", updateOperations));
        break;

      // Deprecated, we have combined (Info & Start) to (Info)
      case START:
        filter.append(InstanceDataKeys.instanceState, InstanceState.INITIALIZING.name());

        updateOperations = new BasicDBObject(
            ImmutableMap.of(InstanceDataKeys.usageStartTime, instant, InstanceDataKeys.activeInstanceIterator,
                ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(instant), InstanceDataKeys.instanceState,
                InstanceState.RUNNING.name()));

        updateOperations.append(InstanceDataKeys.lastUpdatedAt, Instant.now().toEpochMilli());
        bulkWriteOperation.find(filter).update(new BasicDBObject("$set", updateOperations));
        break;
      default:
        break;
    }
  };

  private final K8sBatchQueryFnFactory<Lifecycle> lifecycleUpdateQueryFn = (bulkWriteOperation, lifecycle) -> {
    Instant instanceTime = HTimestamps.toInstant(lifecycle.getTimestamp());

    if (lifecycle.getType() == Lifecycle.EventType.EVENT_TYPE_STOP) {
      BasicDBObject filter = new BasicDBObject(ImmutableMap.of(InstanceDataKeys.instanceId, lifecycle.getInstanceId(),
          InstanceDataKeys.instanceState, InstanceState.RUNNING.name(), InstanceDataKeys.usageStartTime,
          new BasicDBObject("$lte", instanceTime.minus(5, ChronoUnit.MINUTES))));

      BasicDBObject updateOperations = new BasicDBObject(
          ImmutableMap.of(InstanceDataKeys.usageStopTime, instanceTime, InstanceDataKeys.activeInstanceIterator,
              ActiveInstanceIterator.getActiveInstanceIteratorFromStopTime(instanceTime),
              InstanceDataKeys.instanceState, InstanceState.STOPPED.name()));
      updateOperations.append(InstanceDataKeys.ttl, new Date(instanceTime.plus(180, ChronoUnit.DAYS).toEpochMilli()));

      updateOperations.append(InstanceDataKeys.lastUpdatedAt, Instant.now().toEpochMilli());
      bulkWriteOperation.find(filter).update(new BasicDBObject("$set", updateOperations));
    } else if (lifecycle.getType() == Lifecycle.EventType.EVENT_TYPE_START) {
      BasicDBObject filter = new BasicDBObject(InstanceDataKeys.clusterId, lifecycle.getClusterId())
                                 .append(InstanceDataKeys.instanceId, lifecycle.getInstanceId())
                                 .append(InstanceDataKeys.usageStopTime, new BasicDBObject("$lte", instanceTime));

      BasicDBObject updateOperations =
          new BasicDBObject(InstanceDataKeys.instanceState, InstanceState.RUNNING.name())
              .append(InstanceDataKeys.lastUpdatedAt, Instant.now().toEpochMilli())
              .append(InstanceDataKeys.activeInstanceIterator,
                  ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(instanceTime));

      bulkWriteOperation.find(filter).update(
          new BasicDBObject("$set", updateOperations)
              .append("$unset", ImmutableMap.of(InstanceDataKeys.usageStopTime, "", InstanceDataKeys.ttl, "")));
    }
  };

  @Override
  public boolean updateLifecycle(List<Lifecycle> lifecycleList) {
    return batchQueryExecutor(lifecycleList, lifecycleUpdateQueryFn, Lifecycle.class);
  }

  @Override
  public boolean upsertInstanceInfo(List<InstanceInfo> instanceInfoList) {
    return batchQueryExecutor(instanceInfoList, instanceInfoUpsertQueryFn, InstanceInfo.class);
  }

  // The update events is Unordered to utilize mongo parallel threads
  @Override
  public boolean updateInstanceEvent(List<InstanceEvent> instanceEventList) {
    return batchQueryExecutor(instanceEventList, instanceEventUpdateQueryFn, InstanceEvent.class);
  }

  private <T> boolean batchQueryExecutor(List<T> itemsList, K8sBatchQueryFnFactory<T> k8sBatchQueryFn, Class<T> clazz) {
    final int bulkWriteLimit = config.getBulkOperationBatchQueryConfig().getQueryBatchSize();

    for (List<T> itemsListPartitioned : Lists.partition(itemsList, bulkWriteLimit)) {
      final BulkWriteOperation bulkWriteOperation =
          wingsPersistence.getCollection(InstanceData.class).initializeUnorderedBulkOperation();
      for (T singleItem : itemsListPartitioned) {
        try {
          k8sBatchQueryFn.addQueryFor(bulkWriteOperation, singleItem);
        } catch (Exception ex) {
          log.error("Error updating {}:[{}]", clazz.getSimpleName(), singleItem.toString(), ex);
        }
      }
      BulkWriteResult result = bulkWriteExecutor(bulkWriteOperation);

      if (!result.isAcknowledged()) {
        return false;
      }
    }

    return true;
  }

  private static BulkWriteResult bulkWriteExecutor(BulkWriteOperation bulkWriteOperation) {
    BulkWriteResult result;
    for (int i = 1; i < 5; i++) {
      try {
        result = bulkWriteOperation.execute();
        log.info("BulkWriteExecutor result: {}", result.toString());
        return result;
      } catch (IllegalArgumentException ex) {
        throw ex;
      } catch (Exception ex) {
        log.warn("Exception occurred with bulkWriteExecutor, retry:{}", i, ex);
      }
    }
    result = bulkWriteOperation.execute();
    log.info(
        "BulkWriteExecutor result [acknowledged:{}, insertedCount:{}, matchedCount:{}, modifiedCount:{}, removedCount:{}]",
        result.isAcknowledged(), result.getInsertedCount(), result.getMatchedCount(), result.getModifiedCount(),
        result.getRemovedCount());
    return result;
  }
}
