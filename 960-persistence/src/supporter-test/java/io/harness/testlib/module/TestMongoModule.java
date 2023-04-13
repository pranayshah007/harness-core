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
import io.harness.mongo.MongoConfig;
import io.harness.mongo.ObjectFactoryModule;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.QueryFactory;
import io.harness.persistence.UserProvider;
import io.harness.serializer.KryoModule;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.MongoClient;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import dev.morphia.ObjectFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

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
  @Named("realLegacyMongoClient")
  @Singleton
  public MongoClient realLegacyMongoClientProvider(
      @Named("databaseName") String databaseName, ClosingFactory closingFactory) throws Exception {
    return realLegacyMongoClient(closingFactory, databaseName);
  }

  @Provides
  @Named("fakeLegacyMongoClient")
  @Singleton
  public MongoClient fakeLegacyMongoClientProvider(ClosingFactory closingFactory) throws Exception {
    return fakeLegacyMongoClient(closingFactory);
  }

  @Provides
  @Named("realMongoClient")
  @Singleton
  public com.mongodb.client.MongoClient realMongoClientProvider(
      @Named("databaseName") String databaseName, ClosingFactory closingFactory) throws Exception {
    return realMongoClient(closingFactory, databaseName);
  }

  @Provides
  @Named("fakeMongoClient")
  @Singleton
  public com.mongodb.client.MongoClient fakeMongoClientProvider(ClosingFactory closingFactory) throws Exception {
    return fakeMongoClient(closingFactory);
  }

  @Provides
  @Named("primaryMongoClient")
  @Singleton
  public com.mongodb.client.MongoClient mongoClient(MongoType type,
      @Named("realMongoClient") Provider<com.mongodb.client.MongoClient> realMongoClient,
      @Named("fakeMongoClient") Provider<com.mongodb.client.MongoClient> fakeMongoClient) {
    com.mongodb.client.MongoClient mongoClient = null;
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

    return mongoClient;
  }

  @Provides
  @Named("locksMongoClient")
  @Singleton
  public MongoClient locksMongoClient(@Named("realLegacyMongoClient") MongoClient mongoClient) throws Exception {
    return mongoClient;
  }

  @Provides
  @Named("primaryDatastore")
  @Singleton
  AdvancedDatastore datastore(@Named("databaseName") String databaseName, MongoType type,
      @Named("realLegacyMongoClient") Provider<MongoClient> realLegacyMongoClient,
      @Named("fakeLegacyMongoClient") Provider<MongoClient> fakeLegacyMongoClient, Morphia morphia,
      ObjectFactory objectFactory) {
    MongoClient mongoClient = null;
    switch (type) {
      case REAL:
        mongoClient = realLegacyMongoClient.get();
        break;
      case FAKE:
        mongoClient = fakeLegacyMongoClient.get();
        break;
      default:
        unhandled(type);
    }

    AdvancedDatastore datastore = (AdvancedDatastore) morphia.createDatastore(mongoClient, databaseName);
    MongoConfig mongoConfig = MongoConfig.builder().build();
    datastore.setQueryFactory(new QueryFactory(mongoConfig.getTraceMode(), mongoConfig.getMaxOperationTimeInMillis(),
        mongoConfig.getMaxDocumentsToBeFetched()));
    ((HObjectFactory) objectFactory).setDatastore(datastore);
    return datastore;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder().build();
  }

  @Provides
  @Singleton
  @Named("dbAliases")
  public List<String> getDbAliases() {
    return Collections.EMPTY_LIST;
  }

  @Override
  protected void configure() {
    install(ObjectFactoryModule.getInstance());
    install(MorphiaModule.getInstance());
    install(KryoModule.getInstance());
    bind(UserProvider.class).toInstance(testUserProvider);
    MapBinder.newMapBinder(binder(), String.class, Migrator.class);
  }
}
