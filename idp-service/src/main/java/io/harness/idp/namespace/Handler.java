/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.*;
import static java.time.Duration.ofSeconds;

import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

public class Handler implements MongoPersistenceIterator.Handler<NamespaceEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;

  @Inject private MorphiaPersistenceProvider<NamespaceEntity> persistenceProvider;

  @Override
  public void handle(NamespaceEntity namespaceEntity) {
    System.out.println(
        "Hello******" + namespaceEntity.getNextIteration() + "****" + namespaceEntity.getAccountIdentifier());
  }

  public void registerIterators() {
    persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
            .name("IDPTriggerProcessor")
            .poolSize(1)
            .batchSize(1)
            .interval(ofSeconds(10))
            .build(),
        Handler.class,
        MongoPersistenceIterator.<NamespaceEntity, SpringFilterExpander>builder()
            .clazz(NamespaceEntity.class)
            .fieldName(NamespaceEntity.NamespaceKeys.nextIteration)
            .targetInterval(ofSeconds(10))
            .acceptableExecutionTime(ofMinutes(1))
            .acceptableNoAlertDelay(ofSeconds(30))
            .maximumDelayForCheck(ofSeconds(30))
            .redisModeBatchSize(5)
            .handler(this)
            //                        .filterExpander(query
            //                                -> query.addCriteria(new Criteria()
            //                                .and(NamespaceEntity.NamespaceKeys.nextIteration)
            //                                .exists(true)))
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate)));
    //                        .redistribute(true));
  }
}
