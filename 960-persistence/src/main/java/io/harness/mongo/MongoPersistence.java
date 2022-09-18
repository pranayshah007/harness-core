/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.MongoUtils.setUnsetOnInsert;
import static io.harness.persistence.HQuery.allChecks;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.mongo.SampleEntity.SampleEntityKeys;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;
import io.harness.persistence.HQuery.QueryChecks;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.Store;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UserProvider;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.ReadPreference;
import com.mongodb.WriteResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.InsertOptions;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateOpsImpl;
import org.mongodb.morphia.query.UpdateResults;

@Singleton
@Slf4j
public class MongoPersistence implements HPersistence {
  @Override
  public <T> PageResponse<T> query(Class<T> cls, PageRequest<T> req) {
    return query(cls, req, allChecks);
  }

  public <T> PageResponse<T> querySecondary(Class<T> cls, PageRequest<T> req) {
    return querySecondary(cls, req, allChecks);
  }

  @Override
  public <T> PageResponse<T> query(Class<T> cls, PageRequest<T> req, Set<QueryChecks> queryChecks) {
    AdvancedDatastore advancedDatastore = getDatastore(cls);
    Query<T> query = advancedDatastore.createQuery(cls);

    ((HQuery) query).setQueryChecks(queryChecks);
    Mapper mapper = ((DatastoreImpl) advancedDatastore).getMapper();

    return PageController.queryPageRequest(advancedDatastore, query, mapper, cls, req);
  }

  public <T> PageResponse<T> querySecondary(Class<T> cls, PageRequest<T> req, Set<QueryChecks> queryChecks) {
    AdvancedDatastore advancedDatastore = getDatastore(cls);
    Query<T> query = advancedDatastore.createQuery(cls);
    query.useReadPreference(ReadPreference.secondaryPreferred());

    ((HQuery) query).setQueryChecks(queryChecks);
    Mapper mapper = ((DatastoreImpl) advancedDatastore).getMapper();

    return PageController.queryPageRequest(advancedDatastore, query, mapper, cls, req);
  }

  @Value
  @Builder
  private static class Info {
    private String uri;
    // private Set<Class> classes;
  }

  @Inject Morphia morphia;
  @Inject IndexManager indexManager;

  private Map<String, Info> storeInfo = new ConcurrentHashMap<>();
  private Map<Class, Store> classStores = new ConcurrentHashMap<>();
  private Map<String, AdvancedDatastore> datastoreMap;
  @Inject UserProvider userProvider;

  @Inject
  public MongoPersistence(@Named("primaryDatastore") AdvancedDatastore primaryDatastore) {
    datastoreMap = new HashMap<>();
    datastoreMap.put(DEFAULT_STORE.getName(), primaryDatastore);
  }

  @Override
  public Duration healthExpectedResponseTimeout() {
    return ofSeconds(5);
  }

  @Override
  public Duration healthValidFor() {
    return ofSeconds(15);
  }

  @Override
  public void isHealthy() {
    List<AdvancedDatastore> datastores = datastoreMap.values().stream().distinct().collect(toList());
    for (AdvancedDatastore datastore : datastores) {
      datastore.getDB().getStats();
    }
  }

  @Override
  public void register(Store store, String uri) {
    storeInfo.put(store.getName(), Info.builder().uri(uri).build());
  }

  @Override
  public void registerDatastore(String storeName, AdvancedDatastore datastore) {
    datastoreMap.put(storeName, datastore);
  }

  @Override
  public AdvancedDatastore getDatastore(Store store) {
    return datastoreMap.computeIfAbsent(store.getName(), key -> {
      Info info = storeInfo.get(store.getName());
      if (info == null || isEmpty(info.getUri())) {
        return getDatastore(DEFAULT_STORE);
      }
      return MongoModule.createDatastore(morphia, info.getUri());
    });
  }

  @Override
  public Map<Class, Store> getClassStores() {
    return classStores;
  }

