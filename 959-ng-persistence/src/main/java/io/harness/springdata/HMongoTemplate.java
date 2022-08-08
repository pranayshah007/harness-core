/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.health.HealthMonitor;
import io.harness.mongo.tracing.TraceMode;
import io.harness.ng.persistence.tracer.NgTracer;
import io.harness.observer.Subject;

import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadException;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.lang.Nullable;

@SuppressWarnings("NullableProblems")
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class HMongoTemplate extends MongoTemplate implements HealthMonitor {
  private static final int RETRIES = 3;

  public static final FindAndModifyOptions upsertReturnNewOptions =
      new FindAndModifyOptions().upsert(true).returnNew(true);
  public static final FindAndModifyOptions upsertReturnOldOptions =
      new FindAndModifyOptions().upsert(true).returnNew(false);

  private final TraceMode traceMode;

  @Getter private final Subject<NgTracer> tracerSubject = new Subject<>();

  public HMongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter mongoConverter) {
    this(mongoDbFactory, mongoConverter, TraceMode.DISABLED);
  }

  public HMongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter mongoConverter, TraceMode traceMode) {
    super(mongoDbFactory, mongoConverter);
    this.traceMode = traceMode;
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
    traceQuery(query, entityClass);
    return super.find(query, entityClass, collectionName);
  }

  @Override
  public <T> T findOne(Query query, Class<T> entityClass, String collectionName) {
    traceQuery(query, entityClass);
    return super.findOne(query, entityClass, collectionName);
  }

  private <T> void traceQuery(Query query, Class<T> entityClass) {
    if (traceMode == TraceMode.ENABLED) {
      tracerSubject.fireInform(NgTracer::traceSpringQuery, query, entityClass, this);
    }
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
