/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.health.HealthMonitor;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.tracing.TraceMode;
import io.harness.ng.persistence.tracer.NgTracer;
import io.harness.observer.Subject;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.DocumentCallbackHandler;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapreduce.MapReduceOptions;
import org.springframework.data.mongodb.core.mapreduce.MapReduceResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.data.util.CloseableIterator;
import org.springframework.lang.Nullable;

@SuppressWarnings("NullableProblems")
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class HMongoTemplate extends MongoTemplate implements HealthMonitor {
  private static final int RETRIES = 3;
  private final int maxOperationTimeInMillis;
  private final int maxDocumentsToBeFetched;

  public static final FindAndModifyOptions upsertReturnNewOptions =
      new FindAndModifyOptions().upsert(true).returnNew(true);
  public static final FindAndModifyOptions upsertReturnOldOptions =
      new FindAndModifyOptions().upsert(true).returnNew(false);
  private static final String ERROR_MSG_QUERY_EXCEEDED_TIME_LIMIT =
      "query [{}] for collection [{}] exceeded max time limit of [{}] ms with error {}.";

  private final TraceMode traceMode;
  private static final String MAX_TIME = "maxTimeMS";

  @Getter private final Subject<NgTracer> tracerSubject = new Subject<>();

  public HMongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter mongoConverter, MongoConfig mongoConfig) {
    super(mongoDbFactory, mongoConverter);
    this.traceMode = mongoConfig.getTraceMode();
    this.maxOperationTimeInMillis = mongoConfig.getMaxOperationTimeInMillis();
    this.maxDocumentsToBeFetched = mongoConfig.getMaxDocumentsToBeFetched();
  }

  @Nullable
  @Override
  public <T> T findAndModify(Query query, UpdateDefinition update, Class<T> entityClass) {
    traceQuery(query, entityClass);
    return retry(
        () -> findAndModify(query, update, new FindAndModifyOptions(), entityClass, getCollectionName(entityClass)));
  }

  @Nullable
  @Override
  public <T> T findAndModify(Query query, UpdateDefinition update, FindAndModifyOptions options, Class<T> entityClass) {
    traceQuery(query, entityClass);
    return retry(() -> findAndModify(query, update, options, entityClass, getCollectionName(entityClass)));
  }

  @Override
  public UpdateResult upsert(Query query, UpdateDefinition update, Class<?> entityClass) {
    traceQuery(query, entityClass);
    return retry(() -> upsert(query, update, entityClass, getCollectionName(entityClass)));
  }

  @Override
  public <T> T findAndModify(
      Query query, UpdateDefinition update, FindAndModifyOptions options, Class<T> entityClass, String collectionName) {
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      return super.findAndModify(query, update, options, entityClass, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
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
    executeCommand("{ buildInfo: 1 }");
  }

  @Override
  public <T> List<T> find(Query query, Class<T> entityClass, String collectionName) {
    List<T> list = new ArrayList<>();
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      if (query.getLimit() == 0) {
        query.limit(maxDocumentsToBeFetched);
      }
      list = super.find(query, entityClass, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
    if (checkIfListIsLarge(list)) {
      log.warn("find query {} returns {} items for collection {}. Consider using an Iterator to avoid causing OOM",
          query, list.size(), collectionName, new Exception());
    }
    return list;
  }

  @Override
  public <T> List<T> findAll(Class<T> entityClass, String collectionName) {
    Query query = new Query();
    return find(query, entityClass, collectionName);
  }

  @Override
  public <T> T findOne(Query query, Class<T> entityClass, String collectionName) {
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      return super.findOne(query, entityClass, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  public <T> List<T> findDistinct(
      Query query, String field, String collectionName, Class<?> entityClass, Class<T> resultClass) {
    List<T> list = new ArrayList<>();
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      list = super.findDistinct(query, field, collectionName, entityClass, resultClass);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
    if (checkIfListIsLarge(list)) {
      log.warn(
          "findDistinct query {} returns {} items for collection {}. Consider using an Iterator to avoid causing OOM",
          query, list.size(), collectionName, new Exception());
    }
    return list;
  }

  @Override
  public <S, T> T findAndReplace(Query query, S replacement, FindAndReplaceOptions options, Class<S> entityType,
      String collectionName, Class<T> resultType) {
    try {
      traceQuery(query, entityType);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      return super.findAndReplace(query, replacement, options, entityType, collectionName, resultType);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  public <T> T findAndRemove(Query query, Class<T> entityClass, String collectionName) {
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      return super.findAndRemove(query, entityClass, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  public <T> List<T> findAllAndRemove(Query query, Class<T> entityClass, String collectionName) {
    List<T> list = new ArrayList<>();
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      list = super.findAllAndRemove(query, entityClass, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
    if (checkIfListIsLarge(list)) {
      log.warn(
          "FindAllAndRemove query {} returns {} items for collection {}. Consider using an Iterator to avoid causing OOM",
          query, list.size(), collectionName, new Exception());
    }
    return list;
  }

  @Override
  public <T> MapReduceResults<T> mapReduce(Query query, String inputCollectionName, String mapFunction,
      String reduceFunction, @Nullable MapReduceOptions mapReduceOptions, Class<T> entityClass) {
    try {
      traceQuery(query, entityClass);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      if (mapReduceOptions == null) {
        mapReduceOptions = new MapReduceOptions();
      }
      if (query.getLimit() == 0) {
        query.limit(maxDocumentsToBeFetched);
      }
      return super.mapReduce(query, inputCollectionName, mapFunction, reduceFunction, mapReduceOptions, entityClass);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, inputCollectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  protected long doCount(String collectionName, Document filter, CountOptions options) {
    try {
      options.maxTime(maxOperationTimeInMillis, TimeUnit.MILLISECONDS);
      return super.doCount(collectionName, filter, options);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        log.error("count operation for collection [{}] exceeded max time limit of [{}] ms with error {}.",
            collectionName, maxOperationTimeInMillis, ex);
        throw(MongoExecutionTimeoutException) ex.getCause();
      }
      throw ex;
    }
  }

  @Override
  public <T> CloseableIterator<T> stream(Query query, Class<T> entityType, String collectionName) {
    try {
      traceQuery(query, entityType);
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      if (query.getLimit() == 0) {
        query.limit(maxDocumentsToBeFetched);
      }
      return super.stream(query, entityType, collectionName);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  public void executeQuery(Query query, String collectionName, DocumentCallbackHandler dch) {
    try {
      if (query.getMeta().getMaxTimeMsec() == null) {
        query.maxTime(Duration.ofMillis(maxOperationTimeInMillis));
      }
      if (query.getLimit() == 0) {
        query.limit(maxDocumentsToBeFetched);
      }
      super.executeQuery(query, collectionName, dch);
    } catch (UncategorizedMongoDbException ex) {
      if (isMongoExecutionTimeoutException(ex)) {
        logAndThrowMongoExecutionTimeoutException(query, collectionName, ex);
      }
      throw ex;
    }
  }

  @Override
  protected <O> AggregationResults<O> aggregate(Aggregation aggregation, String collectionName, Class<O> outputType,
      @Nullable AggregationOperationContext context) {
    final AggregationResults<O> results =
        super.aggregate(applyLimitsAndMaxTimeToAggregation(aggregation), collectionName, outputType, context);
    if (checkIfListIsLarge(results.getMappedResults())) {
      log.warn("Aggregate query {} returns {} items for collection {}. Consider using an Iterator to avoid causing OOM",
          aggregation, results.getMappedResults().size(), collectionName, new Exception());
    }
    return results;
  }

  @Override
  protected <O> CloseableIterator<O> aggregateStream(Aggregation aggregation, String collectionName,
      Class<O> outputType, @Nullable AggregationOperationContext context) {
    return super.aggregateStream(applyLimitsAndMaxTimeToAggregation(aggregation), collectionName, outputType, context);
  }

  private Aggregation applyLimitsAndMaxTimeToAggregation(Aggregation aggregation) {
    Document document = aggregation.getOptions().toDocument();
    document.append(MAX_TIME, (long) maxOperationTimeInMillis);
    List<AggregationOperation> aggregationOperations = new ArrayList<>(aggregation.getPipeline().getOperations());
    boolean isLimitOperationApplied = aggregationOperations.stream().anyMatch(
        aggregationOperation -> LimitOperation.class.equals(aggregationOperation.getClass()));
    if (!isLimitOperationApplied) {
      aggregationOperations.add(Aggregation.limit(maxDocumentsToBeFetched));
    }
    return newAggregation(aggregationOperations).withOptions(AggregationOptions.fromDocument(document));
  }

  private <T> void traceQuery(Query query, Class<T> entityClass) {
    if (traceMode == TraceMode.ENABLED) {
      tracerSubject.fireInform(NgTracer::traceSpringQuery, query, entityClass, this);
    }
  }

  private <T> boolean checkIfListIsLarge(List<T> list) {
    return list.size() > 1000;
  }

  private void logAndThrowMongoExecutionTimeoutException(Query query, String collectionName, Exception ex)
      throws MongoExecutionTimeoutException {
    log.error(ERROR_MSG_QUERY_EXCEEDED_TIME_LIMIT, query, collectionName, maxOperationTimeInMillis, ex);
    throw(MongoExecutionTimeoutException) ex.getCause();
  }

  private boolean isMongoExecutionTimeoutException(Exception ex) {
    return ex.getCause().getClass().equals(MongoExecutionTimeoutException.class);
  }

  public interface Executor<R> {
    R execute();
  }

  public static <R> R retry(Executor<R> executor) {
    for (int i = 1; i < RETRIES; ++i) {
      try {
        return executor.execute();
      } catch (MongoSocketOpenException | MongoSocketReadException | OptimisticLockingFailureException e) {
        log.error("Exception ignored on retry ", e);
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
}
