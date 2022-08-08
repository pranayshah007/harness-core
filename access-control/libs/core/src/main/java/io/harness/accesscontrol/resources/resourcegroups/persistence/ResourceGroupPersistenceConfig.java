/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.persistence;

import com.mongodb.client.MongoClient;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongodbClientCreates;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.MongoClientURI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@OwnedBy(HarnessTeam.PL)
@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.accesscontrol.resources.resourcegroups.persistence"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class))
@EnableMongoAuditing
public class ResourceGroupPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;

  @Inject
  public ResourceGroupPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    return MongodbClientCreates.createMongoClient(mongoBackendConfiguration);
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
  public MongoTemplate mongoTemplate() throws Exception {
    return new HMongoTemplate(mongoDbFactory(), mappingMongoConverter(
            mongoDbFactory(),
            customConversions(),
            mongoMappingContext(customConversions())));
  }

  @Override
  public boolean autoIndexCreation() {
    return false;
  }
}
