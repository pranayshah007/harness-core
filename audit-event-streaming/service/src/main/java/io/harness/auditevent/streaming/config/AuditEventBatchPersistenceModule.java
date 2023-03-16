/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static io.harness.mongo.helper.MongoConstants.ANALYTICS;
import static io.harness.mongo.helper.MongoConstants.SECONDARY;

import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.mongo.MongoConfig;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.exceptions.SpringMongoExceptionHandler;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import java.util.Arrays;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.TransactionException;

public class AuditEventBatchPersistenceModule extends AbstractModule {
  @Override
  protected void configure() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});

    exceptionHandlerMapBinder.addBinding(TransactionException.class).to(SpringMongoExceptionHandler.class);
    exceptionHandlerMapBinder.addBinding(UncategorizedMongoDbException.class).to(SpringMongoExceptionHandler.class);
  }

  @Provides
  @Named(SECONDARY)
  @Singleton
  protected MongoTemplate getSecondaryPreferredMongoTemplate(
      MongoTemplate mongoTemplate, MongoConfig primaryMongoConfig) {
    HMongoTemplate template =
        new HMongoTemplate(mongoTemplate.getMongoDbFactory(), mongoTemplate.getConverter(), primaryMongoConfig);
    template.setReadPreference(ReadPreference.secondaryPreferred());
    return template;
  }

  @Provides
  @Named(ANALYTICS)
  @Singleton
  protected MongoTemplate getAnalyticsMongoTemplate(MongoTemplate mongoTemplate, MongoConfig primaryMongoConfig) {
    HMongoTemplate template =
        new HMongoTemplate(mongoTemplate.getMongoDbFactory(), mongoTemplate.getConverter(), primaryMongoConfig);
    template.setReadPreference(ReadPreference.secondaryPreferred(Arrays.asList(
        new TagSet(new Tag("nodeType", "ANALYTICS")), new TagSet(new Tag("nodeType", "ELECTABLE")), new TagSet())));
    return template;
  }
}
