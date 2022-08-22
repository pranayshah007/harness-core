/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import static org.springframework.test.util.ReflectionTestUtils.getField;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.mongo.QueryFactory;
import io.harness.persistence.HPersistence;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Set;
import lombok.val;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

@Configuration
@Profile("test")
public class TestConfiguration implements MongoRuleMixin {
  @Bean
  MongoRuleMixin.MongoType mongoType() {
    return MongoType.FAKE;
  }

  @Bean
  TestMongoModule testMongoModule() {
    return TestMongoModule.getInstance();
  }

  @Bean
  ProviderModule morphiaConverters() {
    return new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }
    };
  }

  @Bean
  public MongoDbFactory mongoDbFactory(
      ClosingFactory closingFactory, HPersistence hPersistence, BatchMainConfig config, Morphia morphia) {
    AdvancedDatastore eventsDatastore =
        (AdvancedDatastore) morphia.createDatastore(fakeMongoClient(closingFactory), "events");
    eventsDatastore.setQueryFactory(new QueryFactory());

    @SuppressWarnings("unchecked")
    val datastoreMap = (Map<String, AdvancedDatastore>) getField(hPersistence, "datastoreMap");
    datastoreMap.put("events", eventsDatastore);

    return new SimpleMongoDbFactory(eventsDatastore.getMongo(), eventsDatastore.getDB().getName());
  }

  @Bean
  ClosingFactory closingFactory() {
    return new ClosingFactory();
  }
}
