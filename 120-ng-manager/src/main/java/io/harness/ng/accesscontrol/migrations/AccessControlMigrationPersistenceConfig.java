/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.ng.accesscontrol.migrations.repositories",
                             "io.harness.ng.accesscontrol.mockserver.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class))
@OwnedBy(HarnessTeam.PL)
public class AccessControlMigrationPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;
  protected final MongoClient mongoClient;

  @Inject
  public AccessControlMigrationPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration = injector.getInstance(MongoConfig.class);
    this.mongoClient = injector.getInstance(Key.get(MongoClient.class, Names.named("primaryMongoClient")));
  }

  @Override
  public MongoClient mongoClient() {
    return mongoClient;
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Bean
  public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
    return new HMongoTemplate(databaseFactory, converter, mongoBackendConfiguration);
  }
}
