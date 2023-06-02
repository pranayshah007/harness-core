/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static dev.morphia.logging.MorphiaLoggerFactory.registerLogger;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.exception.UnexpectedException;
import io.harness.logging.MorphiaLoggerFactory;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.mongo.tracing.TracerModule;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.QueryFactory;
import io.harness.persistence.store.Store;
import io.harness.serializer.KryoModule;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import com.mongodb.client.MongoClients;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import dev.morphia.ObjectFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class MongoModule extends AbstractModule {
  private static volatile MongoModule instance;
  public static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
  public static final int DEFAULT_SERVER_SELECTION_TIMEOUT = 90000;
  public static final int DEFAULT_SOCKET_TIMEOUT = 360000;
  public static final int DEFAULT_MAX_CONNECTION_IDLE_TIME = 600000;
  public static final int DEFAULT_CONNECTIONS_PER_HOST = 300;

  static MongoModule getInstance() {
    if (instance == null) {
      instance = new MongoModule();
    }
    return instance;
  }

  @Provides
  @Singleton
  public HarnessConnectionPoolListener harnessConnectionPoolListener() {
    return new HarnessConnectionPoolListener();
  }

  @Provides
  @Named("defaultMongoClientOptions")
  @Singleton
  public static MongoClientOptions getDefaultMongoClientOptions(MongoConfig mongoConfig) {
    MongoClientOptions defaultMongoClientOptions;
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    if (mongoSSLConfig != null && mongoSSLConfig.isMongoSSLEnabled()) {
      defaultMongoClientOptions = getMongoSslContextClientOptions(mongoConfig);
    } else {
      defaultMongoClientOptions = MongoClientOptions.builder()
                                      .retryWrites(true)
                                      .connectTimeout(DEFAULT_CONNECTION_TIMEOUT)
                                      .serverSelectionTimeout(DEFAULT_SERVER_SELECTION_TIMEOUT)
                                      .socketTimeout(DEFAULT_SOCKET_TIMEOUT)
                                      .maxConnectionIdleTime(DEFAULT_MAX_CONNECTION_IDLE_TIME)
                                      .connectionsPerHost(DEFAULT_CONNECTIONS_PER_HOST)
                                      .build();
    }
    return defaultMongoClientOptions;
  }

  @Provides
  @Named("primaryMongoClient")
  @Singleton
  public MongoClient primaryMongoClient(
      MongoConfig mongoConfig, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    MongoClientOptions primaryMongoClientOptions;
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    if (mongoSSLConfig != null && mongoSSLConfig.isMongoSSLEnabled()) {
      primaryMongoClientOptions = getMongoSslContextClientOptions(mongoConfig);
    } else {
      primaryMongoClientOptions = MongoClientOptions.builder()
                                      .retryWrites(true)
                                      .connectTimeout(mongoConfig.getConnectTimeout())
                                      .serverSelectionTimeout(mongoConfig.getServerSelectionTimeout())
                                      .socketTimeout(mongoConfig.getSocketTimeout())
                                      .maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime())
                                      .connectionsPerHost(mongoConfig.getConnectionsPerHost())
                                      .readPreference(mongoConfig.getReadPreference())
                                      .build();
    }

    MongoClientURI uri = new MongoClientURI(mongoConfig.getUri(),
        MongoClientOptions.builder(primaryMongoClientOptions)
            .readPreference(mongoConfig.getReadPreference())
            .addConnectionPoolListener(harnessConnectionPoolListener)
            .applicationName("primary_mongo_client"));
    return new MongoClient(uri);
  }

  @Provides
  @Named("primaryMongoClient")
  @Singleton
  public com.mongodb.client.MongoClient primaryNewMongoClient(
      MongoConfig mongoConfig, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    MongoClientSettings primaryMongoClientSettings;
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    if (mongoSSLConfig != null && mongoSSLConfig.isMongoSSLEnabled()) {
      primaryMongoClientSettings = getMongoSslContextClientSettings(mongoConfig);
    } else {
      primaryMongoClientSettings =
          MongoClientSettings.builder()
              .applyConnectionString(new ConnectionString(mongoConfig.getUri()))
              .retryWrites(true)
              .applyToSocketSettings(
                  builder -> builder.connectTimeout(mongoConfig.getConnectTimeout(), TimeUnit.MILLISECONDS))
              .applyToClusterSettings(builder
                  -> builder.serverSelectionTimeout(mongoConfig.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
              .applyToSocketSettings(
                  builder -> builder.readTimeout(mongoConfig.getSocketTimeout(), TimeUnit.MILLISECONDS))
              .applyToConnectionPoolSettings(builder
                  -> builder.maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
              .applyToConnectionPoolSettings(builder -> builder.maxSize(mongoConfig.getConnectionsPerHost()))
              .readPreference(mongoConfig.getReadPreference())
              .build();
    }

    return MongoClients.create(
        MongoClientSettings.builder(primaryMongoClientSettings)
            .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(harnessConnectionPoolListener))
            .applicationName("primary_mongo_client")
            .build());
  }

  public static AdvancedDatastore createDatastore(
      Morphia morphia, String uri, String name, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    MongoConfig mongoConfig = MongoConfig.builder().build();

    MongoClientURI clientUri = new MongoClientURI(uri,
        MongoClientOptions.builder(getDefaultMongoClientOptions(mongoConfig))
            .addConnectionPoolListener(harnessConnectionPoolListener)
            .applicationName("mongo_client_" + name));
    MongoClient mongoClient = new MongoClient(clientUri);

    AdvancedDatastore datastore = (AdvancedDatastore) morphia.createDatastore(mongoClient, clientUri.getDatabase());
    datastore.setQueryFactory(new QueryFactory(mongoConfig.getTraceMode(), mongoConfig.getMaxOperationTimeInMillis(),
        mongoConfig.getMaxDocumentsToBeFetched()));

    return datastore;
  }

  public static com.mongodb.client.MongoClient createNewMongoCLient(
      String uri, String name, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    MongoClientSettings mongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(uri))
            .retryWrites(true)
            .applyToSocketSettings(builder -> builder.connectTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS))
            .applyToClusterSettings(
                builder -> builder.serverSelectionTimeout(DEFAULT_SERVER_SELECTION_TIMEOUT, TimeUnit.MILLISECONDS))
            .applyToSocketSettings(builder -> builder.readTimeout(DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(
                builder -> builder.maxConnectionIdleTime(DEFAULT_MAX_CONNECTION_IDLE_TIME, TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder -> builder.maxSize(DEFAULT_CONNECTIONS_PER_HOST))
            .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(harnessConnectionPoolListener))
            .applicationName("mongo_client_" + name)
            .build();

    return MongoClients.create(mongoClientSettings);
  }

  private MongoModule() {
    try {
      registerLogger(MorphiaLoggerFactory.class);
    } catch (Exception e) {
      // happens when MorphiaLoggerFactory.get has already been called.
      log.warn("Failed to register logger", e);
    }
  }

  @Override
  protected void configure() {
    install(ObjectFactoryModule.getInstance());
    install(MorphiaModule.getInstance());
    install(KryoModule.getInstance());
    install(TracerModule.getInstance());

    MapBinder.newMapBinder(binder(), String.class, Migrator.class);
  }

  private static MongoClientOptions getMongoSslContextClientOptions(MongoConfig mongoConfig) {
    MongoClientOptions primaryMongoClientOptions;
    validateSSLMongoConfig(mongoConfig);
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    String trustStorePath = mongoSSLConfig.getMongoTrustStorePath();
    String trustStorePassword = mongoSSLConfig.getMongoTrustStorePassword();
    primaryMongoClientOptions = MongoClientOptions.builder()
                                    .retryWrites(true)
                                    .connectTimeout(mongoConfig.getConnectTimeout())
                                    .serverSelectionTimeout(mongoConfig.getServerSelectionTimeout())
                                    .socketTimeout(mongoConfig.getSocketTimeout())
                                    .maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime())
                                    .connectionsPerHost(mongoConfig.getConnectionsPerHost())
                                    .readPreference(mongoConfig.getReadPreference())
                                    .sslEnabled(mongoSSLConfig.isMongoSSLEnabled())
                                    .sslInvalidHostNameAllowed(true)
                                    .sslContext(sslContext(trustStorePath, trustStorePassword))
                                    .build();
    return primaryMongoClientOptions;
  }

  private static MongoClientSettings getMongoSslContextClientSettings(MongoConfig mongoConfig) {
    validateSSLMongoConfig(mongoConfig);
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    String trustStorePath = mongoSSLConfig.getMongoTrustStorePath();
    String trustStorePassword = mongoSSLConfig.getMongoTrustStorePassword();

    return MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(mongoConfig.getUri()))
        .retryWrites(true)
        .applyToSocketSettings(
            builder -> builder.connectTimeout(mongoConfig.getConnectTimeout(), TimeUnit.MILLISECONDS))
        .applyToClusterSettings(
            builder -> builder.serverSelectionTimeout(mongoConfig.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
        .applyToSocketSettings(builder -> builder.readTimeout(mongoConfig.getSocketTimeout(), TimeUnit.MILLISECONDS))
        .applyToConnectionPoolSettings(
            builder -> builder.maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
        .applyToConnectionPoolSettings(builder -> builder.maxSize(mongoConfig.getConnectionsPerHost()))
        .readPreference(mongoConfig.getReadPreference())
        .applyToSslSettings(builder -> builder.enabled(mongoSSLConfig.isMongoSSLEnabled()))
        .applyToSslSettings(builder -> builder.invalidHostNameAllowed(true))
        .applyToSslSettings(builder -> builder.context(sslContext(trustStorePath, trustStorePassword)))
        .build();
  }

  private static void validateSSLMongoConfig(MongoConfig mongoConfig) {
    MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
    Preconditions.checkNotNull(mongoSSLConfig,
        "mongoSSLConfig must be set under mongo config if SSL context creation is requested or mongoSSLEnabled is set to true");
    Preconditions.checkArgument(
        mongoSSLConfig.isMongoSSLEnabled(), "mongoSSLEnabled must be set to true for MongoSSLConfiguration");
    Preconditions.checkArgument(StringUtils.isNotBlank(mongoSSLConfig.getMongoTrustStorePath()),
        "mongoTrustStorePath must be set if mongoSSLEnabled is set to true");
  }

  @Provides
  @Named("primaryDatastore")
  @Singleton
  public AdvancedDatastore primaryDatastore(@Named("primaryMongoClient") MongoClient mongoClient,
      MongoConfig mongoConfig, @Named("morphiaClasses") Set<Class> classes,
      @Named("morphiaInterfaceImplementersClasses") Map<String, Class> morphiaInterfaceImplementers, Morphia morphia,
      ObjectFactory objectFactory, IndexManager indexManager) {
    for (Class clazz : classes) {
      if (morphia.getMapper().getMCMap().get(clazz.getName()).getCollectionName().startsWith("!!!custom_")) {
        throw new UnexpectedException(format("The custom collection name for %s is not provided", clazz.getName()));
      }
    }

    AdvancedDatastore primaryDatastore = (AdvancedDatastore) morphia.createDatastore(
        mongoClient, new MongoClientURI(mongoConfig.getUri()).getDatabase());
    primaryDatastore.setQueryFactory(new QueryFactory(mongoConfig.getTraceMode(),
        mongoConfig.getMaxOperationTimeInMillis(), mongoConfig.getMaxDocumentsToBeFetched()));

    Store store = null;
    if (Objects.nonNull(mongoConfig.getAliasDBName())) {
      store = Store.builder().name(mongoConfig.getAliasDBName()).build();
    }

    indexManager.ensureIndexes(mongoConfig.getIndexManagerMode(), primaryDatastore, morphia, store);

    ClassRefactoringManager.updateMovedClasses(primaryDatastore, morphiaInterfaceImplementers);
    ((HObjectFactory) objectFactory).setDatastore(primaryDatastore);

    return primaryDatastore;
  }

  @Provides
  @Named("analyticsDatabase")
  @Singleton
  public AdvancedDatastore getAnalyticsDatabase(
      MongoConfig mongoConfig, Morphia morphia, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    TagSet tags = null;
    if (!mongoConfig.getAnalyticNodeConfig().getMongoTagKey().equals("none")) {
      tags = new TagSet(new Tag(mongoConfig.getAnalyticNodeConfig().getMongoTagKey(),
          mongoConfig.getAnalyticNodeConfig().getMongoTagValue()));
    }

    ReadPreference readPreference;
    if (Objects.isNull(tags)) {
      readPreference = ReadPreference.secondaryPreferred();
    } else {
      readPreference = ReadPreference.secondaryPreferred(tags);
    }

    final String mongoClientUrl = mongoConfig.getUri();
    MongoClientURI uri = new MongoClientURI(mongoClientUrl,
        MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig))
            .readPreference(readPreference)
            .addConnectionPoolListener(harnessConnectionPoolListener)
            .applicationName("analytics_mongo_client"));

    MongoClient mongoClient = new MongoClient(uri);
    AdvancedDatastore analyticalDataStore = (AdvancedDatastore) morphia.createDatastore(mongoClient, uri.getDatabase());
    analyticalDataStore.setQueryFactory(new QueryFactory(mongoConfig.getTraceMode(),
        mongoConfig.getMaxOperationTimeInMillis(), mongoConfig.getMaxDocumentsToBeFetched()));
    return analyticalDataStore;
  }

  @Provides
  @Named("locksMongoClient")
  @Singleton
  public MongoClient getLocksMongoClient(
      MongoConfig mongoConfig, HarnessConnectionPoolListener harnessConnectionPoolListener) {
    MongoClientURI uri;
    MongoClientOptions.Builder builder = MongoClientOptions.builder(getDefaultMongoClientOptions(mongoConfig))
                                             .addConnectionPoolListener(harnessConnectionPoolListener)
                                             .applicationName("locks_mongo_client");
    if (isNotEmpty(mongoConfig.getLocksUri())) {
      uri = new MongoClientURI(mongoConfig.getLocksUri(), builder);
    } else {
      uri = new MongoClientURI(mongoConfig.getUri(), builder);
    }
    return new MongoClient(uri);
  }

  @Provides
  @Named("locksDatabase")
  @Singleton
  public String getLocksDatabase(MongoConfig mongoConfig) {
    MongoClientURI uri;
    if (isNotEmpty(mongoConfig.getLocksUri())) {
      uri = new MongoClientURI(
          mongoConfig.getLocksUri(), MongoClientOptions.builder(getDefaultMongoClientOptions(mongoConfig)));
    } else {
      uri = new MongoClientURI(
          mongoConfig.getUri(), MongoClientOptions.builder(getDefaultMongoClientOptions(mongoConfig)));
    }
    return uri.getDatabase();
  }

  private static SSLContext sslContext(String keystoreFile, String password) {
    SSLContext sslContext = null;
    try {
      KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      InputStream in = new FileInputStream(keystoreFile);
      keystore.load(in, password.toCharArray());
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keystore);
      sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

    } catch (GeneralSecurityException | IOException exception) {
      throw new GeneralException("SSLContext exception: {}", exception);
    }
    return sslContext;
  }
}
