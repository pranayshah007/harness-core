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

//import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

import javax.validation.constraints.NotNull;

public class Handler implements MongoPersistenceIterator.Handler<NamespaceEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;

  @Inject private MorphiaPersistenceProvider<NamespaceEntity> persistenceProvider;

  @Override
  public void handle(NamespaceEntity namespaceEntity) {
    System.out.println(
        "Hello******" + namespaceEntity.getNextIteration() + "****" + namespaceEntity.getAccountIdentifier()+"****"+namespaceEntity.getUuid());
  }

//  public void registerIterators() {
//    persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(
//        PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
//            .name("IDPTriggerProcessor")
//            .poolSize(5)
//            .batchSize(20)
//            .interval(ofSeconds(5))
//            .build(),
//        Handler.class,
//        MongoPersistenceIterator.<NamespaceEntity, SpringFilterExpander>builder()
//            .clazz(NamespaceEntity.class)
//            .fieldName(NamespaceEntity.NamespaceKeys.nextIteration)
//            .targetInterval(ofSeconds(40))
//            .acceptableExecutionTime(ofMinutes(1))
//            .acceptableNoAlertDelay(ofSeconds(80))
//            .maximumDelayForCheck(ofSeconds(30))
//            .handler(this)
//            //                        .filterExpander(query
//            //                                -> query.addCriteria(new Criteria()
//            //                                .and(NamespaceEntity.NamespaceKeys.nextIteration)
//            //                                .exists(true)))
//            .schedulingType(REGULAR)
//            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate)).redistribute(true));
//  }

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
            PersistenceIteratorFactory.PumpExecutorOptions.builder()
                    .name("IDPTriggerProcessor")
                    .poolSize(5)
                    .interval(ofSeconds(5))
                    .build(),
            Handler.class,
            MongoPersistenceIterator.<NamespaceEntity, SpringFilterExpander>builder()
                    .clazz(NamespaceEntity.class)
                    .fieldName(NamespaceEntity.NamespaceKeys.nextIteration)
                    .targetInterval(ofSeconds(31))
                    .acceptableExecutionTime(ofSeconds(31))
                    .acceptableNoAlertDelay(ofSeconds(62))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
                    .redistribute(true));
  }

}
