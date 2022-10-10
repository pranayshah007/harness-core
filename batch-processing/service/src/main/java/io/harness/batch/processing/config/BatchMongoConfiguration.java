/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.app.EventServiceApplication.EVENTS_STORE;

import io.harness.mongo.AbstractMongoModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UserProvider;

import software.wings.security.ThreadLocalUserProvider;

import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

@Configuration
@Slf4j
public class BatchMongoConfiguration {
  private static void registerEventsStore(HPersistence hPersistence, BatchMainConfig config) {
    final String eventsMongoUri = config.getEventsMongo().getUri();
    if (isNotEmpty(eventsMongoUri) && !eventsMongoUri.equals(config.getHarnessMongo().getUri())) {
      hPersistence.register(EVENTS_STORE, eventsMongoUri);
    }
  }

  @Bean
  @Profile("!test")
  public MongoDbFactory mongoDbFactory(HPersistence hPersistence, BatchMainConfig config) {
    registerEventsStore(hPersistence, config);
    AdvancedDatastore eventsDatastore = hPersistence.getDatastore(EVENTS_STORE);
    return new SimpleMongoDbFactory(eventsDatastore.getMongo(), eventsDatastore.getDB().getName());
  }

  @Bean
  public MongoTemplate mongoTemplate(MongoDbFactory mongoDbFactory) {
    return new MongoTemplate(mongoDbFactory);
  }

  @Bean
  @Profile("!test")
  public AbstractMongoModule mongoModule() {
    return new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new ThreadLocalUserProvider();
      }
    };
  }
}