  @Override
  public DBCollection getCollection(Store store, String collectionName) {
    return getDatastore(store).getDB().getCollection(collectionName);
  }

  @Override
  public DBCollection getCollection(Class cls) {
    AdvancedDatastore datastore = getDatastore(cls);
    return datastore.getDB().getCollection(datastore.getCollection(cls).getName());
  }

  @Override
  public void ensureIndexForTesting(Class cls) {
    AdvancedDatastore datastore = getDatastore(cls);
    MappedClass mappedClass = morphia.getMapper().getMCMap().get(cls.getName());
    IndexManagerSession session = new IndexManagerSession(datastore, null, AUTO);
    session.processCollection(mappedClass, getCollection(cls));
  }

  @Override
  public void close() {
    Set<AdvancedDatastore> datastores = new HashSet<>();
    datastores.addAll(datastoreMap.values());

    for (AdvancedDatastore datastore : datastores) {
      datastore.getMongo().close();
    }
  }

  @Override
  public <T extends PersistentEntity> Query<T> createQuery(Class<T> cls) {
    return getDatastore(cls).createQuery(cls);
  }

  public <T extends PersistentEntity> Query<T> createAnalyticsQuery(Class<T> cls) {
    return getDefaultAnalyticsDatastore(cls).createQuery(cls);
  }

  @Override
  public <T extends PersistentEntity> Query<T> createQuery(Class<T> cls, Set<QueryChecks> queryChecks) {
    Query<T> query = createQuery(cls);
    ((HQuery) query).setQueryChecks(queryChecks);
    return query;
  }

  @Override
  public <T extends PersistentEntity> Query<T> createQueryForCollection(String collectionName) {
    Class classFromCollection = morphia.getMapper().getClassFromCollection(collectionName);
    return createQuery(classFromCollection);
  }

  @Override
  public <T extends PersistentEntity> Query<T> createQueryForCollection(
      String collectionName, Set<QueryChecks> queryChecks) {
    Query<T> query = createQueryForCollection(collectionName);
    ((HQuery) query).setQueryChecks(queryChecks);
    return query;
  }

  @Override
  public <T extends PersistentEntity> UpdateOperations<T> createUpdateOperations(Class<T> cls) {
    return getDatastore(cls).createUpdateOperations(cls);
  }

  @Override
  public <T extends PersistentEntity> T convertToEntity(Class<T> cls, DBObject dbObject) {
    AdvancedDatastore advancedDatastore = getDatastore(cls);
    return morphia.fromDBObject(advancedDatastore, cls, dbObject);
  }

  private <T extends PersistentEntity> void onEntityUpdate(T entity, long currentTime) {
    if (entity instanceof UpdatedByAware) {
      UpdatedByAware updatedFromAware = (UpdatedByAware) entity;
      updatedFromAware.setLastUpdatedBy(userProvider.activeUser());
    }
    if (entity instanceof UpdatedAtAware) {
      UpdatedAtAware updatedAtAware = (UpdatedAtAware) entity;
      updatedAtAware.setLastUpdatedAt(currentTime);
    }
  }

  private <T extends PersistentEntity> void onSave(T entity) {
    if (entity instanceof UuidAware) {
      UuidAware uuidAware = (UuidAware) entity;
      if (uuidAware.getUuid() == null) {
        uuidAware.setUuid(generateUuid());
      }
    }

    long currentTime = currentTimeMillis();
    if (entity instanceof CreatedByAware) {
      CreatedByAware createdByAware = (CreatedByAware) entity;
      if (createdByAware.getCreatedBy() == null) {
        createdByAware.setCreatedBy(userProvider.activeUser());
      }
    }
    if (entity instanceof CreatedAtAware) {
      CreatedAtAware createdAtAware = (CreatedAtAware) entity;
      if (createdAtAware.getCreatedAt() == 0) {
        createdAtAware.setCreatedAt(currentTime);
      }
    }

    onEntityUpdate(entity, currentTime);
  }

