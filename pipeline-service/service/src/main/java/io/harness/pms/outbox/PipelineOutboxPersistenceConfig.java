/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;

import com.mongodb.ConnectionString;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongodbClientCreates;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
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
    return MongodbClientCreates.createMongoClient(mongoConfig);
  }

  @Override
  protected String getDatabaseName() {
    return new ConnectionString(mongoConfig.getUri()).getDatabase();
  }

  @Bean(name = "pipeline-outbox-secondary")
  public MongoTemplate mongoTemplate() throws Exception {
    final MongoDatabaseFactory mongoDatabaseFactory = mongoDbFactory();
    final MongoCustomConversions mongoCustomConversions = customConversions();
    MappingMongoConverter mappingMongoConverter = mappingMongoConverter(
            mongoDatabaseFactory, mongoCustomConversions, mongoMappingContext(mongoCustomConversions));
    mappingMongoConverter.setMapKeyDotReplacement(DOT_REPLACEMENT);
    MongoTemplate mongoTemplate =
        new HMongoTemplate(mongoDatabaseFactory, mappingMongoConverter, mongoConfig.getTraceMode());
    mongoTemplate.setReadPreference(ReadPreference.secondary());
    return mongoTemplate;
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected boolean autoIndexCreation() {
    return false;
  }
}
