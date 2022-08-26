/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.queue;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;

import static java.lang.String.format;

import io.harness.exception.UnexpectedException;
import io.harness.persistence.HPersistence;
import io.harness.queue.Queuable;
import io.harness.queue.Queuable.QueuableKeys;
import io.harness.queue.QueueConsumer;
import io.harness.queue.TopicUtils;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class NGMongoQueueConsumer<T extends Queuable> implements QueueConsumer<T> {
  private final Class<T> klass;
  @Setter private Duration heartbeat;
  List<String> topics;
  private Semaphore semaphore = new Semaphore(1);
  private MongoTemplate persistence;

  public NGMongoQueueConsumer(
      Class<T> klass, Duration heartbeat, List<List<String>> topicExpression, MongoTemplate mongoTemplate) {
    Objects.requireNonNull(klass);
    this.klass = klass;
    this.heartbeat = heartbeat;
    this.topics = TopicUtils.resolveExpressionIntoListOfTopics(topicExpression);
    this.persistence = mongoTemplate;
  }

  @Override
  public T get(Duration wait, Duration poll) {
    long endTime = System.currentTimeMillis() + wait.toMillis();

    boolean acquired = false;
    try {
      acquired = semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
      if (acquired) {
        return getUnderLock(endTime, poll);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (acquired) {
        semaphore.release();
      }
    }
    return null;
  }

  private T getUnderLock(long endTime, Duration poll) {
    while (true) {
      final Date now = new Date();

      Query query = createQuery()
                        .addCriteria(Criteria.where(QueuableKeys.earliestGet).lte(now))
                        .with(Sort.by(Direction.ASC, QueuableKeys.earliestGet));

      Update update = new Update().set(QueuableKeys.earliestGet, new Date(now.getTime() + heartbeat().toMillis()));

      T message = HPersistence.retry(() -> persistence.findAndModify(query, update, klass));
      if (message != null) {
        return message;
      }

      if (System.currentTimeMillis() >= endTime) {
        return null;
      }

      try {
        Thread.sleep(poll.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      } catch (final IllegalArgumentException ex) {
        poll = Duration.ofMillis(0);
      }
    }
  }

  @Override
  public void updateHeartbeat(T message) {
    Date earliestGet = new Date(System.currentTimeMillis() + heartbeat().toMillis());

    Query query = new Query().addCriteria(Criteria.where(QueuableKeys.id).is(message.getId()));

    Update update = new Update().set(QueuableKeys.earliestGet, earliestGet);
    if (persistence.findAndModify(query, update, klass) != null) {
      message.setEarliestGet(earliestGet);
      return;
    }
    log.error("Update heartbeat failed for {}", message.getId());
  }

  @Override
  // This API is used only for testing, we do not need index for the running field. If you start using the
  // API in production, please consider adding such.
  public long count(final Filter filter) {
    switch (filter) {
      case ALL:
        return persistence.count(new Query(), klass);
      case RUNNING:
        return persistence.count(
            createQuery().addCriteria(Criteria.where(QueuableKeys.earliestGet).gte(new Date())), klass);
      case NOT_RUNNING:
        return persistence.count(
            createQuery().addCriteria(Criteria.where(QueuableKeys.earliestGet).lte(new Date())), klass);
      default:
        unhandled(filter);
    }
    throw new UnexpectedException(format("Unknown filter type %s", filter));
  }

  @Override
  public void ack(final T message) {
    Objects.requireNonNull(message);
    persistence.remove(new Query().addCriteria(Criteria.where(QueuableKeys.id).is(message.getId())), klass);
  }

  @Override
  public void requeue(final String id, int retries) {
    requeue(id, retries, new Date());
  }

  @Override
  public void requeue(final String id, final int retries, final Date earliestGet) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(earliestGet);

    persistence.updateFirst(new Query().addCriteria(Criteria.where(QueuableKeys.id).is(id)),
        new Update().set(QueuableKeys.retries, retries).set(QueuableKeys.earliestGet, earliestGet), klass);
  }

  @Override
  public Duration heartbeat() {
    return heartbeat;
  }

  @Override
  public String getName() {
    return klass.getSimpleName();
  }

  private Query createQuery() {
    Query query = new Query();
    if (isNotEmpty(topics)) {
      query.addCriteria(Criteria.where(QueuableKeys.topic).in(topics));
    } else {
      query.addCriteria(Criteria.where(QueuableKeys.topic).exists(false));
    }
    return query;
  }
}