  @Override
  public <T extends PersistentEntity> String save(T entity) {
    onSave(entity);
    AdvancedDatastore datastore = getDatastore(entity);
    return HPersistence.retry(() -> datastore.save(entity).getId().toString());
  }

  @Override
  public <T extends PersistentEntity> List<String> save(List<T> ts) {
    ts.removeIf(Objects::isNull);
    List<String> ids = new ArrayList<>();
    for (T entity : ts) {
      ids.add(save(entity));
    }
    return ids;
  }

  @Override
  public <T extends PersistentEntity> List<String> saveBatch(List<T> ts) {
    ts.removeIf(Objects::isNull);
    if (ts.size() == 0) {
      return Arrays.asList();
    }
    for (T entity : ts) {
      onSave(entity);
    }
    AdvancedDatastore datastore = getDatastore(ts.get(0));
    return HPersistence.retry(()
                                  -> StreamSupport.stream(datastore.insert(ts).spliterator(), false)
                                         .map(key -> key.getId().toString())
                                         .collect(toList()));
  }

  @Override
  public <T extends PersistentEntity> void saveIgnoringDuplicateKeys(List<T> ts) {
    for (Iterator<T> iterator = ts.iterator(); iterator.hasNext();) {
      T entity = iterator.next();
      if (entity == null) {
        iterator.remove();
        continue;
      }
      onSave(entity);
    }

    if (isEmpty(ts)) {
      return;
    }

    AdvancedDatastore datastore = getDatastore(ts.get(0));

    InsertOptions insertOptions = new InsertOptions();
    insertOptions.continueOnError(true);
    try {
      HPersistence.retry(() -> datastore.insert(ts, insertOptions));
    } catch (DuplicateKeyException ignore) {
      // ignore
    }
  }

  @Override
  public <T extends PersistentEntity> String insert(T entity) {
    onSave(entity);
    AdvancedDatastore datastore = getDatastore(entity);
    return HPersistence.retry(() -> datastore.insert(entity).getId().toString());
  }

  @Override
  public <T extends PersistentEntity> String insertIgnoringDuplicateKeys(T entity) {
    onSave(entity);
    AdvancedDatastore datastore = getDatastore(entity);
    try {
      return HPersistence.retry(() -> datastore.insert(entity).getId().toString());
    } catch (DuplicateKeyException ignore) {
      // ignore
    }
    if (entity instanceof UuidAccess) {
      return ((UuidAccess) entity).getUuid();
    }

    return null;
  }

  @Override
  public <T extends PersistentEntity> T get(Class<T> cls, String id) {
    return createQuery(cls).filter(ID_KEY, id).get();
  }

  @Override
  public <T extends PersistentEntity> boolean delete(Class<T> cls, String uuid) {
    AdvancedDatastore datastore = getDatastore(cls);
    return HPersistence.retry(() -> {
      WriteResult result = datastore.delete(cls, uuid);
      return !(result == null || result.getN() == 0);
    });
  }

  @Override
  public <T extends PersistentEntity> boolean delete(Query<T> query) {
    return deleteOnServer(query);
  }

