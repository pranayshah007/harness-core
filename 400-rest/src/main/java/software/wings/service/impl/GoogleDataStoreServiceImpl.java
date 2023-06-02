/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.System.currentTimeMillis;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.exception.WingsException;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.reflection.HarnessReflections;

import software.wings.beans.Log;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.DataStoreService;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery.Builder;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.ProjectionEntityQuery;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Transaction;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Singleton
@Slf4j
public class GoogleDataStoreServiceImpl implements DataStoreService {
  private static int DATA_STORE_BATCH_SIZE = 500;

  private static final String GOOGLE_APPLICATION_CREDENTIALS_PATH = "GOOGLE_APPLICATION_CREDENTIALS";
  private static final String ENV_VARIABLE_WORKLOAD_IDENTITY = "USE_WORKLOAD_IDENTITY";
  private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
  private DataStoreService mongoDataStoreService;

  @Inject
  public GoogleDataStoreServiceImpl(WingsPersistence wingsPersistence) {
    String googleCredentialsPath = System.getenv(GOOGLE_APPLICATION_CREDENTIALS_PATH);
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv(ENV_VARIABLE_WORKLOAD_IDENTITY));
    if (!usingWorkloadIdentity && (isEmpty(googleCredentialsPath) || !new File(googleCredentialsPath).exists())) {
      throw new WingsException("Invalid credentials found at " + googleCredentialsPath);
    }
    mongoDataStoreService = new MongoDataStoreServiceImpl(wingsPersistence);
  }

  @Override
  public <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate) {
    if (isEmpty(records)) {
      return;
    }
    List<List<T>> batches = Lists.partition(records, DATA_STORE_BATCH_SIZE);
    batches.forEach(batch -> {
      List<Entity> logList = new ArrayList<>();
      batch.forEach(record -> logList.add(record.convertToCloudStorageEntity(datastore)));
      datastore.put(logList.stream().toArray(Entity[] ::new));
    });
  }

  @Override
  public <T extends GoogleDataStoreAware> PageResponse<T> list(Class<T> clazz, PageRequest<T> pageRequest) {
    return list(clazz, pageRequest, true);
  }

  @Override
  public <T extends GoogleDataStoreAware> PageResponse<T> list(
      Class<T> clazz, PageRequest<T> pageRequest, boolean getTotalRecords) {
    QueryResults<Entity> results = readResults(clazz, pageRequest);
    int total = getTotalRecords ? getNumberOfResults(clazz, pageRequest) : 0;
    List<T> rv = new ArrayList<>();
    while (results.hasNext()) {
      try {
        rv.add((T) clazz.newInstance().readFromCloudStorageEntity(results.next()));
      } catch (Exception e) {
        throw new WingsException(e);
      }
    }

    return aPageResponse()
        .withResponse(rv)
        .withTotal(total)
        .withLimit(pageRequest.getLimit())
        .withOffset(pageRequest.getOffset())
        .withFilters(pageRequest.getFilters())
        .withOrders(pageRequest.getOrders())
        .build();
  }

  @Override
  public <T extends GoogleDataStoreAware> T getEntity(Class<T> clazz, String id) {
    Key keyToFetch =
        datastore.newKeyFactory().setKind(clazz.getAnnotation(dev.morphia.annotations.Entity.class).value()).newKey(id);
    try {
      return (T) clazz.newInstance().readFromCloudStorageEntity(datastore.get(keyToFetch));
    } catch (Exception ex) {
      throw new WingsException(ex);
    }
  }

  @Override
  public <T extends GoogleDataStoreAware> void incrementField(
      Class<T> clazz, String id, String fieldName, int incrementCount) {
    Transaction txn = datastore.newTransaction();
    Key keyToFetch =
        datastore.newKeyFactory().setKind(clazz.getAnnotation(dev.morphia.annotations.Entity.class).value()).newKey(id);
    Entity entity = txn.get(keyToFetch);
    Entity updatedEntity = Entity.newBuilder(entity).set(fieldName, entity.getLong(fieldName) + incrementCount).build();
    txn.put(updatedEntity);
    txn.commit();
  }

  @Override
  public void delete(Class<? extends GoogleDataStoreAware> clazz, String id) {
    log.info("Deleting from GoogleDatastore table {}, id: {}",
        clazz.getAnnotation(dev.morphia.annotations.Entity.class).value(), id);
    Key keyToDelete =
        datastore.newKeyFactory().setKind(clazz.getAnnotation(dev.morphia.annotations.Entity.class).value()).newKey(id);
    datastore.delete(keyToDelete);
    log.info("Deleted from GoogleDatastore table {}, id: {}",
        clazz.getAnnotation(dev.morphia.annotations.Entity.class).value(), id);
  }

  @Override
  public void purgeByActivity(String appId, String activityId) {
    mongoDataStoreService.purgeByActivity(appId, activityId);
    Query<Key> query = Query.newKeyQueryBuilder()
                           .setKind(Log.class.getAnnotation(dev.morphia.annotations.Entity.class).value())
                           .setFilter(CompositeFilter.and(
                               PropertyFilter.eq("appId", appId), PropertyFilter.eq("activityId", activityId)))
                           .build();
    List<Key> keysToDelete = new ArrayList<>();
    datastore.run(query).forEachRemaining(keysToDelete::add);
    log.info("deleting {} keys for activity {}", keysToDelete.size(), activityId);
    datastore.delete(keysToDelete.stream().toArray(Key[] ::new));
  }

  @Override
  public void purgeOlderRecords() {
    Set<Class<? extends GoogleDataStoreAware>> dataStoreClasses =
        HarnessReflections.get().getSubTypesOf(GoogleDataStoreAware.class);

    dataStoreClasses.forEach(dataStoreClass -> {
      if (AccountDataRetentionEntity.class.isAssignableFrom(dataStoreClass)) {
        return;
      }

      long now = currentTimeMillis();
      String collectionName = dataStoreClass.getAnnotation(dev.morphia.annotations.Entity.class).value();
      log.info("cleaning up {}", collectionName);
      List<Key> keysToDelete = new ArrayList<>();

      Query<Key> query =
          Query.newKeyQueryBuilder().setKind(collectionName).setFilter(PropertyFilter.lt("validUntil", now)).build();
      datastore.run(query).forEachRemaining(keysToDelete::add);

      log.info("Total keys to delete {} for {}", keysToDelete.size(), collectionName);
      final List<List<Key>> keyBatches = Lists.partition(keysToDelete, DATA_STORE_BATCH_SIZE);
      keyBatches.forEach(keys -> {
        log.info("purging {} records from {}", keys.size(), collectionName);
        datastore.delete(keys.stream().toArray(Key[] ::new));
      });
    });
  }

  @Override
  public void purgeDataRetentionOlderRecords(Map<String, Long> accounts) {
    Set<Class<? extends GoogleDataStoreAware>> dataStoreClasses =
        HarnessReflections.get().getSubTypesOf(GoogleDataStoreAware.class);

    dataStoreClasses.forEach(dataStoreClass -> {
      long now = currentTimeMillis();
      String collectionName = dataStoreClass.getAnnotation(dev.morphia.annotations.Entity.class).value();
      log.info("cleaning up {}", collectionName);
      List<Key> keysToDelete = new ArrayList<>();

      if (AccountDataRetentionEntity.class.isAssignableFrom(dataStoreClass)) {
        ProjectionEntityQuery query =
            Query.newProjectionEntityQueryBuilder()
                .setKind(collectionName)
                .setFilter(PropertyFilter.lt(AccountDataRetentionEntity.VALID_UNTIL_KEY, now))
                .setProjection(AccountDataRetentionEntity.ACCOUNT_ID_KEY, AccountDataRetentionEntity.CREATED_AT_KEY)
                .build();

        datastore.run(query).forEachRemaining(entity -> {
          String accountId = entity.getString(AccountDataRetentionEntity.ACCOUNT_ID_KEY);
          long createdAt = entity.getLong(AccountDataRetentionEntity.CREATED_AT_KEY);

          Long retentionData = accounts.get(accountId);
          // delete only if the retention criteria is met
          if (retentionData == null || createdAt + retentionData <= now) {
            keysToDelete.add(entity.getKey());
          }
        });
      } else {
        Query<Key> query =
            Query.newKeyQueryBuilder().setKind(collectionName).setFilter(PropertyFilter.lt("validUntil", now)).build();
        datastore.run(query).forEachRemaining(keysToDelete::add);
      }

      log.info("Total keys to delete {} for {}", keysToDelete.size(), collectionName);
      final List<List<Key>> keyBatches = Lists.partition(keysToDelete, DATA_STORE_BATCH_SIZE);
      keyBatches.forEach(keys -> {
        log.info("purging {} records from {}", keys.size(), collectionName);
        datastore.delete(keys.stream().toArray(Key[] ::new));
      });
    });
  }

  @Override
  public void delete(Class<? extends GoogleDataStoreAware> clazz, String fieldName, String fieldValue) {
    String collectionName = clazz.getAnnotation(dev.morphia.annotations.Entity.class).value();
    log.info("deleting records from {} for {} = {}", collectionName, fieldName, fieldValue);
    Query<Key> query =
        Query.newKeyQueryBuilder().setKind(collectionName).setFilter(PropertyFilter.eq(fieldName, fieldValue)).build();
    List<Key> keysToDelete = new ArrayList<>();
    datastore.run(query).forEachRemaining(keysToDelete::add);
    log.info("Total keys to delete in {} are {} for condition {} = {}", collectionName, keysToDelete.size(), fieldName,
        fieldValue);
    final List<List<Key>> keyBatches = Lists.partition(keysToDelete, DATA_STORE_BATCH_SIZE);
    keyBatches.forEach(keys -> {
      log.info("purging {} records from {}", keys.size(), collectionName);
      datastore.delete(keys.stream().toArray(Key[] ::new));
    });
  }

  private <T extends GoogleDataStoreAware> QueryResults<Entity> readResults(
      Class<T> clazz, PageRequest<T> pageRequest) {
    final Builder queryBuilder = Query.newEntityQueryBuilder()
                                     .setKind(clazz.getAnnotation(dev.morphia.annotations.Entity.class).value())
                                     .setFilter(createCompositeFilter(pageRequest));
    if (isNotEmpty(pageRequest.getOrders())) {
      pageRequest.getOrders().forEach(sortOrder -> {
        switch (sortOrder.getOrderType()) {
          case DESC:
            queryBuilder.setOrderBy(OrderBy.desc(sortOrder.getFieldName()));
            break;
          case ASC:
            queryBuilder.setOrderBy(OrderBy.asc(sortOrder.getFieldName()));
            break;
          default:
            throw new IllegalStateException("Invalid order type: " + sortOrder.getOrderType());
        }
      });
    }

    if (isNotEmpty(pageRequest.getLimit()) && !pageRequest.getLimit().equals(UNLIMITED)) {
      queryBuilder.setLimit(Integer.parseInt(pageRequest.getLimit()));
    }

    if (isNotEmpty(pageRequest.getOffset())) {
      queryBuilder.setOffset(Integer.parseInt(pageRequest.getOffset()));
    }

    return datastore.run(queryBuilder.build());
  }

  @Override
  public <T extends GoogleDataStoreAware> int getNumberOfResults(Class<T> clazz, PageRequest<T> pageRequest) {
    if (isEmpty(pageRequest.getLimit()) || pageRequest.getLimit().equals(UNLIMITED)) {
      return 0;
    }

    Query<Key> query = Query.newKeyQueryBuilder()
                           .setKind(clazz.getAnnotation(dev.morphia.annotations.Entity.class).value())
                           .setFilter(createCompositeFilter(pageRequest))
                           .setLimit(10000)
                           .build();
    List<Key> keys = new ArrayList<>();
    datastore.run(query).forEachRemaining(keys::add);
    return keys.size();
  }

  @Nullable
  private <T extends GoogleDataStoreAware> CompositeFilter createCompositeFilter(PageRequest<T> pageRequest) {
    CompositeFilter compositeFilter = null;
    if (isNotEmpty(pageRequest.getFilters())) {
      List<PropertyFilter> propertyFilters = new ArrayList<>();
      pageRequest.getFilters()
          .stream()
          .filter(searchFilter -> searchFilter.getFieldValues()[0] != null)
          .forEach(searchFilter -> propertyFilters.add(createFilter(searchFilter)));
      if (propertyFilters.size() == 1) {
        compositeFilter = CompositeFilter.and(propertyFilters.get(0));
      } else {
        compositeFilter = CompositeFilter.and(propertyFilters.get(0),
            propertyFilters.subList(1, propertyFilters.size()).toArray(new PropertyFilter[propertyFilters.size() - 1]));
      }
    }
    return compositeFilter;
  }

  private PropertyFilter createFilter(SearchFilter searchFilter) {
    switch (searchFilter.getOp()) {
      case EQ:
        if (searchFilter.getFieldValues()[0] instanceof String) {
          return PropertyFilter.eq(searchFilter.getFieldName(), (String) searchFilter.getFieldValues()[0]);
        } else if (searchFilter.getFieldValues()[0].getClass() != null
            && searchFilter.getFieldValues()[0].getClass().isEnum()) {
          return PropertyFilter.eq(searchFilter.getFieldName(), ((Enum) searchFilter.getFieldValues()[0]).name());
        } else {
          return PropertyFilter.eq(searchFilter.getFieldName(), (long) searchFilter.getFieldValues()[0]);
        }

      case LT:
        return PropertyFilter.lt(
            searchFilter.getFieldName(), Long.parseLong(String.valueOf(searchFilter.getFieldValues()[0])));

      case LT_EQ:
        return PropertyFilter.le(
            searchFilter.getFieldName(), Long.parseLong(String.valueOf(searchFilter.getFieldValues()[0])));

      case GT:
        return PropertyFilter.gt(
            searchFilter.getFieldName(), Long.parseLong(String.valueOf(searchFilter.getFieldValues()[0])));

      case GE:
        return PropertyFilter.ge(
            searchFilter.getFieldName(), Long.parseLong(String.valueOf(searchFilter.getFieldValues()[0])));

      default:
        throw new IllegalArgumentException("Not supported filter: " + searchFilter);
    }
  }

  @Override
  public boolean supportsInOperator() {
    return false;
  }
}
