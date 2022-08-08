/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.platform.PlatformConfiguration;
import io.harness.reflection.HarnessReflections;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@OwnedBy(PL)
@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.audit.repositories", "io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class))
@EnableMongoAuditing
public class AuditPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;

  @Inject
  public AuditPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration =
        injector.getInstance(Key.get(PlatformConfiguration.class)).getAuditServiceConfig().getMongoConfig();
  }

  @Override
  public MongoClient mongoClient() {
    MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoBackendConfiguration.getUri()))
            .retryWrites(true)
            .applyToConnectionPoolSettings(builder -> {
              builder.maxConnectionIdleTime(mongoBackendConfiguration.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS);
              builder.maxSize(mongoBackendConfiguration.getConnectionsPerHost());
            }).applyToSocketSettings(builder -> {
              builder.connectTimeout(mongoBackendConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS);
            }).applyToClusterSettings(builder -> {
              builder.serverSelectionTimeout(mongoBackendConfiguration.getServerSelectionTimeout(), TimeUnit.MILLISECONDS);
            }).readPreference(ReadPreference.primary())
            .build();
    return MongoClients.create(mongoClientSettings);
  }

  @Override
  protected String getDatabaseName() {
    return new ConnectionString(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDbFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    return HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
  }

  @Bean
  public MongoTemplate mongoTemplate() throws Exception {
    // TO-DbCheck: why this.mongoDbFactory() ?
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(this.mongoDbFactory());
    MongoDatabaseFactory mongoDbFactory = mongoDbFactory();
    MongoMappingContext mappingContext = mongoMappingContext(customConversions());
    mappingContext.setAutoIndexCreation(false);
    MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
    converter.setCodecRegistryProvider(mongoDbFactory);
    converter.afterPropertiesSet();
    // TO-DbCheck: why created new converter while there is already a converter constructed?
    // TO-DbCheck: why not use the inherited mappingMongoConverter(..), why new?
    // (will remove this comment before merge)
    return new HMongoTemplate(mongoDbFactory, converter);
  }
}