  @Override
  public final <T extends PersistentEntity> boolean deleteOnServer(Query<T> query) {
    AdvancedDatastore datastore = getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> {
      WriteResult result = datastore.delete(query);
      return !(result == null || result.getN() == 0);
    });
  }

  @Override
  public <T extends PersistentEntity> boolean delete(T entity) {
    AdvancedDatastore datastore = getDatastore(entity);
    return HPersistence.retry(() -> {
      WriteResult result = datastore.delete(entity);
      return !(result == null || result.getN() == 0);
    });
  }

  @Override
  public <T extends PersistentEntity> T upsert(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions options) {
    if (!options.isUpsert()) {
      throw new InvalidArgumentsException("The options do not have the upsert flag set");
    }

    // TODO: add encryption handling; right now no encrypted classes use upsert
    // When necessary, we can fix this by adding Class<T> cls to the args and then similar to updateField

    long currentTime = currentTimeMillis();

    if (CreatedByAware.class.isAssignableFrom(query.getEntityClass())) {
      setUnsetOnInsert(updateOperations, SampleEntityKeys.createdBy, userProvider.activeUser());
    }

    if (CreatedAtAware.class.isAssignableFrom(query.getEntityClass())) {
      updateOperations.setOnInsert(SampleEntityKeys.createdAt, currentTime);
    }

    onUpdate(query, updateOperations, currentTime);
    // TODO: this ignores the read preferences in the query
    AdvancedDatastore datastore = getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> datastore.findAndModify(query, updateOperations, options));
  }

  private <T extends PersistentEntity> void onEntityUpdate(
      T entity, UpdateOperations<T> updateOperations, long currentTime) {
    if (entity instanceof UpdatedByAware) {
      MongoUtils.setUnset(updateOperations, SampleEntityKeys.lastUpdatedBy, userProvider.activeUser());
    }
    if (entity instanceof UpdatedAtAware) {
      updateOperations.set(SampleEntityKeys.lastUpdatedAt, currentTime);
    }

    if (log.isDebugEnabled()) {
      log.debug("Update {} with {}", entity.getClass(), ((UpdateOpsImpl) updateOperations).getOps().toString());
    }
  }

  private <T extends PersistentEntity> void onUpdate(
      Query<T> query, UpdateOperations<T> updateOperations, long currentTime) {
    if (UpdatedByAware.class.isAssignableFrom(query.getEntityClass())) {
      MongoUtils.setUnset(updateOperations, SampleEntityKeys.lastUpdatedBy, userProvider.activeUser());
    }

    if (UpdatedAtAware.class.isAssignableFrom(query.getEntityClass())) {
      updateOperations.set(SampleEntityKeys.lastUpdatedAt, currentTime);
    }

    if (log.isDebugEnabled()) {
      log.debug("Update {} with {}", query.getEntityClass().getName(),
          ((UpdateOpsImpl) updateOperations).getOps().toString());
    }
  }

  @Override
  public <T extends PersistentEntity> UpdateResults update(T entity, UpdateOperations<T> ops) {
    // TODO: add encryption handling; right now no encrypted classes use update
    // When necessary, we can fix this by adding Class<T> cls to the args and then similar to updateField
    onEntityUpdate(entity, ops, currentTimeMillis());
    AdvancedDatastore datastore = getDatastore(entity);
    return HPersistence.retry(() -> datastore.update(entity, ops));
  }

  @Override
  public <T extends PersistentEntity> UpdateResults update(Query<T> updateQuery, UpdateOperations<T> updateOperations) {
    // TODO: add encryption handling; right now no encrypted classes use update
    // When necessary, we can fix this by adding Class<T> cls to the args and then similar to updateField
    onUpdate(updateQuery, updateOperations, currentTimeMillis());
    AdvancedDatastore datastore = getDatastore(updateQuery.getEntityClass());
    return HPersistence.retry(() -> datastore.update(updateQuery, updateOperations));
  }

  @Override
  public <T extends PersistentEntity> T findAndModify(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions findAndModifyOptions) {
    onUpdate(query, updateOperations, currentTimeMillis());
    AdvancedDatastore datastore = getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> datastore.findAndModify(query, updateOperations, findAndModifyOptions));
  }

  @Override
  public <T extends PersistentEntity> T findAndModifySystemData(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions findAndModifyOptions) {
    AdvancedDatastore datastore = getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> datastore.findAndModify(query, updateOperations, findAndModifyOptions));
  }

  @Override
  public <T extends PersistentEntity> T findAndDelete(Query<T> query, FindAndModifyOptions findAndModifyOptions) {
    AdvancedDatastore datastore = getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> datastore.findAndDelete(query, findAndModifyOptions));
  }

  @Override
  public <T extends PersistentEntity> String merge(T entity) {
    onEntityUpdate(entity, currentTimeMillis());
    AdvancedDatastore datastore = getDatastore(entity);
    return HPersistence.retry(() -> datastore.merge(entity).getId().toString());
  }
}
