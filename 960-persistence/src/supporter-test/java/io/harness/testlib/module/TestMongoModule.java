/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testlib.module;

import static io.harness.govern.Switch.unhandled;
import static io.harness.rule.TestUserProvider.testUserProvider;

import io.harness.factory.ClosingFactory;
import io.harness.mongo.HObjectFactory;
import io.harness.mongo.ObjectFactoryModule;
import io.harness.mongo.QueryFactory;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.UserProvider;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.client.MongoClient;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.ObjectFactory;

@Slf4j
public class TestMongoModule extends AbstractModule implements MongoRuleMixin {
  private static volatile TestMongoModule instance;

  public static TestMongoModule getInstance() {
    if (instance == null) {
      instance = new TestMongoModule();
    }
    return instance;
  }

  private TestMongoModule() {}

  @Provides
  @Named("databaseName")
  @Singleton
  String databaseNameProvider() {
    return databaseName();
  }

  @Provides
  @Named("locksDatabase")
  @Singleton
  String databaseNameProvider(@Named("databaseName") String databaseName) {
    return databaseName;
  }

  @Provides
  @Named("realMongoClient")
  @Singleton
  public MongoClient realMongoClientProvider(@Named("databaseName") String databaseName, ClosingFactory closingFactory)
      throws Exception {
    return realMongoClient(closingFactory, databaseName);
  }

  @Provides
  @Named("fakeMongoClient")
  @Singleton
  public MongoClient fakeMongoClientProvider(ClosingFactory closingFactory) throws Exception {
    return fakeMongoClient(closingFactory);
  }

  @Provides
  @Named("realMongoLegacyClient")
  @Singleton
  public com.mongodb.MongoClient realMongoLegacyClientProvider(@Named("databaseName") String databaseName, ClosingFactory closingFactory)
          throws Exception {
    return realMongoLegacyClient(closingFactory, databaseName);
  }

  @Provides
  @Named("fakeMongoLegacyClient")
  @Singleton
  public com.mongodb.MongoClient fakeMongoLegacyClientProvider(ClosingFactory closingFactory) throws Exception {
    return fakeMongoLegacyClient(closingFactory);
  }

  @Provides
  @Named("locksMongoClient")
  @Singleton
  public MongoClient locksMongoClient(@Named("realMongoClient") MongoClient mongoClient) throws Exception {
    return mongoClient;
  }

  @Provides
  @Named("primaryDatastore")
  @Singleton
  AdvancedDatastore datastore(@Named("databaseName") String databaseName, MongoType type,
      @Named("realMongoLegacyClient") Provider<com.mongodb.MongoClient> realMongoClient,
      @Named("fakeMongoLegacyClient") Provider<com.mongodb.MongoClient> fakeMongoClient, Morphia morphia, ObjectFactory objectFactory) {
    com.mongodb.MongoClient mongoClient = null;
    switch (type) {
      case REAL:
        mongoClient = realMongoClient.get();
        break;
      case FAKE:
        mongoClient = fakeMongoClient.get();
        break;
      default:
        unhandled(type);
    }

    AdvancedDatastore datastore = (AdvancedDatastore) morphia.createDatastore(mongoClient, databaseName);
    datastore.setQueryFactory(new QueryFactory());
    ((HObjectFactory) objectFactory).setDatastore(datastore);
    return datastore;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder().build();
  }

  @Override
  protected void configure() {
    install(ObjectFactoryModule.getInstance());
    install(MorphiaModule.getInstance());

    bind(UserProvider.class).toInstance(testUserProvider);

    MapBinder.newMapBinder(binder(), String.class, Migrator.class);
  }
}
