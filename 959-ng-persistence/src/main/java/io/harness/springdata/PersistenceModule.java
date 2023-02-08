/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.helper.MongoConstants.ANALYTICS;
import static io.harness.mongo.helper.MongoConstants.SECONDARY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.mongo.MongoConfig;
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
import org.springframework.guice.module.BeanFactoryProvider;
import org.springframework.guice.module.SpringModule;
import org.springframework.transaction.TransactionException;

@OwnedBy(PL)
public abstract class PersistenceModule extends AbstractModule {
  // Batch size to define to update multiple records should be limited within this value
  public static final int MAX_BATCH_SIZE = 1000;
  @Override
  protected void configure() {
    install(new SpringModule(BeanFactoryProvider.from(getConfigClasses())));

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

  protected abstract Class<?>[] getConfigClasses();
}
