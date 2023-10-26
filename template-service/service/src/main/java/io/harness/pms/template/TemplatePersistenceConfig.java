/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.TemplateServiceConfiguration;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "template-mongodb")
@OwnedBy(HarnessTeam.PIPELINE)
public class TemplatePersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoBackendConfiguration mongoBackendConfiguration;
  private final HarnessConnectionPoolListener harnessConnectionPoolListener;
  private final MongoConfig mongoConfig;

  @Inject
  public TemplatePersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration =
        (MongoBackendConfiguration) injector.getInstance(Key.get(TemplateServiceConfiguration.class))
            .getTemplateClientConfiguration()
            .getTemplateClientBackendConfiguration();
    this.harnessConnectionPoolListener = injector.getInstance(HarnessConnectionPoolListener.class);
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    MongoClientSettings mongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoBackendConfiguration.getUri()))
            .retryWrites(true)
            .applyToSocketSettings(
                builder -> builder.connectTimeout(mongoBackendConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS))
            .applyToClusterSettings(builder
                -> builder.serverSelectionTimeout(
                    mongoBackendConfiguration.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
            .applyToSocketSettings(
                builder -> builder.readTimeout(mongoBackendConfiguration.getSocketTimeout(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder
                -> builder.maxConnectionIdleTime(
                    mongoBackendConfiguration.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(
                builder -> builder.maxSize(mongoBackendConfiguration.getConnectionsPerHost()))
            .readPreference(ReadPreference.primary())
            .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(harnessConnectionPoolListener))
            .build();

    return MongoClients.create(mongoClientSettings);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Collection<String> getMappingBasePackages() {
    return Collections.singleton("io.harness");
  }

  @Bean(name = "template-mongodb")
  public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
    return new HMongoTemplate(
        new SimpleMongoClientDatabaseFactory(mongoClient(), getDatabaseName()), converter, mongoConfig);
  }
}
