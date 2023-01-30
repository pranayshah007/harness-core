/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@Configuration
@GuiceModule
@EnableMongoRepositories(basePackageClasses = {OutboxEventRepository.class},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "pipeline-outbox-secondary")
@EnableMongoAuditing
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineOutboxPersistenceConfig extends AbstractMongoClientConfiguration {
  protected final MongoConfig mongoConfig;

  @Inject
  public PipelineOutboxPersistenceConfig(Injector injector) {
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
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
            .build();

    return MongoClients.create(mongoClientSettings);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoConfig.getUri()).getDatabase();
  }

  @Bean(name = "pipeline-outbox-secondary")
  public MongoTemplate mongoTemplate() throws Exception {
    MappingMongoConverter mappingMongoConverter = mappingMongoConverter();
    mappingMongoConverter.setMapKeyDotReplacement(DOT_REPLACEMENT);
    MongoTemplate mongoTemplate = new HMongoTemplate(mongoDbFactory(), mappingMongoConverter, mongoConfig);
    mongoTemplate.setReadPreference(ReadPreference.secondary());
    return mongoTemplate;
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDbFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected boolean autoIndexCreation() {
    return false;
  }
}
