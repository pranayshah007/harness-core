/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.health.HealthMonitor;
import io.harness.ng.DbAliases;
import io.harness.persistence.HQuery.QueryChecks;
import io.harness.persistence.store.Store;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import com.mongodb.client.MongoClient;
import dev.morphia.AdvancedDatastore;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.CountOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface HPersistence extends HealthMonitor {
  String ANALYTICS_STORE_NAME = "analytic";
  Store DEFAULT_STORE = Store.builder().name("default").build();
  Store CG_HARNESS_STORE = Store.builder().name(DbAliases.HARNESS).build();
  Store ANALYTIC_STORE = Store.builder().name(ANALYTICS_STORE_NAME).build();
  static Logger logger() {
    return LoggerFactory.getLogger(HPersistence.class);
  }

  /**
   * Register Uri for the datastore.
   *
   * @param store the store
   * @param uri the datastore uri
   */
  void register(Store store, String uri);

  void registerDatastore(String storeName, AdvancedDatastore datastore);

  /**
   * Gets the datastore.
   *
   * @param store    the store
   * @param readPref the readPref
   * @return         the datastore
   */
  AdvancedDatastore getDatastore(Store store);

  MongoClient getNewMongoClient(Store store);

  /**
   * Gets the datastore.
   *
   * @param entity   the entity
   * @param readPref the readPref
   * @return         the datastore
   */
  default AdvancedDatastore getDatastore(PersistentEntity entity) {
    return getDatastore(entity.getClass());
  }

  default AdvancedDatastore getDatastore(PersistentEntity entity, boolean isMigrationEnabled) {
    if (isMigrationEnabled) {
      Optional<Store> secondaryStore = getSecondaryStore(entity.getClass());
      if (secondaryStore.isPresent()) {
        return getDatastore(secondaryStore.get());
      }
      logger().warn("Serious: Trying to migrate data without secondaryStore.");
    }
    return getDatastore(getPrimaryStore(entity.getClass()));
  }

  Map<Class, Store> getClassStores();

  Map<Class, Store> getSecondaryClassStores();

  boolean isMigrationEnabled(String className);

  /**
   * Gets the datastore.
   *
   * @param cls      the entity class
   * @param readPref the readPref
   * @return         the datastore
   */

  default AdvancedDatastore getDatastore(Class cls) {
    Optional<Store> secondaryStore = getSecondaryStore(cls);
    if (secondaryStore.isPresent() && isMigrationEnabled(cls.getName())) {
      return getDatastore(secondaryStore.get());
    }
    return getDatastore(getPrimaryStore(cls));
  }

  default AdvancedDatastore getDatastore(Class cls, boolean isMigrationEnabled) {
    Optional<Store> secondaryStore = getSecondaryStore(cls);
    if (secondaryStore.isPresent() && isMigrationEnabled) {
      return getDatastore(secondaryStore.get());
    }
    return getDatastore(getPrimaryStore(cls));
  }

  default AdvancedDatastore getDefaultAnalyticsDatastore(Class cls) {
    Map<Class, Store> classStores = new HashMap<>(getClassStores());
    Store classStore = classStores.computeIfAbsent(cls,
        klass
        -> Arrays.stream(cls.getDeclaredAnnotations())
               .filter(annotation -> annotation.annotationType().equals(StoreIn.class))
               .map(annotation -> ((StoreIn) annotation).value())
               .map(name -> Store.builder().name(name).build())
               .findFirst()
               .orElseGet(() -> DEFAULT_STORE));

    // only if the request is for cg db, get from analytics node
    if (DEFAULT_STORE.equals(classStore) || CG_HARNESS_STORE.equals(classStore)) {
      return getDatastore(ANALYTIC_STORE);
    }
    return getDatastore(classStore);
  }

  default Store getPrimaryStore(Class cls) {
    return getClassStores().computeIfAbsent(cls,
        klass
        -> Arrays.stream(cls.getDeclaredAnnotations())
               .filter(annotation -> annotation.annotationType().equals(StoreIn.class))
               .map(annotation -> ((StoreIn) annotation).value())
               .map(name -> Store.builder().name(name).build())
               .findFirst()
               .orElse(DEFAULT_STORE));
  }

  default Optional<Store> getSecondaryStore(Class cls) {
    // using default_store as a marker so that we don't have to compute secondaryStore on each query
    Store secondaryStore = getSecondaryClassStores().computeIfAbsent(cls,
        klass
        -> Arrays.stream(cls.getDeclaredAnnotations())
               .filter(annotation -> annotation.annotationType().equals(SecondaryStoreIn.class))
               .map(annotation -> ((SecondaryStoreIn) annotation).value())
               .map(name -> Store.builder().name(name).build())
               .findFirst()
               .orElse(DEFAULT_STORE));
    if (secondaryStore != DEFAULT_STORE) {
      return Optional.of(secondaryStore);
    }
    return Optional.empty();
  }

  /**
   * Gets the collection.
   *
   * @param store          the store
   * @param collectionName the collection name
   * @param readPref       the readPref
   * @return               the collection
   */
  DBCollection getCollection(Store store, String collectionName);

  /**
   * Gets the collection.
   *
   * @param cls            the class of the collection
   * @param readPref       the readPref
   * @return               the collection
   */
  DBCollection getCollection(Class cls);

  /**
   * Gets the collection.
   *
   * @param cls            the class of the collection that indexes should be ensured
   */
  void ensureIndexForTesting(Class cls);

  /**
   * Close.
   */
  void close();

  CountOptions upToOne = new CountOptions().limit(1);

  /**
   * Creates the query.
   *
   * @param <T> the generic type
   * @param cls the cls
   * @return the query
   */
  <T extends PersistentEntity> Query<T> createQuery(Class<T> cls);

  <T extends PersistentEntity> Query<T> createQuery(Class<T> cls, boolean isMigrationEnabled);

  /**
   * Creates the query for analytics.
   *
   * @param <T> the generic type
   * @param cls the cls
   * @return the query
   */
  <T extends PersistentEntity> Query<T> createAnalyticsQuery(Class<T> cls);

  /**
   * Creates the query for analytics.
   *
   * @param <T> the generic type
   * @param cls the cls
   * @return the query
   */
  <T extends PersistentEntity> Query<T> createAnalyticsQuery(Class<T> cls, Set<QueryChecks> queryChecks);

  /**
   * Creates the query.
   *
   * @param <T>          the generic type
   * @param cls          the cls
   * @param queryChecks  the query checks
   * @return             the query
   */
  <T extends PersistentEntity> Query<T> createQuery(Class<T> cls, Set<QueryChecks> queryChecks);

  <T extends PersistentEntity> Query<T> createQuery(
      Class<T> cls, Set<QueryChecks> queryChecks, boolean isMigrationEnabled);

  /**
   * Creates the query.
   *
   * @param <T>            the generic type
   * @param collectionName the collection name
   * @return               the query
   */
  <T extends PersistentEntity> Query<T> createQueryForCollection(String collectionName);

  /**
   * Creates the query.
   *
   * @param <T>              the generic type
   * @param collectionName   the collection name
   * @param queryChecks      the query checks
   * @return                 the query
   */
  <T extends PersistentEntity> Query<T> createQueryForCollection(String collectionName, Set<QueryChecks> queryChecks);

  /**
   * Creates the update operations.
   *
   * @param cls the cls
   * @return the update operations
   */
  <T extends PersistentEntity> UpdateOperations<T> createUpdateOperations(Class<T> cls);

  <T extends PersistentEntity> UpdateOperations<T> createUpdateOperations(Class<T> cls, boolean isMigrationEnabled);

  /**
   * Convert DBObject to java entity.
   *
   * @param cls the cls
   * @return the update operations
   */
  <T extends PersistentEntity> T convertToEntity(Class<T> cls, DBObject dbObject);

  /**
   * Save.
   *
   * @param entity   the entity
   * @return the key of the entity
   */
  <T extends PersistentEntity> String save(T entity);

  <T extends PersistentEntity> String save(T entity, boolean isMigrationEnabled);

  /**
   * Save.
   *
   * @param entityList list of entities to save
   * @return list of keys
   */
  <T extends PersistentEntity> List<String> save(List<T> entityList);

  /**
   * Save list in one go using a batch.
   *
   * @param entityList list of entities to save
   * @return list of keys
   */
  <T extends PersistentEntity> List<String> saveBatch(List<T> entityList);

  /**
   * Save ignoring duplicate key errors.
   * This saves any new records and skips existing records
   *
   * @param entityList list of entities to save
   */
  <T extends PersistentEntity> void saveIgnoringDuplicateKeys(List<T> entityList);

  /**
   * Insert.
   *
   * @param entity   the entity
   * @return the key of the entity
   */
  <T extends PersistentEntity> String insert(T entity);

  /**
   * Insert.
   *
   * @param entity   the entity
   * @return the key of the entity
   */
  <T extends PersistentEntity> String insertIgnoringDuplicateKeys(T entity);

  /**
   * Get returns the entity with id.
   *
   * @param cls the class of the entity
   * @param id  the id of the entity
   * @return the t
   */
  <T extends PersistentEntity> T get(Class<T> cls, String id);

  <T extends PersistentEntity> T get(Class<T> cls, String id, boolean isMigrationEnabled);

  /**
   * Delete.
   *
   * @param cls  the class of the entity
   * @param id the id
   * @return true, if successful
   */
  <T extends PersistentEntity> boolean delete(Class<T> cls, String id);

  /**
   * Delete.
   *
   * @param query query that selects one or more items to delete
   * @return true, if successful
   */
  <T extends PersistentEntity> boolean delete(Query<T> query);

  /**
   * Delete the items matching the query without deserializing them on the client side.
   *
   * @param query query that selects one or more items to delete
   * @return true, if successful
   */
  <T extends PersistentEntity> boolean deleteOnServer(Query<T> query);

  <T extends PersistentEntity> boolean deleteOnServer(Query<T> query, boolean isMigrationEnabled);

  /**
   * Delete.
   *
   * @param entity entity to delete
   * @return true, if successful
   */
  <T extends PersistentEntity> boolean delete(T entity);

  FindAndModifyOptions upsertReturnNewOptions = new FindAndModifyOptions().upsert(true).returnNew(true);
  FindAndModifyOptions upsertReturnOldOptions = new FindAndModifyOptions().upsert(true).returnNew(false);

  /**
   * Upsert.
   *
   * @param query            the query
   * @param updateOperations the update operations
   * @return the entity
   */
  default<T extends PersistentEntity> void upsert(Query<T> query, UpdateOperations<T> updateOperations) {
    upsert(query, updateOperations, upsertReturnNewOptions);
  }

  /**
   * Upsert.
   *
   * @param query            the query
   * @param updateOperations the update operations
   * @return the entity
   */
  <T extends PersistentEntity> T upsert(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions options);

  /**
   * Update.
   *
   * @param ent the ent
   * @param ops the ops
   * @return the update results
   */
  <T extends PersistentEntity> UpdateResults update(T ent, UpdateOperations<T> ops);

  <T extends PersistentEntity> UpdateResults update(T ent, UpdateOperations<T> ops, boolean isMigrationEnabled);

  /**
   * Update.
   *
   * @param updateQuery      the update query
   * @param updateOperations the update operations
   * @return the update results
   */
  <T extends PersistentEntity> UpdateResults update(Query<T> updateQuery, UpdateOperations<T> updateOperations);

  <T extends PersistentEntity> UpdateResults update(
      Query<T> updateQuery, UpdateOperations<T> updateOperations, boolean isMigrationEnabled);

  FindAndModifyOptions returnNewOptions = new FindAndModifyOptions().upsert(false).returnNew(true);
  FindAndModifyOptions returnOldOptions = new FindAndModifyOptions().upsert(false).returnNew(false);

  /**
   * Find and modify.
   *
   * @param query                the query
   * @param updateOperations     the update operations
   * @param findAndModifyOptions the find and modify options
   * @return previous or new entity depending on options
   */
  <T extends PersistentEntity> T findAndModify(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions findAndModifyOptions);

  <T extends PersistentEntity> T findAndModify(Query<T> query, UpdateOperations<T> updateOperations,
      FindAndModifyOptions findAndModifyOptions, boolean isMigrationEnabled);

  /**
   * Find and modify data that is system and it should not refresh any of the trackers.
   *
   * @param query                the query
   * @param updateOperations     the update operations
   * @param findAndModifyOptions the find and modify options
   * @return previous or new entity depending on options
   */
  <T extends PersistentEntity> T findAndModifySystemData(
      Query<T> query, UpdateOperations<T> updateOperations, FindAndModifyOptions findAndModifyOptions);

  <T extends PersistentEntity> T findAndModifySystemData(Query<T> query, UpdateOperations<T> updateOperations,
      FindAndModifyOptions findAndModifyOptions, boolean isMigrationEnabled);

  /**
   * Find and delete.
   *
   * @param query                the query
   * @param findAndModifyOptions the delete and modify options
   * @return previous entity depending on options
   */
  <T extends PersistentEntity> T findAndDelete(Query<T> query, FindAndModifyOptions findAndModifyOptions);

  /**
   * Merge.
   *
   * @param entity   the entity to merge
   * @return the key of the entity
   */
  <T extends PersistentEntity> String merge(T entity);

  int RETRIES = 3;

  /**
   * Query.
   *
   * @param <T> the generic type
   * @param cls the cls
   * @param req the req
   * @return the page response
   */
  <T> PageResponse<T> query(Class<T> cls, PageRequest<T> req);

  /**
   * Query. Read preference is set to secondary mongo
   *
   * @param <T> the generic type
   * @param cls the cls
   * @param req the req
   * @return the page response
   */

  <T> PageResponse<T> querySecondary(Class<T> cls, PageRequest<T> req);

  /**
   * Query. Read preference is set to analytics mongo
   *
   * @param <T> the generic type
   * @param cls the cls
   * @param req the req
   * @return the page response
   */

  <T> PageResponse<T> queryAnalytics(Class<T> cls, PageRequest<T> req);

  /**
   * Query page response.
   *
   * @param <T>          the type parameter
   * @param cls          the cls
   * @param req          the req
   * @param queryChecks  the query checks
   * @return             the page response
   */
  <T> PageResponse<T> query(Class<T> cls, PageRequest<T> req, Set<QueryChecks> queryChecks);

  interface Executor<R> {
    R execute();
  }

  static <R> R retry(Executor<R> executor) {
    for (int i = 1; i < RETRIES; ++i) {
      try {
        return executor.execute();
      } catch (MongoSocketOpenException | MongoSocketReadException e) {
        logger().error("Exception ignored on retry ", e);
        continue;
      } catch (RuntimeException exception) {
        if (ExceptionUtils.cause(MongoSocketOpenException.class, exception) != null) {
          continue;
        }
        if (ExceptionUtils.cause(MongoSocketReadException.class, exception) != null) {
          continue;
        }
        throw exception;
      }
    }
    // one last try
    return executor.execute();
  }

  default FindOptions analyticNodePreferenceOptions() {
    return new FindOptions().readPreference(
        ReadPreference.secondaryPreferred(new TagSet(new Tag("nodeType", "ANALYTICS"))));
  }

  default int getMaxTimeMs(Class cls) {
    AdvancedDatastore datastore = getDatastore(cls);
    if (datastore.getQueryFactory() instanceof QueryFactory) {
      QueryFactory queryFactory = (QueryFactory) datastore.getQueryFactory();
      return queryFactory.getMaxOperationTimeInMillis();
    }
    return 0;
  }

  default int getMaxDocumentLimit(Class cls) {
    AdvancedDatastore datastore = getDatastore(cls);
    if (datastore.getQueryFactory() instanceof QueryFactory) {
      QueryFactory queryFactory = (QueryFactory) datastore.getQueryFactory();
      return queryFactory.getMaxDocumentsToBeFetched();
    }
    return 0;
  }
}
