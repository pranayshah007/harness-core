/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.persistence.HQuery.QueryChecks.AUTHORITY;
import static io.harness.persistence.HQuery.QueryChecks.COUNT;
import static io.harness.persistence.HQuery.QueryChecks.VALIDATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.log.CollectionLogContext;
import io.harness.mongo.tracing.TraceMode;
import io.harness.observer.Subject;

import com.google.common.collect.Sets;
import com.mongodb.DBCollection;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.client.MongoCursor;
import dev.morphia.Datastore;
import dev.morphia.Key;
import dev.morphia.query.CountOptions;
import dev.morphia.query.Criteria;
import dev.morphia.query.FindOptions;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.MorphiaKeyIterator;
import dev.morphia.query.Query;
import dev.morphia.query.QueryImpl;
import dev.morphia.query.internal.MorphiaKeyCursor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * The type H query.
 *
 * @param <T> the type parameter
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class HQuery<T> extends QueryImpl<T> {
  public enum QueryChecks { VALIDATE, AUTHORITY, COUNT }

  public static final Set<QueryChecks> allChecks = EnumSet.<QueryChecks>of(VALIDATE, AUTHORITY, COUNT);
  public static final Set<QueryChecks> excludeValidate = EnumSet.<QueryChecks>of(AUTHORITY, COUNT);
  public static final Set<QueryChecks> excludeAuthority = EnumSet.<QueryChecks>of(VALIDATE, COUNT);
  public static final Set<QueryChecks> excludeCount = EnumSet.<QueryChecks>of(AUTHORITY, VALIDATE);
  public static final Set<QueryChecks> excludeAuthorityCount = EnumSet.<QueryChecks>of(QueryChecks.VALIDATE);

  private final TraceMode traceMode;
  private final Subject<Tracer> tracerSubject;
  private Set<QueryChecks> queryChecks = allChecks;
  private final int maxOperationTimeInMillis;
  private final int maxDocumentsToBeFetched;
  private List<Criteria> children;

  private static final Set<String> requiredFilterArgs = Sets.newHashSet("accountId", "accounts", "appId", "accountIds");

  public void setQueryChecks(Set<QueryChecks> queryChecks) {
    this.queryChecks = queryChecks;
    if (queryChecks.contains(VALIDATE)) {
      enableValidation();
    } else {
      disableValidation();
    }
  }

  /**
   * Creates a Query for the given type and collection
   *
   * @param clazz         the type to return
   * @param coll          the collection to query
   * @param ds            the Datastore to use
   * @param traceMode     the trace mode
   * @param tracerSubject the trace subject used in case trace mode is ENABLED
   */
  public HQuery(Class<T> clazz, DBCollection coll, Datastore ds, TraceMode traceMode, Subject<Tracer> tracerSubject,
      int maxOperationTimeInMillis, int maxDocumentsToBeFetched) {
    super(clazz, coll, ds);
    this.traceMode = traceMode;
    this.tracerSubject = tracerSubject;
    this.maxOperationTimeInMillis = maxOperationTimeInMillis;
    this.maxDocumentsToBeFetched = maxDocumentsToBeFetched;
    this.children = new ArrayList<>();
  }

  public MongoCursor<T> iterator() {
    log.error("Do not use the query as iterator directly.", new Exception(""));
    return this.find();
  }

  public List<Criteria> getChildren() {
    return this.children;
  }

  @Override
  public void add(Criteria... criteria) {
    super.add(criteria);
    children.addAll(Arrays.asList(criteria));
  }

  @Override
  public void remove(Criteria criteria) {
    super.remove(criteria);
    this.children.remove(criteria);
  }

  private void checkKeyListSize(List<Key<T>> list) {
    if (!queryChecks.contains(COUNT)) {
      return;
    }

    if (list.size() > 5000) {
      log.warn("Key list query returns {} items.  Consider using an Iterator to avoid causing OOM", list.size(),
          new Exception());
    }
  }

  private void checkListSize(List<T> list) {
    if (!queryChecks.contains(COUNT)) {
      return;
    }

    if (list.size() > 1000) {
      log.warn(
          "List query returns {} items. Consider using an Iterator to avoid causing OOM", list.size(), new Exception());
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public T get(FindOptions options) {
    String collectionName = super.getCollection().getName();
    try (AutoLogContext ignore = new CollectionLogContext(collectionName, OVERRIDE_ERROR)) {
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      return HPersistence.retry(() -> super.get(options));
    } catch (MongoExecutionTimeoutException ex) {
      log.error("Get query {} exceeded max time limit of {} ms for collection {} with error {}", this,
          maxOperationTimeInMillis, collectionName, ex);
      throw ex;
    }
  }

  @Override
  public Key<T> getKey(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      return HPersistence.retry(() -> super.getKey(options));
    } catch (MongoExecutionTimeoutException ex) {
      log.error("getKey query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  @Override
  public List<Key<T>> asKeyList(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      enforceHarnessRules();
      traceQuery();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      return HPersistence.retry(() -> {
        final List<Key<T>> list = super.asKeyList(options);
        checkKeyListSize(list);
        return list;
      });
    } catch (MongoExecutionTimeoutException ex) {
      log.error("asKeyList query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public List<T> asList(FindOptions options) {
    String collectionName = super.getCollection().getName();
    try (AutoLogContext ignore = new CollectionLogContext(collectionName, OVERRIDE_ERROR)) {
      enforceHarnessRules();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      traceQuery();
      return HPersistence.retry(() -> {
        final List<T> list = super.asList(options);
        checkListSize(list);
        return list;
      });
    } catch (MongoExecutionTimeoutException ex) {
      log.error("asList query {} exceeded max time limit of {} ms for collection {} with error {}", this,
          maxOperationTimeInMillis, collectionName, ex);
      throw ex;
    }
  }

  @Override
  public long count() {
    CountOptions countOptions = new CountOptions();
    return count(countOptions);
  }

  @Override
  public long count(final CountOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      return HPersistence.retry(() -> super.count(options));
    } catch (MongoExecutionTimeoutException ex) {
      log.error("count query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  public MorphiaIterator<T, T> fetch() {
    enforceHarnessRules();
    traceQuery();
    return super.fetch();
  }

  public MorphiaIterator<T, T> fetch(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      return HPersistence.retry(() -> { return super.fetch(options); });
    } catch (MongoExecutionTimeoutException ex) {
      log.error("fetch query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  @Override
  public MorphiaIterator<T, T> fetchEmptyEntities(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      enforceHarnessRules();
      traceQuery();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      return HPersistence.retry(() -> { return super.fetchEmptyEntities(options); });
    } catch (MongoExecutionTimeoutException ex) {
      log.error("fetchEmptyEntities query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  @Override
  public MorphiaKeyIterator<T> fetchKeys(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      enforceHarnessRules();
      traceQuery();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      return HPersistence.retry(() -> super.fetchKeys(options));
    } catch (MongoExecutionTimeoutException ex) {
      log.error("fetchKeys query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  @Override
  public Query<T> search(String search) {
    enforceHarnessRules();
    traceQuery();
    return super.search(search);
  }

  @Override
  public Query<T> search(String search, String language) {
    enforceHarnessRules();
    traceQuery();
    return super.search(search, language);
  }

  @Override
  public MorphiaKeyCursor<T> keys(FindOptions options) {
    Class<T> entityClass = null;
    try {
      entityClass = super.getEntityClass();
      enforceHarnessRules();
      traceQuery();
      if (options.getMaxTime(TimeUnit.MILLISECONDS) == 0) {
        options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      }
      if (options.getLimit() == 0) {
        options.limit(maxDocumentsToBeFetched);
      }
      return HPersistence.retry(() -> super.keys(options));
    } catch (MongoExecutionTimeoutException ex) {
      log.error("keys query {} exceeded max time limit of {} ms for entityClass {} with error {}", this,
          maxOperationTimeInMillis, entityClass, ex);
      throw ex;
    }
  }

  private void enforceHarnessRules() {
    if (!queryChecks.contains(AUTHORITY)) {
      return;
    }

    if (!this.getChildren().stream().map(Criteria::getFieldName).anyMatch(requiredFilterArgs::contains)) {
      log.warn("QUERY-ENFORCEMENT: appId or accountId must be present in List(Object/Key)/Get/Count/Search query",
          new Exception(""));
    }
  }

  private void traceQuery() {
    if (traceMode == TraceMode.ENABLED) {
      tracerSubject.fireInform(Tracer::traceMorphiaQuery, this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass() || !super.equals(o)) {
      return false;
    }
    HQuery<?> hQuery = (HQuery<?>) o;
    return Objects.equals(queryChecks, hQuery.queryChecks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), queryChecks);
  }
}
