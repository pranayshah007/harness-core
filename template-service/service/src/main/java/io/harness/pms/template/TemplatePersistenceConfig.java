/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.TemplateServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.template.repositories"},
    includeFilters = @ComponentScan.Filter(GitSyncableHarnessRepo.class), mongoTemplateRef = "template-mongodb")
@EnableMongoAuditing
@OwnedBy(HarnessTeam.PL)
public class TemplatePersistenceConfig extends AbstractMongoClientConfiguration {
  private final HarnessConnectionPoolListener harnessConnectionPoolListener;
  private final MongoConfig mongoConfig;

  @Inject
  public TemplatePersistenceConfig(Injector injector) {
    this.harnessConnectionPoolListener = injector.getInstance(HarnessConnectionPoolListener.class);
    this.mongoConfig =
        injector.getInstance(Key.get(TemplateServiceConfiguration.class)).getTemplateClientConfiguration();
  }

  @Bean("template-mongo-client")
  @Override
  @Primary
  public MongoClient mongoClient() {
    MongoClientSettings mongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoConfig.getUri()))
            .retryWrites(true)
            .applyToSocketSettings(
                builder -> builder.connectTimeout(mongoConfig.getConnectTimeout(), TimeUnit.MILLISECONDS))
            .applyToClusterSettings(builder
                -> builder.serverSelectionTimeout(mongoConfig.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
            .applyToSocketSettings(
                builder -> builder.readTimeout(mongoConfig.getSocketTimeout(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(
                builder -> builder.maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder -> builder.maxSize(mongoConfig.getConnectionsPerHost()))
            .readPreference(ReadPreference.primary())
            .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(harnessConnectionPoolListener))
            .build();

    return MongoClients.create(mongoClientSettings);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoConfig.getUri()).getDatabase();
  }

  MongoTransactionManager transactionManager(@Qualifier("template-mongo-factory") MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Collection<String> getMappingBasePackages() {
    return Collections.singleton("io.harness");
  }

  @Override
  @Bean("template-mongo-factory")
  @Primary
  public MongoDatabaseFactory mongoDbFactory() {
    return new SimpleMongoClientDatabaseFactory(mongoClient(), getDatabaseName());
  }

  @Bean(name = "template-mongodb")
  @Primary
  public MongoTemplate mongoTemplate(
      @Qualifier("template-mongo-factory") MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
    return new HMongoTemplate(databaseFactory, converter, mongoConfig);
  }
}
